package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.server.dao.RefreshTokenRepository;
import com.fzdzzj.lifehabitassistant.server.dao.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Session revocation must survive an outer transaction that is about to roll
 * back (e.g. refresh reuse detection), so it runs in its own transaction.
 */
@Service
public class SessionRevocationService {
    private final SessionRepository sessions;
    private final RefreshTokenRepository refreshTokens;

    public SessionRevocationService(SessionRepository sessions, RefreshTokenRepository refreshTokens) {
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeSession(Long sessionId, LocalDateTime now) {
        sessions.revokeById(sessionId, now);
        refreshTokens.revokeAllForSession(sessionId, now);
    }
}
