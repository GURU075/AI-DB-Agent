package com.guru.AI_Assisted_Database_Agent.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SessionStoreServiceTest {

    @Test
    void removesExpiredSessions() throws InterruptedException {
        SessionStoreService store = new SessionStoreService(Duration.ofMillis(1));
        store.getOrCreate("session-1");
        Thread.sleep(10);

        store.removeExpiredSessions();

        assertThat(store.size()).isZero();
    }

    @Test
    void accessRefreshesSessionActivity() {
        SessionStoreService store = new SessionStoreService(Duration.ofMinutes(1));
        store.getOrCreate("session-1");

        assertThat(store.get("session-1")).isNotNull();
        assertThat(store.size()).isEqualTo(1);
    }
}

