package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.common.ApiException;
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

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public AuthDtos.AuthResponse register(AuthDtos.Credentials input) {
        if (users.existsByUsername(input.username())) throw ApiException.conflict("用户名已存在");
        User user = users.save(new User(input.username(), encoder.encode(input.password())));
        return new AuthDtos.AuthResponse(jwt.create(user.getUsername()), "Bearer", user.getUsername());
    }

    public AuthDtos.AuthResponse login(AuthDtos.Credentials input) {
        User user = users.findByUsername(input.username()).orElseThrow(() -> ApiException.unauthorized("用户名或密码错误"));
        if (!encoder.matches(input.password(), user.getPasswordHash()))
            throw ApiException.unauthorized("用户名或密码错误");
        return new AuthDtos.AuthResponse(jwt.create(user.getUsername()), "Bearer", user.getUsername());
    }
}
