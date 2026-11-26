package com.guru.AI_Assisted_Database_Agent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Restricts outbound database connections to explicitly approved hosts. */
@Service
public class ConnectionPolicyService {

    private final List<String> allowedHosts;

    public ConnectionPolicyService(@Value("${app.connection.allowed-hosts:localhost,127.0.0.1,::1}") String allowedHosts) {
        this.allowedHosts = Arrays.stream(allowedHosts.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
        if (this.allowedHosts.isEmpty()) {
            throw new IllegalArgumentException("At least one database host must be allowed.");
        }
    }

    public void validateHost(String requestedHost) {
        String host = normalize(requestedHost);
        boolean allowed = allowedHosts.stream().anyMatch(pattern -> matches(pattern, host));
        if (!allowed) {
            throw new ConnectionPolicyException("The requested database host is not approved by server policy.");
        }
    }

    private boolean matches(String pattern, String host) {
        if (pattern.startsWith("*.")) {
            String suffix = pattern.substring(1);
            return host.endsWith(suffix) && host.length() > suffix.length();
        }
        return pattern.equals(host);
    }

    private String normalize(String value) {
        if (value == null) return "";
        String host = value.trim().toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.contains("/") || host.contains("\\") || host.contains("@") || host.contains("?") || host.contains("#")) {
            throw new ConnectionPolicyException("Database host must be a hostname or IP address, not a URL.");
        }
        return host;
    }

    public static class ConnectionPolicyException extends RuntimeException {
        public ConnectionPolicyException(String message) {
            super(message);
        }
    }
}

