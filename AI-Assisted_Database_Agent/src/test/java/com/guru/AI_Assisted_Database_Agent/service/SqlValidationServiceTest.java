package com.guru.AI_Assisted_Database_Agent.service;

import com.guru.AI_Assisted_Database_Agent.model.DbType;
import com.guru.AI_Assisted_Database_Agent.model.SqlPlan;
import com.guru.AI_Assisted_Database_Agent.model.SchemaInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SqlValidationServiceTest {

    private final SchemaInfo schema = new SchemaInfo(List.of(new SchemaInfo.TableInfo(null, "customers", List.of(new SchemaInfo.ColumnInfo("id", "INTEGER", false, true, null), new SchemaInfo.ColumnInfo("name", "VARCHAR", true, false, null)))));
    private final SqlValidationService service = new SqlValidationService();

    @Test
    void acceptsSingleParameterizedReadForExpectedDialect() {
        SqlPlan plan = plan("SELECT", "POSTGRES", "SELECT id, name FROM customers WHERE id = :customerId", Map.of("customerId", 42));

        SqlValidationService.ValidationResult result = service.validate(plan, DbType.POSTGRES, false, schema);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void rejectsWhenDeclaredIntentDoesNotMatchParsedStatement() {
        SqlPlan plan = plan("SELECT", "POSTGRES", "DELETE FROM customers WHERE id = :customerId", Map.of("customerId", 42));

        SqlValidationService.ValidationResult result = service.validate(plan, DbType.POSTGRES, false, schema);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("declared operation type");
    }

    @Test
    void rejectsMultipleStatements() {
        SqlPlan plan = plan("SELECT", "POSTGRES", "SELECT 1; DELETE FROM customers", Map.of());

        assertThat(service.validate(plan, DbType.POSTGRES, false, schema).valid()).isFalse();
    }

    @Test
    void rejectsLockingRead() {
        SqlPlan plan = plan("SELECT", "POSTGRES", "SELECT * FROM customers FOR UPDATE", Map.of());

        SqlValidationService.ValidationResult result = service.validate(plan, DbType.POSTGRES, false, schema);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("locking");
    }

    @Test
    void rejectsMissingOrUnexpectedNamedParameters() {
        SqlPlan plan = plan("UPDATE", "POSTGRES", "UPDATE customers SET name = :name WHERE id = :id", Map.of("name", "Ada"));

        SqlValidationService.ValidationResult result = service.validate(plan, DbType.POSTGRES, false, schema);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("parameters");
    }

    @Test
    void rejectsWrongDialect() {
        SqlPlan plan = plan("SELECT", "MYSQL", "SELECT id FROM customers LIMIT 10", Map.of());

        assertThat(service.validate(plan, DbType.POSTGRES, false, schema).valid()).isFalse();
    }

    @Test
    void forcesConfirmationForEveryWriteIntent() {
        SqlPlan plan = new SqlPlan(
                "UPDATE", "POSTGRES", "UPDATE customers SET name = :name WHERE id = :id",
                Map.of("name", "Ada", "id", 42), true, false, "Update a customer"
        );

        assertThat(service.requiresConfirmation(plan)).isTrue();
    }

    private SqlPlan plan(String intent, String dialect, String sql, Map<String, Object> params) {
        return new SqlPlan(intent, dialect, sql, params, !"SELECT".equals(intent), !"SELECT".equals(intent), "test");
    }
}


