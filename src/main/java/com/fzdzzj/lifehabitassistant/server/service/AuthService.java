package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.common.ApiException;
import com.fzdzzj.lifehabitassistant.config.AuthRateLimiter;
import com.fzdzzj.lifehabitassistant.pojo.AuthDtos;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.dao.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuthRateLimiter rateLimiter;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, AuthRateLimiter rateLimiter) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.rateLimiter = rateLimiter;
    }

    public AuthDtos.AuthResponse register(AuthDtos.Credentials input, String clientIp) {
        if (!rateLimiter.tryRegister(clientIp)) {
            throw ApiException.tooManyRequests("注册过于频繁，请稍后再试");
        }
        if (users.existsByUsername(input.username())) throw ApiException.conflict("用户名已存在");
        User user = users.save(new User(input.username(), encoder.encode(input.password())));
        return new AuthDtos.AuthResponse(jwt.create(user.getUsername()), "Bearer", user.getUsername());
    }

    public AuthDtos.AuthResponse login(AuthDtos.Credentials input, String clientIp) {
        if (!rateLimiter.tryLogin(clientIp, input.username())) {
            throw ApiException.tooManyRequests("登录尝试过于频繁，请稍后再试");
        }
        User user = users.findByUsername(input.username()).orElse(null);
        if (user == null || !encoder.matches(input.password(), user.getPasswordHash())) {
            rateLimiter.recordLoginFailure(clientIp, input.username());
            throw ApiException.unauthorized("用户名或密码错误");
        }
        rateLimiter.recordLoginSuccess(clientIp, input.username());
        return new AuthDtos.AuthResponse(jwt.create(user.getUsername()), "Bearer", user.getUsername());
    }
}
