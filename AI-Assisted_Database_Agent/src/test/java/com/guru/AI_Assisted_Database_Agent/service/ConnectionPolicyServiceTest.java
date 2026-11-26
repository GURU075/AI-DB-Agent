package com.guru.AI_Assisted_Database_Agent.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectionPolicyServiceTest {

    private final ConnectionPolicyService policy = new ConnectionPolicyService(
            "localhost,127.0.0.1,*.internal.example.com"
    );

    @Test
    void permitsExactAndWildcardHosts() {
        assertThatNoException().isThrownBy(() -> policy.validateHost("localhost"));
        assertThatNoException().isThrownBy(() -> policy.validateHost("db.internal.example.com"));
    }

    @Test
    void rejectsUnapprovedHost() {
        assertThatThrownBy(() -> policy.validateHost("database.example.net"))
                .isInstanceOf(ConnectionPolicyService.ConnectionPolicyException.class)
                .hasMessageContaining("not approved");
    }

    @Test
    void rejectsUrlInsteadOfHost() {
        assertThatThrownBy(() -> policy.validateHost("https://localhost/database"))
                .isInstanceOf(ConnectionPolicyService.ConnectionPolicyException.class)
                .hasMessageContaining("not a URL");
    }
}

