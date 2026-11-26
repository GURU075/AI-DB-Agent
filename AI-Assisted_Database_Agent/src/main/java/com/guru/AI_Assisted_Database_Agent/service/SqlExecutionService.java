package com.guru.AI_Assisted_Database_Agent.service;

import com.guru.AI_Assisted_Database_Agent.model.SqlPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executes only validated SQL with bounded time, rows, and transaction scope. */
@Service
public class SqlExecutionService {

    private static final Logger log = LoggerFactory.getLogger(SqlExecutionService.class);
    private static final int MAX_ROWS = 100;

    private final int queryTimeoutSeconds;

    public SqlExecutionService(@Value("${app.sql.query-timeout-seconds:30}") int queryTimeoutSeconds) {
        if (queryTimeoutSeconds < 1 || queryTimeoutSeconds > 300) {
            throw new IllegalArgumentException("Query timeout must be between 1 and 300 seconds.");
        }
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    public record ExecutionResult(
            List<String> columns,
            List<Map<String, Object>> rows,
            int affectedRows,
            String message
    ) {}

    public ExecutionResult executeSelect(SqlPlan plan, DataSource dataSource) {
        NamedParameterJdbcTemplate jdbc = template(dataSource);
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();

        jdbc.query(plan.sql(), parameters(plan), resultSet -> {
            ResultSetMetaData metadata = resultSet.getMetaData();
            int columnCount = metadata.getColumnCount();
            if (columns.isEmpty()) {
                for (int index = 1; index <= columnCount; index++) {
                    columns.add(metadata.getColumnLabel(index));
                }
            }

            Map<String, Object> row = new LinkedHashMap<>();
            for (int index = 1; index <= columnCount; index++) {
                row.put(metadata.getColumnLabel(index), resultSet.getObject(index));
            }
            rows.add(row);
        });

        log.info("Read query completed with {} row(s)", rows.size());
        String message = rows.isEmpty()
                ? "Query completed successfully. No rows were returned."
                : "Returned " + rows.size() + (rows.size() == MAX_ROWS ? " rows (limited to " + MAX_ROWS + ")." : " rows.");
        return new ExecutionResult(List.copyOf(columns), List.copyOf(rows), 0, message);
    }

    public ExecutionResult executeWrite(SqlPlan plan, DataSource dataSource) {
        NamedParameterJdbcTemplate jdbc = template(dataSource);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        Integer affectedRows = transaction.execute(status -> jdbc.update(plan.sql(), parameters(plan)));
        int affected = affectedRows == null ? 0 : affectedRows;
        log.info("Confirmed {} completed with {} affected row(s)", plan.intent(), affected);
        return new ExecutionResult(
                List.of(),
                List.of(),
                affected,
                plan.intent() + " completed successfully. " + affected + " row(s) affected."
        );
    }

    public ExecutionResult execute(SqlPlan plan, DataSource dataSource) {
        return switch (plan.intent().toUpperCase()) {
            case "SELECT" -> executeSelect(plan, dataSource);
            case "INSERT", "UPDATE", "DELETE" -> executeWrite(plan, dataSource);
            default -> throw new IllegalArgumentException("Unsupported SQL intent: " + plan.intent());
        };
    }

    private NamedParameterJdbcTemplate template(DataSource dataSource) {
        NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(dataSource);
        template.getJdbcTemplate().setMaxRows(MAX_ROWS);
        template.getJdbcTemplate().setQueryTimeout(queryTimeoutSeconds);
        return template;
    }

    private MapSqlParameterSource parameters(SqlPlan plan) {
        MapSqlParameterSource source = new MapSqlParameterSource();
        if (plan.params() != null) source.addValues(plan.params());
        return source;
    }
}
