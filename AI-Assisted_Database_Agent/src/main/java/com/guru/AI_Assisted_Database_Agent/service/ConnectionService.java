package com.guru.AI_Assisted_Database_Agent.service;

import com.guru.AI_Assisted_Database_Agent.model.ConnectionTestResult;
import com.guru.AI_Assisted_Database_Agent.model.DbConnectionRequest;
import com.guru.AI_Assisted_Database_Agent.model.DbType;
import com.guru.AI_Assisted_Database_Agent.model.SessionState;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Locale;

/**
 * Builds JDBC URLs, creates pooled DataSources (HikariCP), and tests connections
 * for PostgreSQL, Oracle, and MySQL.
 */
@Service
public class ConnectionService {

    private final ConnectionPolicyService connectionPolicy;

    public ConnectionService(ConnectionPolicyService connectionPolicy) {
        this.connectionPolicy = connectionPolicy;
    }

    /**
     * Build the JDBC URL for the given connection request.
     */
    public String buildJdbcUrl(DbConnectionRequest req) {
        connectionPolicy.validateHost(req.host());
        return switch (req.dbType()) {
            case POSTGRES -> "jdbc:postgresql://" + req.host() + ":" + req.port() + "/" + req.database()
                    + "?sslmode=" + (req.ssl() ? "require" : "disable")
                    + "&connectTimeout=10&socketTimeout=30";
            case ORACLE   -> "jdbc:oracle:thin:@" + (req.ssl() ? "tcps://" : "//")
                    + req.host() + ":" + req.port() + "/" + req.database();
            case MYSQL    -> "jdbc:mysql://" + req.host() + ":" + req.port() + "/" + req.database()
                    + "?useSSL=" + req.ssl()
                    + "&requireSSL=" + req.ssl()
                    + "&verifyServerCertificate=" + req.ssl()
                    + "&allowPublicKeyRetrieval=" + (!req.ssl())
                    + "&serverTimezone=UTC&connectTimeout=10000&socketTimeout=30000";
        };
    }

    /**
     * Returns the appropriate JDBC driver class name.
     */
    public String driverClassName(DbType dbType) {
        return switch (dbType) {
            case POSTGRES -> "org.postgresql.Driver";
            case ORACLE   -> "oracle.jdbc.OracleDriver";
            case MYSQL    -> "com.mysql.cj.jdbc.Driver";
        };
    }

    /**
     * Quick one-shot JDBC test using DriverManager (no pool).
     */
    public ConnectionTestResult testConnection(DbConnectionRequest req) {
        String url = buildJdbcUrl(req);
        try {
            // Explicitly load driver before DriverManager call
            Class.forName(driverClassName(req.dbType()));
            try (Connection con = java.sql.DriverManager.getConnection(url, req.username(), req.password())) {
                DatabaseMetaData md = con.getMetaData();
                return new ConnectionTestResult(
                        true,
                        md.getDatabaseProductName(),
                        md.getDatabaseProductVersion(),
                        "Connection successful"
                );
            }
        } catch (Exception e) {
            return new ConnectionTestResult(false, null, null, safeConnectionFailure(e));
        }
    }

    String safeConnectionFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof UnknownHostException) {
                return "The database hostname could not be resolved.";
            }
            if (cause instanceof ConnectException) {
                return "The database server refused the connection or is not running on the selected host and port.";
            }
            if (cause instanceof SocketTimeoutException) {
                return "The database connection timed out.";
            }
            if (cause instanceof SQLException sqlException) {
                String sqlState = sqlException.getSQLState();
                if (sqlState != null && sqlState.startsWith("28")) {
                    return "Database authentication failed. Verify the username and password.";
                }
                if (sqlState != null && sqlState.startsWith("3D")) {
                    return "The requested database does not exist or is not accessible to this user.";
                }
                if (sqlState != null && sqlState.startsWith("08")) {
                    return "The database server could not be reached or rejected the connection.";
                }
            }

            String message = cause.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("ssl") || normalized.contains("certificate") || normalized.contains("handshake")) {
                    return "The database TLS handshake failed. Verify the SSL setting and certificate configuration.";
                }
                if (normalized.contains("timeout") || normalized.contains("timed out")) {
                    return "The database connection timed out.";
                }
            }
        }
        return "Database connection failed. Verify the connection details and database server availability.";
    }

    /**
     * Creates a pooled HikariDataSource and attaches it to the given session.
     */
    public void createSessionDataSource(DbConnectionRequest req, SessionState state) {
        // Close old connection if re-connecting
        if (state.getDataSource() != null && !state.getDataSource().isClosed()) {
            state.getDataSource().close();
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(buildJdbcUrl(req));
        config.setDriverClassName(driverClassName(req.dbType()));
        config.setUsername(req.username());
        config.setPassword(req.password());
        config.setMaximumPoolSize(3);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(10_000);
        config.setIdleTimeout(300_000);
        config.setValidationTimeout(5_000);
        config.setInitializationFailTimeout(10_000);
        config.setLeakDetectionThreshold(60_000);
        config.setPoolName("db-agent-" + state.getSessionId().substring(0, 8));

        if (req.dbType() == DbType.ORACLE) {
            config.addDataSourceProperty("oracle.net.CONNECT_TIMEOUT", "10000");
            config.addDataSourceProperty("oracle.jdbc.ReadTimeout", "30000");
        }

        state.setDataSource(new HikariDataSource(config));
        state.setDbType(req.dbType());
    }
}
