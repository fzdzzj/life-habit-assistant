package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.common.ApiException;
import com.fzdzzj.lifehabitassistant.common.ErrorCode;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    @Test
    void registerShouldHashPassword() {
        UserRepository users = mock(UserRepository.class);
        JwtService jwt = mock(JwtService.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        AuthDtos.Credentials input = new AuthDtos.Credentials("demo", "demo123456");
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwt.create("demo")).thenReturn("token");

        new AuthService(users, encoder, jwt).register(input);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        assertTrue(encoder.matches("demo123456", captor.getValue().getPasswordHash()));
    }

    @Test
    void registerShouldRejectDuplicateUsername() {
        UserRepository users = mock(UserRepository.class);
        when(users.existsByUsername("demo")).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class,
                () -> new AuthService(users, new BCryptPasswordEncoder(), mock(JwtService.class))
                        .register(new AuthDtos.Credentials("demo", "demo123456")));

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.errorCode());
    }

    @Test
    void loginShouldRejectWrongPassword() {
        UserRepository users = mock(UserRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        when(users.findByUsername("demo")).thenReturn(Optional.of(new User("demo", encoder.encode("correct-password"))));

        ApiException exception = assertThrows(ApiException.class,
                () -> new AuthService(users, encoder, mock(JwtService.class))
                        .login(new AuthDtos.Credentials("demo", "wrong-password")));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.errorCode());
    }
}
