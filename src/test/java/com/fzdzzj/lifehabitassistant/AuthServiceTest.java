package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.common.ApiException;
import com.fzdzzj.lifehabitassistant.common.ErrorCode;
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
import com.fzdzzj.lifehabitassistant.server.service.AuthService;
import com.fzdzzj.lifehabitassistant.server.service.CurrentUser;
import com.fzdzzj.lifehabitassistant.server.service.JwtService;
import com.fzdzzj.lifehabitassistant.server.service.PasswordResetMailService;
import com.fzdzzj.lifehabitassistant.server.service.SessionRevocationService;
import com.fzdzzj.lifehabitassistant.server.service.TokenService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    private static final String CLIENT_IP = "127.0.0.1";
    private static final AuthDtos.Device DEVICE = new AuthDtos.Device("browser", "dev-1", CLIENT_IP, "test-agent");

    @Test
    void registerShouldHashPasswordAndOpenSession() {
        UserRepository users = mock(UserRepository.class);
        SessionRepository sessions = mock(SessionRepository.class);
        RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
        PasswordResetTokenRepository resetTokens = mock(PasswordResetTokenRepository.class);
        JwtService jwt = mock(JwtService.class);
        AuthRateLimiter rateLimiter = mock(AuthRateLimiter.class);
        TokenService tokens = mock(TokenService.class);
        PasswordResetMailService mail = mock(PasswordResetMailService.class);
        when(rateLimiter.tryRegister(CLIENT_IP)).thenReturn(true);
        when(tokens.opaque()).thenReturn("raw-refresh");
        when(tokens.sha256(anyString())).thenReturn("hash");
        when(jwt.create("demo")).thenReturn("access");
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessions.save(any(UserSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokens.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthDtos.AuthResponse response = service(users, encoder, jwt, rateLimiter, sessions,
                        refreshTokens, resetTokens, tokens, mail, mock(SessionRevocationService.class),
                        mock(CurrentUser.class))
                .register(new AuthDtos.Credentials("demo", "demo123456"), CLIENT_IP, DEVICE);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        assertTrue(encoder.matches("demo123456", captor.getValue().getPasswordHash()));
        assertEquals("access", response.token());
        assertEquals("raw-refresh", response.refreshToken());
        assertNull(response.sessionId());
        verify(sessions).save(any(UserSession.class));
        verify(refreshTokens).save(any(RefreshToken.class));
    }

    @Test
    void registerShouldRejectDuplicateUsername() {
        UserRepository users = mock(UserRepository.class);
        AuthRateLimiter rateLimiter = mock(AuthRateLimiter.class);
        when(rateLimiter.tryRegister(CLIENT_IP)).thenReturn(true);
        when(users.existsByUsername("demo")).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class,
                () -> service(users, new BCryptPasswordEncoder(), mock(JwtService.class), rateLimiter,
                                mock(SessionRepository.class), mock(RefreshTokenRepository.class),
                                mock(PasswordResetTokenRepository.class), mock(TokenService.class),
                                mock(PasswordResetMailService.class), mock(SessionRevocationService.class),
                                mock(CurrentUser.class))
                        .register(new AuthDtos.Credentials("demo", "demo123456"), CLIENT_IP, DEVICE));

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.errorCode());
    }

    @Test
    void registerShouldRejectDuplicateEmail() {
        UserRepository users = mock(UserRepository.class);
        AuthRateLimiter rateLimiter = mock(AuthRateLimiter.class);
        when(rateLimiter.tryRegister(CLIENT_IP)).thenReturn(true);
        when(users.existsByEmail("a@b.com")).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class,
                () -> service(users, new BCryptPasswordEncoder(), mock(JwtService.class), rateLimiter,
                                mock(SessionRepository.class), mock(RefreshTokenRepository.class),
                                mock(PasswordResetTokenRepository.class), mock(TokenService.class),
                                mock(PasswordResetMailService.class), mock(SessionRevocationService.class),
                                mock(CurrentUser.class))
                        .register(new AuthDtos.Credentials("demo", "demo123456", "A@B.com", null, null),
                                CLIENT_IP, DEVICE));

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.errorCode());
    }

    @Test
    void loginShouldRejectWrongPassword() {
        UserRepository users = mock(UserRepository.class);
        AuthRateLimiter rateLimiter = mock(AuthRateLimiter.class);
        when(rateLimiter.tryLogin(CLIENT_IP, "demo")).thenReturn(true);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        when(users.findByUsername("demo")).thenReturn(Optional.of(new User("demo", encoder.encode("correct-password"))));

        ApiException exception = assertThrows(ApiException.class,
                () -> service(users, encoder, mock(JwtService.class), rateLimiter,
                                mock(SessionRepository.class), mock(RefreshTokenRepository.class),
                                mock(PasswordResetTokenRepository.class), mock(TokenService.class),
                                mock(PasswordResetMailService.class), mock(SessionRevocationService.class),
                                mock(CurrentUser.class))
                        .login(new AuthDtos.Credentials("demo", "wrong-password"), CLIENT_IP, DEVICE));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.errorCode());
    }

    @Test
    void loginShouldRejectDisabledUser() {
        UserRepository users = mock(UserRepository.class);
        AuthRateLimiter rateLimiter = mock(AuthRateLimiter.class);
        when(rateLimiter.tryLogin(CLIENT_IP, "demo")).thenReturn(true);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        User user = new User("demo", encoder.encode("correct-password"));
        user.setEnabled(false);
        when(users.findByUsername("demo")).thenReturn(Optional.of(user));

        ApiException exception = assertThrows(ApiException.class,
                () -> service(users, encoder, mock(JwtService.class), rateLimiter,
                                mock(SessionRepository.class), mock(RefreshTokenRepository.class),
                                mock(PasswordResetTokenRepository.class), mock(TokenService.class),
                                mock(PasswordResetMailService.class), mock(SessionRevocationService.class),
                                mock(CurrentUser.class))
                        .login(new AuthDtos.Credentials("demo", "correct-password"), CLIENT_IP, DEVICE));

        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode());
    }

    @Test
    void registerShouldRejectWhenPerIpLimitExceeded() {
        AuthRateLimiter rateLimiter = mock(AuthRateLimiter.class);
        when(rateLimiter.tryRegister(CLIENT_IP)).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class,
                () -> service(mock(UserRepository.class), new BCryptPasswordEncoder(),
                                mock(JwtService.class), rateLimiter,
                                mock(SessionRepository.class), mock(RefreshTokenRepository.class),
                                mock(PasswordResetTokenRepository.class), mock(TokenService.class),
                                mock(PasswordResetMailService.class), mock(SessionRevocationService.class),
                                mock(CurrentUser.class))
                        .register(new AuthDtos.Credentials("demo", "demo123456"), CLIENT_IP, DEVICE));

        assertEquals(ErrorCode.TOO_MANY_REQUESTS, exception.errorCode());
    }

    @Test
    void loginShouldRejectWhenLockedOut() {
        AuthRateLimiter rateLimiter = mock(AuthRateLimiter.class);
        when(rateLimiter.tryLogin(CLIENT_IP, "demo")).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class,
                () -> service(mock(UserRepository.class), new BCryptPasswordEncoder(),
                                mock(JwtService.class), rateLimiter,
                                mock(SessionRepository.class), mock(RefreshTokenRepository.class),
                                mock(PasswordResetTokenRepository.class), mock(TokenService.class),
                                mock(PasswordResetMailService.class), mock(SessionRevocationService.class),
                                mock(CurrentUser.class))
                        .login(new AuthDtos.Credentials("demo", "demo123456"), CLIENT_IP, DEVICE));

        assertEquals(ErrorCode.TOO_MANY_REQUESTS, exception.errorCode());
        verify(rateLimiter, never()).recordLoginFailure(anyString(), anyString());
    }

    @Test
    void loginShouldRecordFailureForUnknownUserToo() {
        UserRepository users = mock(UserRepository.class);
        AuthRateLimiter rateLimiter = mock(AuthRateLimiter.class);
        when(rateLimiter.tryLogin(CLIENT_IP, "ghost")).thenReturn(true);
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class,
                () -> service(users, new BCryptPasswordEncoder(), mock(JwtService.class), rateLimiter,
                                mock(SessionRepository.class), mock(RefreshTokenRepository.class),
                                mock(PasswordResetTokenRepository.class), mock(TokenService.class),
                                mock(PasswordResetMailService.class), mock(SessionRevocationService.class),
                                mock(CurrentUser.class))
                        .login(new AuthDtos.Credentials("ghost", "demo123456"), CLIENT_IP, DEVICE));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.errorCode());
        verify(rateLimiter).recordLoginFailure(CLIENT_IP, "ghost");
    }

    @Test
    void loginShouldClearFailuresAfterSuccess() {
        UserRepository users = mock(UserRepository.class);
        AuthRateLimiter rateLimiter = mock(AuthRateLimiter.class);
        SessionRepository sessions = mock(SessionRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        when(rateLimiter.tryLogin(CLIENT_IP, "demo")).thenReturn(true);
        when(users.findByUsername("demo")).thenReturn(Optional.of(new User("demo", encoder.encode("correct-password"))));
        when(sessions.save(any(UserSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service(users, encoder, mock(JwtService.class), rateLimiter,
                        sessions, mock(RefreshTokenRepository.class),
                        mock(PasswordResetTokenRepository.class), mock(TokenService.class),
                        mock(PasswordResetMailService.class), mock(SessionRevocationService.class),
                        mock(CurrentUser.class))
                .login(new AuthDtos.Credentials("demo", "correct-password"), CLIENT_IP, DEVICE);

        verify(rateLimiter).recordLoginSuccess(CLIENT_IP, "demo");
    }

    @Test
    void refreshShouldRotateAndIssueNewPair() {
        User user = new User("demo", "hash");
        UserSession session = new UserSession(user, "browser", "dev-1", CLIENT_IP, "agent", LocalDateTime.now());
        RefreshToken token = new RefreshToken(session, "old-hash", LocalDateTime.now().plusDays(30), LocalDateTime.now());
        RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
        SessionRepository sessions = mock(SessionRepository.class);
        TokenService tokens = mock(TokenService.class);
        JwtService jwt = mock(JwtService.class);
        when(tokens.sha256("old-raw")).thenReturn("old-hash");
        when(tokens.sha256("new-raw")).thenReturn("new-hash");
        when(tokens.opaque()).thenReturn("new-raw");
        when(refreshTokens.findByTokenHash("old-hash")).thenReturn(Optional.of(token));
        when(refreshTokens.markRevokedIfActive(any(), any(LocalDateTime.class))).thenReturn(1);
        when(refreshTokens.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessions.save(any(UserSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwt.create("demo")).thenReturn("new-access");

        AuthDtos.AuthResponse response = service(mock(UserRepository.class), new BCryptPasswordEncoder(),
                        jwt, mock(AuthRateLimiter.class), sessions, refreshTokens,
                        mock(PasswordResetTokenRepository.class), tokens,
                        mock(PasswordResetMailService.class), mock(SessionRevocationService.class),
                        mock(CurrentUser.class))
                .refresh(new AuthDtos.RefreshRequest("old-raw"));

        assertEquals("new-access", response.token());
        assertEquals("new-raw", response.refreshToken());
        verify(refreshTokens).save(any(RefreshToken.class));
        verify(sessions).save(any(UserSession.class));
    }

    @Test
    void refreshReuseShouldRevokeWholeSession() {
        User user = new User("demo", "hash");
        UserSession session = new UserSession(user, "browser", "dev-1", CLIENT_IP, "agent", LocalDateTime.now());
        RefreshToken token = new RefreshToken(session, "old-hash", LocalDateTime.now().plusDays(30), LocalDateTime.now());
        token.revoke(LocalDateTime.now());
        RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
        SessionRevocationService revocation = mock(SessionRevocationService.class);
        TokenService tokens = mock(TokenService.class);
        when(tokens.sha256("old-raw")).thenReturn("old-hash");
        when(refreshTokens.findByTokenHash("old-hash")).thenReturn(Optional.of(token));

        ApiException exception = assertThrows(ApiException.class,
                () -> service(mock(UserRepository.class), new BCryptPasswordEncoder(),
                                mock(JwtService.class), mock(AuthRateLimiter.class),
                                mock(SessionRepository.class), refreshTokens,
                                mock(PasswordResetTokenRepository.class), tokens,
                                mock(PasswordResetMailService.class), revocation,
                                mock(CurrentUser.class))
                        .refresh(new AuthDtos.RefreshRequest("old-raw")));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.errorCode());
        verify(revocation).revokeSession(eq(session.getId()), any(LocalDateTime.class));
    }

    @Test
    void refreshRaceShouldRevokeWholeSessionWhenRotationLoses() {
        User user = new User("demo", "hash");
        UserSession session = new UserSession(user, "browser", "dev-1", CLIENT_IP, "agent", LocalDateTime.now());
        RefreshToken token = new RefreshToken(session, "old-hash", LocalDateTime.now().plusDays(30), LocalDateTime.now());
        RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
        SessionRevocationService revocation = mock(SessionRevocationService.class);
        TokenService tokens = mock(TokenService.class);
        when(tokens.sha256("old-raw")).thenReturn("old-hash");
        when(refreshTokens.findByTokenHash("old-hash")).thenReturn(Optional.of(token));
        when(refreshTokens.markRevokedIfActive(any(), any(LocalDateTime.class))).thenReturn(0);

        ApiException exception = assertThrows(ApiException.class,
                () -> service(mock(UserRepository.class), new BCryptPasswordEncoder(),
                                mock(JwtService.class), mock(AuthRateLimiter.class),
                                mock(SessionRepository.class), refreshTokens,
                                mock(PasswordResetTokenRepository.class), tokens,
                                mock(PasswordResetMailService.class), revocation,
                                mock(CurrentUser.class))
                        .refresh(new AuthDtos.RefreshRequest("old-raw")));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.errorCode());
        verify(revocation).revokeSession(eq(session.getId()), any(LocalDateTime.class));
    }

    @Test
    void logoutShouldRevokeOwningSession() {
        User user = new User("demo", "hash");
        UserSession session = new UserSession(user, "browser", "dev-1", CLIENT_IP, "agent", LocalDateTime.now());
        RefreshToken token = new RefreshToken(session, "hash", LocalDateTime.now().plusDays(30), LocalDateTime.now());
        RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
        SessionRevocationService revocation = mock(SessionRevocationService.class);
        TokenService tokens = mock(TokenService.class);
        when(tokens.sha256("raw")).thenReturn("hash");
        when(refreshTokens.findByTokenHash("hash")).thenReturn(Optional.of(token));

        service(mock(UserRepository.class), new BCryptPasswordEncoder(), mock(JwtService.class),
                        mock(AuthRateLimiter.class), mock(SessionRepository.class), refreshTokens,
                        mock(PasswordResetTokenRepository.class), tokens,
                        mock(PasswordResetMailService.class), revocation,
                        mock(CurrentUser.class))
                .logout(new AuthDtos.RefreshRequest("raw"));

        verify(revocation).revokeSession(eq(session.getId()), any(LocalDateTime.class));
    }

    @Test
    void requestPasswordResetShouldNotCreateTokenForUnknownEmail() {
        UserRepository users = mock(UserRepository.class);
        PasswordResetTokenRepository resetTokens = mock(PasswordResetTokenRepository.class);
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        service(users, new BCryptPasswordEncoder(), mock(JwtService.class), mock(AuthRateLimiter.class),
                        mock(SessionRepository.class), mock(RefreshTokenRepository.class), resetTokens,
                        mock(TokenService.class), mock(PasswordResetMailService.class),
                        mock(SessionRevocationService.class), mock(CurrentUser.class))
                .requestPasswordReset("GHOST@example.com");

        verify(resetTokens, never()).save(any(PasswordResetToken.class));
        verify(users).findByEmail("ghost@example.com");
    }

    @Test
    void requestPasswordResetShouldStoreHashedTokenAndSendMail() {
        User user = new User("demo", "hash", "demo@example.com");
        UserRepository users = mock(UserRepository.class);
        PasswordResetTokenRepository resetTokens = mock(PasswordResetTokenRepository.class);
        TokenService tokens = mock(TokenService.class);
        PasswordResetMailService mail = mock(PasswordResetMailService.class);
        when(users.findByEmail("demo@example.com")).thenReturn(Optional.of(user));
        when(tokens.opaque()).thenReturn("reset-raw");
        when(tokens.sha256("reset-raw")).thenReturn("reset-hash");
        when(resetTokens.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service(users, new BCryptPasswordEncoder(), mock(JwtService.class), mock(AuthRateLimiter.class),
                        mock(SessionRepository.class), mock(RefreshTokenRepository.class), resetTokens,
                        tokens, mail, mock(SessionRevocationService.class), mock(CurrentUser.class))
                .requestPasswordReset("demo@example.com");

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetTokens).save(captor.capture());
        assertEquals("reset-hash", captor.getValue().getTokenHash());
        verify(mail).sendResetToken(eq("demo@example.com"), eq("reset-raw"), any(LocalDateTime.class));
    }

    @Test
    void confirmPasswordResetShouldResetPasswordAndRevokeSessions() {
        User user = new User("demo", "old-hash", "demo@example.com");
        PasswordResetToken reset = new PasswordResetToken(user, "reset-hash",
                LocalDateTime.now().plusMinutes(30), LocalDateTime.now());
        UserRepository users = mock(UserRepository.class);
        PasswordResetTokenRepository resetTokens = mock(PasswordResetTokenRepository.class);
        SessionRepository sessions = mock(SessionRepository.class);
        TokenService tokens = mock(TokenService.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        when(tokens.sha256("reset-raw")).thenReturn("reset-hash");
        when(resetTokens.findByTokenHash("reset-hash")).thenReturn(Optional.of(reset));
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(resetTokens.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service(users, encoder, mock(JwtService.class), mock(AuthRateLimiter.class), sessions,
                        mock(RefreshTokenRepository.class), resetTokens, tokens,
                        mock(PasswordResetMailService.class), mock(SessionRevocationService.class),
                        mock(CurrentUser.class))
                .confirmPasswordReset("reset-raw", "new-password");

        assertTrue(encoder.matches("new-password", user.getPasswordHash()));
        assertNotNull(reset.getUsedAt());
        verify(sessions).revokeAllForUser(eq(user.getId()), any(LocalDateTime.class));
    }

    @Test
    void confirmPasswordResetShouldRejectUsedToken() {
        User user = new User("demo", "old-hash");
        PasswordResetToken reset = new PasswordResetToken(user, "reset-hash",
                LocalDateTime.now().plusMinutes(30), LocalDateTime.now());
        reset.markUsed(LocalDateTime.now());
        PasswordResetTokenRepository resetTokens = mock(PasswordResetTokenRepository.class);
        TokenService tokens = mock(TokenService.class);
        when(tokens.sha256("reset-raw")).thenReturn("reset-hash");
        when(resetTokens.findByTokenHash("reset-hash")).thenReturn(Optional.of(reset));

        ApiException exception = assertThrows(ApiException.class,
                () -> service(mock(UserRepository.class), new BCryptPasswordEncoder(),
                                mock(JwtService.class), mock(AuthRateLimiter.class),
                                mock(SessionRepository.class), mock(RefreshTokenRepository.class),
                                resetTokens, tokens, mock(PasswordResetMailService.class),
                                mock(SessionRevocationService.class), mock(CurrentUser.class))
                        .confirmPasswordReset("reset-raw", "new-password"));

        assertEquals(ErrorCode.VALIDATION_FAILED, exception.errorCode());
        assertEquals("old-hash", user.getPasswordHash());
    }

    private AuthService service(UserRepository users, PasswordEncoder encoder, JwtService jwt,
                                AuthRateLimiter rateLimiter, SessionRepository sessions,
                                RefreshTokenRepository refreshTokens, PasswordResetTokenRepository resetTokens,
                                TokenService tokens, PasswordResetMailService mail,
                                SessionRevocationService revocation, CurrentUser currentUser) {
        return new AuthService(users, encoder, jwt, rateLimiter, sessions, refreshTokens, resetTokens,
                tokens, mail, revocation, currentUser, 43200, 30);
    }

}
