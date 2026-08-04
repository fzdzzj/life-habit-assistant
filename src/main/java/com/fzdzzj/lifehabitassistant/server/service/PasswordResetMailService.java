package com.fzdzzj.lifehabitassistant.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Sends password-reset tokens through the configured SMTP channel. When no
 * mail server is configured (dev), the token is written to the log instead so
 * local acceptance flows can still complete.
 */
@Component
public class PasswordResetMailService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String mailHost;

    public PasswordResetMailService(ObjectProvider<JavaMailSender> mailSender,
                                    @Value("${spring.mail.host:}") String mailHost) {
        this.mailSender = mailSender;
        this.mailHost = mailHost;
    }

    public void sendResetToken(String to, String token, LocalDateTime expiresAt) {
        String expires = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(expiresAt);
        if (mailHost == null || mailHost.isBlank()) {
            log.info("Password reset token for {} (expires {}): {}", to, expires, token);
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("spring.mail.host configured but JavaMailSender unavailable; token for {} logged instead", to);
            log.info("Password reset token for {} (expires {}): {}", to, expires, token);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("生活习惯助手 - 密码重置");
        message.setText("您好，请使用以下一次性令牌重置密码，有效期至 " + expires + "：\n\n" + token);
        sender.send(message);
    }
}
