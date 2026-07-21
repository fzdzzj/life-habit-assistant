package com.fzdzzj.lifehabitassistant.server.controller;
import com.fzdzzj.lifehabitassistant.pojo.AuthDtos;
import com.fzdzzj.lifehabitassistant.server.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/auth") public class AuthController {
    private final AuthService service; public AuthController(AuthService service) { this.service = service; }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED) AuthDtos.AuthResponse register(@Valid @RequestBody AuthDtos.Credentials input) { return service.register(input); }
    @PostMapping("/login") AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.Credentials input) { return service.login(input); }
}
