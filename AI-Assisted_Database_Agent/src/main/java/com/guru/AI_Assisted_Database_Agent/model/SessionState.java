package com.guru.AI_Assisted_Database_Agent.model;

import com.zaxxer.hikari.HikariDataSource;

import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * Mutable per-session state held in-memory.
 * Stores the live DataSource, dialect, resolved schema, and any pending SQL plan awaiting user confirmation.
 */
public class SessionState {

    private final String sessionId;
    private HikariDataSource dataSource;
    private DbType dbType;
    private SchemaInfo schema;
    private String schemaFilter;
    private String schemaSummary;
    private PendingOperation pendingOperation;
    private final List<String> chatHistory = new CopyOnWriteArrayList<>(); // simple turn-by-turn log
    private final Instant createdAt = Instant.now();
    private volatile Instant lastAccessedAt = createdAt;

    public SessionState(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionId() { return sessionId; }

    public HikariDataSource getDataSource() { return dataSource; }
    public void setDataSource(HikariDataSource dataSource) { this.dataSource = dataSource; }

    public DbType getDbType() { return dbType; }
    public void setDbType(DbType dbType) { this.dbType = dbType; }

    public String getSchemaSummary() { return schemaSummary; }
    public void setSchemaSummary(String schemaSummary) { this.schemaSummary = schemaSummary; }

    public SchemaInfo getSchema() { return schema; }
    public void setSchema(SchemaInfo schema) { this.schema = schema; }

    public String getSchemaFilter() { return schemaFilter; }
    public void setSchemaFilter(String schemaFilter) { this.schemaFilter = schemaFilter; }

    public PendingOperation getPendingOperation() { return pendingOperation; }
    public void setPendingOperation(PendingOperation pendingOperation) { this.pendingOperation = pendingOperation; }

    public List<String> getChatHistory() { return chatHistory; }

    public void clearHistory() { chatHistory.clear(); }

    public void addHistory(String role, String content) {
        chatHistory.add("[" + role + "]: " + content);
        // keep last 20 turns to avoid prompt bloat
        if (chatHistory.size() > 20) {
            chatHistory.remove(0);
        }
    }

    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastAccessedAt() { return lastAccessedAt; }
    public void touch() { lastAccessedAt = Instant.now(); }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        pendingOperation = null;
        chatHistory.clear();
    }
}
