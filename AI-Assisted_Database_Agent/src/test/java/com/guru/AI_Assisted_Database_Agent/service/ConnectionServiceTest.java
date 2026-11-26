package com.guru.AI_Assisted_Database_Agent.service;

import com.guru.AI_Assisted_Database_Agent.model.DbConnectionRequest;
import com.guru.AI_Assisted_Database_Agent.model.DbType;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionServiceTest {

    private final ConnectionService service = new ConnectionService(new ConnectionPolicyService("db.example.com"));

    @Test
    void buildsTlsPostgresUrlWithResourceTimeouts() {
        DbConnectionRequest request = request(DbType.POSTGRES, true);

        assertThat(service.buildJdbcUrl(request))
                .isEqualTo("jdbc:postgresql://db.example.com:5432/app?sslmode=require&connectTimeout=10&socketTimeout=30");
    }

    @Test
    void buildsVerifiedTlsMysqlUrl() {
        DbConnectionRequest request = request(DbType.MYSQL, true);

        assertThat(service.buildJdbcUrl(request))
                .contains("useSSL=true")
                .contains("requireSSL=true")
                .contains("verifyServerCertificate=true")
                .contains("allowPublicKeyRetrieval=false");
    }

    @Test
    void classifiesAuthenticationFailuresWithoutExposingDriverDetails() {
        SQLException failure = new SQLException("password authentication failed for user secret_user", "28P01");

        assertThat(service.safeConnectionFailure(failure))
                .isEqualTo("Database authentication failed. Verify the username and password.")
                .doesNotContain("secret_user");
    }

    @Test
    void classifiesNetworkFailures() {
        assertThat(service.safeConnectionFailure(new UnknownHostException("private-host")))
                .isEqualTo("The database hostname could not be resolved.");
        assertThat(service.safeConnectionFailure(new ConnectException("Connection refused")))
                .contains("refused");
    }

    private DbConnectionRequest request(DbType type, boolean ssl) {
        int port = type == DbType.POSTGRES ? 5432 : 3306;
        return new DbConnectionRequest(type, "db.example.com", port, "app", "user", "secret", "public", ssl);
    }
}
