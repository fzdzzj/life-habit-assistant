package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.common.ApiException;
import com.fzdzzj.lifehabitassistant.common.ErrorCode;
import com.fzdzzj.lifehabitassistant.config.AuthRateLimiter;
import com.fzdzzj.lifehabitassistant.pojo.AuthDtos;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.dao.UserRepository;
import com.fzdzzj.lifehabitassistant.server.service.AuthService;
import com.fzdzzj.lifehabitassistant.server.service.JwtService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    private static final String CLIENT_IP = "127.0.0.1";

    @Test
    void registerShouldHashPassword() {
        UserRepository users = mock(UserRepository.class);
        JwtService jwt = mock(JwtService.class);
        AuthRateLimiter rateLimiter = mock(AuthRateLimiter.class);
        when(rateLimiter.tryRegister(CLIENT_IP)).thenReturn(true);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        AuthDtos.Credentials input = new AuthDtos.Credentials("demo", "demo123456");
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwt.create("demo")).thenReturn("token");

        new AuthService(users, encoder, jwt, rateLimiter).register(input, CLIENT_IP);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        assertTrue(encoder.matches("demo123456", captor.getValue().getPasswordHash()));
    }

    @Test
    void registerShouldRejectDuplicateUsername() {
        UserRepository users = mock(UserRepository.class);
        AuthRateLimiter rateLimiter = mock(AuthRateLimiter.class);
        when(rateLimiter.tryRegister(CLIENT_IP)).thenReturn(true);
        when(users.existsByUsername("demo")).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class,
                () -> new AuthService(users, new BCryptPasswordEncoder(), mock(JwtService.class), rateLimiter)
                        .register(new AuthDtos.Credentials("demo", "demo123456"), CLIENT_IP));

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
                () -> new AuthService(users, encoder, mock(JwtService.class), rateLimiter)
                        .login(new AuthDtos.Credentials("demo", "wrong-password"), CLIENT_IP));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.errorCode());
    }

    @Test
    void registerShouldRejectWhenPerIpLimitExceeded() {
        AuthRateLimiter rateLimiter = mock(AuthRateLimiter.class);
        when(rateLimiter.tryRegister(CLIENT_IP)).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class,
                () -> new AuthService(mock(UserRepository.class), new BCryptPasswordEncoder(),
                                mock(JwtService.class), rateLimiter)
                        .register(new AuthDtos.Credentials("demo", "demo123456"), CLIENT_IP));

        assertEquals(ErrorCode.TOO_MANY_REQUESTS, exception.errorCode());
    }

    @Test
    void loginShouldRejectWhenLockedOut() {
        AuthRateLimiter rateLimiter = mock(AuthRateLimiter.class);
        when(rateLimiter.tryLogin(CLIENT_IP, "demo")).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class,
                () -> new AuthService(mock(UserRepository.class), new BCryptPasswordEncoder(),
                                mock(JwtService.class), rateLimiter)
                        .login(new AuthDtos.Credentials("demo", "demo123456"), CLIENT_IP));

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
                () -> new AuthService(users, new BCryptPasswordEncoder(), mock(JwtService.class), rateLimiter)
                        .login(new AuthDtos.Credentials("ghost", "demo123456"), CLIENT_IP));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.errorCode());
        verify(rateLimiter).recordLoginFailure(CLIENT_IP, "ghost");
    }

    @Test
    void loginShouldClearFailuresAfterSuccess() {
        UserRepository users = mock(UserRepository.class);
        AuthRateLimiter rateLimiter = mock(AuthRateLimiter.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        when(rateLimiter.tryLogin(CLIENT_IP, "demo")).thenReturn(true);
        when(users.findByUsername("demo")).thenReturn(Optional.of(new User("demo", encoder.encode("correct-password"))));

        new AuthService(users, encoder, mock(JwtService.class), rateLimiter)
                .login(new AuthDtos.Credentials("demo", "correct-password"), CLIENT_IP);

        verify(rateLimiter).recordLoginSuccess(CLIENT_IP, "demo");
    }
}
