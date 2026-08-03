package com.fzdzzj.lifehabitassistant.server.controller;

import com.fzdzzj.lifehabitassistant.common.Result;
import com.fzdzzj.lifehabitassistant.config.ClientIpResolver;
import com.fzdzzj.lifehabitassistant.pojo.AuthDtos;
import com.fzdzzj.lifehabitassistant.server.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    private final ClientIpResolver ipResolver;

    public AuthController(AuthService service, ClientIpResolver ipResolver) {
        this.service = service;
        this.ipResolver = ipResolver;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    Result<AuthDtos.AuthResponse> register(@Valid @RequestBody AuthDtos.Credentials input, HttpServletRequest request) {
        return Result.success(service.register(input, ipResolver.resolve(request)));
    }

    @PostMapping("/login")
    Result<AuthDtos.AuthResponse> login(@Valid @RequestBody AuthDtos.Credentials input, HttpServletRequest request) {
        return Result.success(service.login(input, ipResolver.resolve(request)));
    }
}
