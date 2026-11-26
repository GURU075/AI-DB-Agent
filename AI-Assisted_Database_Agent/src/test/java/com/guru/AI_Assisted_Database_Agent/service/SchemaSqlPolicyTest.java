package com.guru.AI_Assisted_Database_Agent.service;

import com.guru.AI_Assisted_Database_Agent.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class SchemaSqlPolicyTest {
    private final SqlValidationService service = new SqlValidationService();
    private final SchemaInfo schema = new SchemaInfo(List.of(
            new SchemaInfo.TableInfo("public", "customers", List.of(column("id"), column("name"))),
            new SchemaInfo.TableInfo("public", "orders", List.of(column("id"), column("customer_id"), column("total")))));

    private static SchemaInfo.ColumnInfo column(String name) {
        return new SchemaInfo.ColumnInfo(name, "VARCHAR", true, false, null);
    }

    private SqlValidationService.ValidationResult validate(String sql, String intent, Map<String, Object> params) {
        return service.validate(new SqlPlan(intent, "POSTGRES", sql, params, false, false, "test"), DbType.POSTGRES, false, schema);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT id, name FROM public.customers LIMIT 100",
            "SELECT c.id, c.name FROM public.customers AS c WHERE c.id = 42",
            "SELECT c.id, o.total FROM public.customers c JOIN public.orders o ON c.id = o.customer_id",
            "SELECT c.id, o.total FROM public.customers c LEFT JOIN public.orders o ON c.id = o.customer_id",
            "SELECT customer_id, SUM(total) FROM public.orders GROUP BY customer_id HAVING SUM(total) > 10 ORDER BY customer_id DESC LIMIT 5 OFFSET 2",
            "SELECT COUNT(*) FROM public.customers",
            "SELECT COUNT(DISTINCT name) FROM public.customers",
            "SELECT LOWER(name) AS lower_name FROM public.customers",
            "SELECT id FROM public.customers WHERE id IN (1, 2) AND (id > 0 OR name IS NULL)",
            "SELECT id FROM public.customers WHERE id BETWEEN 1 AND 5",
            "SELECT \"id\" FROM \"public\".\"customers\""
    })
    void acceptsSupportedReads(String sql) {
        var result = validate(sql, "SELECT", Map.of());
        assertThat(result.valid()).as("%s: %s", sql, result.reason()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT password FROM public.customers", "SELECT id FROM private.customers",
            "SELECT id FROM public.secrets", "SELECT id FROM customers",
            "SELECT o.name FROM public.customers c JOIN public.orders o ON c.id = o.customer_id",
            "SELECT id FROM public.customers c JOIN public.orders o ON c.id = o.customer_id",
            "SELECT c.id FROM public.customers c WHERE c.secret = 1",
            "SELECT id FROM public.customers ORDER BY secret",
            "SELECT id FROM public.customers GROUP BY secret",
            "SELECT id FROM public.customers HAVING secret > 1",
            "SELECT c.id FROM public.customers c JOIN public.orders o ON c.secret = o.id",
            "SELECT * FROM public.customers", "SELECT c.* FROM public.customers c",
            "SELECT pg_read_file(:path) FROM public.customers",
            "SELECT public.lower(name) FROM public.customers", "SELECT nextval(:path) FROM public.customers",
            "SELECT id FROM public.customers ORDER BY pg_sleep(10)",
            "SELECT id FROM public.customers WHERE id IN (SELECT id FROM private.customers)",
            "WITH x AS (SELECT id FROM public.customers) SELECT id FROM x",
            "SELECT id FROM public.customers UNION SELECT id FROM private.customers",
            "SELECT id INTO public.orders FROM public.customers",
            "SELECT id FROM public.customers FOR UPDATE",
            "SELECT id FROM public.customers; DELETE FROM public.customers",
            "SELECT id FROM public.customers /* comment */", "SELECT id FROM public.customers -- comment",
            "SELECT id FROM public.customers /*!50000 INTO OUTFILE '/tmp/out' */",
            "SELECT id FROM public.customers WHERE name = 'Ada'",
            "SELECT c.id FROM public.customers AS c(secret)",
            "SELECT secret.id FROM public.customers c",
            "SELECT COUNT(* EXCEPT (name)) FROM public.customers"
    })
    void rejectsUnauthorizedOrUnverifiedReads(String sql) {
        Map<String, Object> params = sql.contains(":path") ? Map.of("path", "/etc/passwd") : Map.of();
        assertThat(validate(sql, "SELECT", params).valid()).as(sql).isFalse();
    }

    @Test
    void validatesWriteTargetsAndRequiresConfirmation() {
        for (String sql : List.of("UPDATE public.customers SET name = :name WHERE id = :id",
                "INSERT INTO public.customers (id, name) VALUES (:id, :name)")) {
            String intent = sql.startsWith("UPDATE") ? "UPDATE" : "INSERT";
            var result = validate(sql, intent, Map.of("name", "Ada", "id", 42));
            assertThat(result.valid()).as(result.reason()).isTrue();
            assertThat(service.requiresConfirmation(new SqlPlan(intent, "POSTGRES", sql, Map.of(), false, false, ""))).isTrue();
        }
        assertThat(validate("DELETE FROM public.customers WHERE id = :id", "DELETE", Map.of("id", 42)).valid()).isTrue();
        assertThat(validate("UPDATE public.customers SET secret = :value", "UPDATE", Map.of("value", "x")).valid()).isFalse();
        assertThat(validate("INSERT INTO public.customers (secret) VALUES (:value)", "INSERT", Map.of("value", "x")).valid()).isFalse();
        assertThat(validate("DELETE FROM private.customers", "DELETE", Map.of()).valid()).isFalse();
        assertThat(validate("UPDATE public.customers SET name = :value RETURNING secret", "UPDATE", Map.of("value", "x")).valid()).isFalse();
    }

    @Test
    void rejectsMissingMetadataAndDoesNotInterpretBoundValuesAsSql() {
        var plan = new SqlPlan("SELECT", "POSTGRES", "SELECT id FROM public.customers WHERE name = :name",
                Map.of("name", "'; DROP TABLE customers; --"), false, false, "");
        assertThat(service.validate(plan, DbType.POSTGRES, false, schema).valid()).isTrue();
        assertThat(service.validate(plan, DbType.POSTGRES, false, null).valid()).isFalse();
        assertThat(service.validate(plan, DbType.POSTGRES, false, new SchemaInfo(List.of())).valid()).isFalse();
    }

    @Test
    void handlesDialectIdentifierCaseAndOraclePagination() {
        SchemaInfo oracle = new SchemaInfo(List.of(new SchemaInfo.TableInfo("APP", "CUSTOMERS", List.of(column("ID")))));
        var plan = new SqlPlan("SELECT", "ORACLE", "SELECT id FROM app.customers FETCH FIRST 10 ROWS ONLY", Map.of(), false, false, "");
        var result = service.validate(plan, DbType.ORACLE, false, oracle);
        assertThat(result.valid()).as(result.reason()).isTrue();
        var mysql = new SqlPlan("SELECT", "MYSQL", "SELECT `id` FROM `public`.`customers` LIMIT 10", Map.of(), false, false, "");
        assertThat(service.validate(mysql, DbType.MYSQL, false, schema).valid()).isTrue();
    }
}
