package com.guru.AI_Assisted_Database_Agent.service;

import com.guru.AI_Assisted_Database_Agent.model.SessionState;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Owns all short-lived database sessions and guarantees DataSource cleanup. */
@Service
public class SessionStoreService {

    private static final Logger log = LoggerFactory.getLogger(SessionStoreService.class);

    private final ConcurrentHashMap<String, SessionState> store = new ConcurrentHashMap<>();
    private final Duration sessionTtl;

    public SessionStoreService(@Value("${app.session.ttl:PT30M}") Duration sessionTtl) {
        if (sessionTtl.isZero() || sessionTtl.isNegative()) {
            throw new IllegalArgumentException("Session TTL must be positive.");
        }
        this.sessionTtl = sessionTtl;
    }

    public SessionState getOrCreate(String sessionId) {
        SessionState state = store.computeIfAbsent(sessionId, SessionState::new);
        state.touch();
        return state;
    }

    public SessionState get(String sessionId) {
        SessionState state = store.get(sessionId);
        if (state != null) state.touch();
        return state;
    }

    public boolean remove(String sessionId) {
        SessionState state = store.remove(sessionId);
        if (state == null) return false;
        state.close();
        return true;
    }

    @Scheduled(fixedDelayString = "${app.session.cleanup-interval-ms:60000}")
    public void removeExpiredSessions() {
        Instant cutoff = Instant.now().minus(sessionTtl);
        store.forEach((sessionId, state) -> {
            if (state.getLastAccessedAt().isBefore(cutoff) && store.remove(sessionId, state)) {
                state.close();
                log.info("Expired inactive database session");
            }
        });
    }

    int size() {
        return store.size();
    }

    @PreDestroy
    public void closeAll() {
        store.forEach((sessionId, state) -> state.close());
        store.clear();
    }
}
