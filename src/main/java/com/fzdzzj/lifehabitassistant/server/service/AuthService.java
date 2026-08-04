package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.common.ApiException;
import com.fzdzzj.lifehabitassistant.config.AuthRateLimiter;
import com.fzdzzj.lifehabitassistant.pojo.AuthDtos;
import com.fzdzzj.lifehabitassistant.pojo.PasswordResetToken;
import com.fzdzzj.lifehabitassistant.pojo.RefreshToken;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.pojo.UserSession;
import com.fzdzzj.lifehabitassistant.server.dao.PasswordResetTokenRepository;
import com.fzdzzj.lifehabitassistant.server.dao.RefreshTokenRepository;
import com.fzdzzj.lifehabitassistant.server.dao.SessionRepository;
import com.fzdzzj.lifehabitassistant.server.dao.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Password authentication plus stateful sessions. Access tokens stay short-lived
 * and stateless; refresh tokens are stored hashed and rotated on every use, so a
 * reused token can be detected and the whole session revoked.
 */
@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuthRateLimiter rateLimiter;
    private final SessionRepository sessions;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final TokenService tokenService;
    private final PasswordResetMailService mailService;
    private final SessionRevocationService revocation;
    private final CurrentUser currentUser;
    private final long refreshTokenTtlMinutes;
    private final long passwordResetTtlMinutes;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt,
                       AuthRateLimiter rateLimiter, SessionRepository sessions,
                       RefreshTokenRepository refreshTokens, PasswordResetTokenRepository resetTokens,
                       TokenService tokenService, PasswordResetMailService mailService,
                       SessionRevocationService revocation, CurrentUser currentUser,
                       @Value("${app.security.refresh-token-ttl-minutes:43200}") long refreshTokenTtlMinutes,
                       @Value("${app.security.password-reset-ttl-minutes:30}") long passwordResetTtlMinutes) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.rateLimiter = rateLimiter;
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.tokenService = tokenService;
        this.mailService = mailService;
        this.revocation = revocation;
        this.currentUser = currentUser;
        this.refreshTokenTtlMinutes = refreshTokenTtlMinutes;
        this.passwordResetTtlMinutes = passwordResetTtlMinutes;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.Credentials input, String clientIp, AuthDtos.Device device) {
        if (!rateLimiter.tryRegister(clientIp)) {
            throw ApiException.tooManyRequests("注册过于频繁，请稍后再试");
        }
        if (users.existsByUsername(input.username())) throw ApiException.conflict("用户名已存在");
        String email = normalizeEmail(input.email());
        if (email != null && users.existsByEmail(email)) throw ApiException.conflict("邮箱已被使用");
        User user = users.save(new User(input.username(), encoder.encode(input.password()), email));
        return openSession(user, device);
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.Credentials input, String clientIp, AuthDtos.Device device) {
        if (!rateLimiter.tryLogin(clientIp, input.username())) {
            throw ApiException.tooManyRequests("登录尝试过于频繁，请稍后再试");
        }
        User user = users.findByUsername(input.username()).orElse(null);
        if (user == null || !encoder.matches(input.password(), user.getPasswordHash())) {
            rateLimiter.recordLoginFailure(clientIp, input.username());
            throw ApiException.unauthorized("用户名或密码错误");
        }
        if (!user.isEnabled()) {
            rateLimiter.recordLoginFailure(clientIp, input.username());
            throw ApiException.forbidden("账号已被禁用，请联系管理员");
        }
        rateLimiter.recordLoginSuccess(clientIp, input.username());
        return openSession(user, device);
    }

    /**
     * One-time refresh rotation. The submitted token is atomically revoked; a
     * second submission of the same token means it leaked and revokes the whole
     * session.
     */
    @Transactional
    public AuthDtos.AuthResponse refresh(AuthDtos.RefreshRequest input) {
        LocalDateTime now = LocalDateTime.now();
        RefreshToken token = requireRefreshToken(input.refreshToken());
        if (token.isRevoked()) {
            revocation.revokeSession(token.getSession().getId(), now);
            throw ApiException.unauthorized("登录状态已失效，请重新登录");
        }
        UserSession session = token.getSession();
        if (!session.isActive() || !session.getUser().isEnabled()) {
            throw ApiException.unauthorized("登录状态已失效，请重新登录");
        }
        if (token.isExpired(now)) {
            throw ApiException.unauthorized("登录状态已失效，请重新登录");
        }
        if (refreshTokens.markRevokedIfActive(token.getId(), now) != 1) {
            revocation.revokeSession(session.getId(), now);
            throw ApiException.unauthorized("检测到登录状态异常，请重新登录");
        }
        session.touch(now);
        sessions.save(session);
        String raw = tokenService.opaque();
        refreshTokens.save(new RefreshToken(session, tokenService.sha256(raw),
                now.plus(refreshTokenTtlMinutes, ChronoUnit.MINUTES), now));
        String username = session.getUser().getUsername();
        return new AuthDtos.AuthResponse(jwt.create(username), "Bearer", username, raw, session.getId());
    }

    /** Revokes the session owning the submitted token; repeated logout is harmless. */
    @Transactional
    public void logout(AuthDtos.RefreshRequest input) {
        RefreshToken token = requireRefreshToken(input.refreshToken());
        revocation.revokeSession(token.getSession().getId(), LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<AuthDtos.SessionResponse> sessions() {
        User user = currentUser.require();
        return sessions.findByUserIdAndRevokedAtIsNullOrderByLastActiveAtDesc(user.getId())
                .stream().map(this::toSessionResponse).toList();
    }

    @Transactional
    public AuthDtos.SessionResponse revokeSession(Long id) {
        Long userId = currentUser.require().getId();
        UserSession session = sessions.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("会话不存在"));
        if (session.isActive()) {
            revocation.revokeSession(session.getId(), LocalDateTime.now());
        }
        return toSessionResponse(session);
    }

    /** Same generic response for known and unknown accounts; no account probing. */
    @Transactional
    public void requestPasswordReset(String email) {
        String normalized = normalizeEmail(email);
        Optional<User> found = users.findByEmail(normalized);
        if (found.isEmpty()) {
            log.info("Password reset requested for unknown email {}", masked(normalized));
            return;
        }
        User user = found.get();
        LocalDateTime now = LocalDateTime.now();
        String raw = tokenService.opaque();
        LocalDateTime expiresAt = now.plus(passwordResetTtlMinutes, ChronoUnit.MINUTES);
        resetTokens.save(new PasswordResetToken(user, tokenService.sha256(raw), expiresAt, now));
        mailService.sendResetToken(user.getEmail(), raw, expiresAt);
    }

    @Transactional
    public void confirmPasswordReset(String token, String newPassword) {
        LocalDateTime now = LocalDateTime.now();
        PasswordResetToken reset = resetTokens.findByTokenHash(tokenService.sha256(token))
                .orElseThrow(() -> ApiException.badRequest("重置链接无效或已过期"));
        if (reset.isUsed() || reset.isExpired(now)) {
            throw ApiException.badRequest("重置链接无效或已过期");
        }
        User user = reset.getUser();
        user.updatePassword(encoder.encode(newPassword));
        users.save(user);
        reset.markUsed(now);
        resetTokens.save(reset);
        sessions.revokeAllForUser(user.getId(), now);
    }

    private AuthDtos.AuthResponse openSession(User user, AuthDtos.Device device) {
        LocalDateTime now = LocalDateTime.now();
        UserSession session = sessions.save(new UserSession(user, device.deviceName(), device.deviceId(),
                device.ipAddress(), device.userAgent(), now));
        String raw = tokenService.opaque();
        refreshTokens.save(new RefreshToken(session, tokenService.sha256(raw),
                now.plus(refreshTokenTtlMinutes, ChronoUnit.MINUTES), now));
        return new AuthDtos.AuthResponse(jwt.create(user.getUsername()), "Bearer", user.getUsername(),
                raw, session.getId());
    }

    private RefreshToken requireRefreshToken(String raw) {
        return refreshTokens.findByTokenHash(tokenService.sha256(raw))
                .orElseThrow(() -> ApiException.unauthorized("登录状态已失效，请重新登录"));
    }

    private AuthDtos.SessionResponse toSessionResponse(UserSession session) {
        return new AuthDtos.SessionResponse(session.getId(), session.getDeviceName(), session.getDeviceId(),
                session.getIpAddress(), session.getUserAgent(), session.getCreatedAt(),
                session.getLastActiveAt());
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String masked(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
