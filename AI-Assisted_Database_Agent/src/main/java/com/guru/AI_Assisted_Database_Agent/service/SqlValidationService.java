package com.guru.AI_Assisted_Database_Agent.service;

import com.guru.AI_Assisted_Database_Agent.model.DbType;
import com.guru.AI_Assisted_Database_Agent.model.SqlPlan;
import com.guru.AI_Assisted_Database_Agent.model.SchemaInfo;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses and validates every LLM-produced statement before execution. */
@Service
public class SqlValidationService {

    private static final Set<String> WRITE_INTENTS = Set.of("INSERT", "UPDATE", "DELETE");
    private static final int MAX_SQL_LENGTH = 20_000;
    private static final Pattern NAMED_PARAMETER = Pattern.compile("(?<!:):([A-Za-z][A-Za-z0-9_]*)");
    private static final Pattern SELECT_SIDE_EFFECT = Pattern.compile(
            "(?is)\\b(INSERT|UPDATE|DELETE|MERGE|CALL|EXEC(?:UTE)?|COPY|LOAD|INTO\\s+OUTFILE|FOR\\s+UPDATE|FOR\\s+SHARE)\\b"
    );

    public record ValidationResult(boolean valid, String reason) {
        static ValidationResult allow() { return new ValidationResult(true, null); }
        static ValidationResult reject(String reason) { return new ValidationResult(false, reason); }
    }

    public ValidationResult validate(SqlPlan plan, DbType expectedDialect, boolean adminMode, SchemaInfo schema) {
        if (plan == null) return ValidationResult.reject("The AI did not return a SQL plan.");

        String intent = normalize(plan.intent());
        if ("NONE".equals(intent) || "DDL_BLOCKED".equals(intent)) {
            return ValidationResult.reject(safeReason(plan.reason(), "The requested operation is not supported."));
        }
        if ("DDL".equals(intent)) {
            return ValidationResult.reject(adminMode
                    ? "DDL execution is not implemented by the current safety policy."
                    : "DDL operations are disabled.");
        }
        if (!"SELECT".equals(intent) && !WRITE_INTENTS.contains(intent)) {
            return ValidationResult.reject("Unsupported SQL intent: " + intent);
        }
        if (plan.dialect() == null || !expectedDialect.name().equalsIgnoreCase(plan.dialect())) {
            return ValidationResult.reject("The generated SQL dialect does not match the connected database.");
        }

        String sql = plan.sql();
        if (sql == null || sql.isBlank()) return ValidationResult.reject("The AI returned an empty SQL statement.");
        if (sql.length() > MAX_SQL_LENGTH) return ValidationResult.reject("The generated SQL statement is too large.");
        // Reject comments before parsing: MySQL executable comments must not be discarded as harmless text.
        String outsideQuotes = sql.replaceAll("'(?:''|[^'])*'", "''").replaceAll("\"(?:\"\"|[^\"])*\"", "\"\"");
        if (outsideQuotes.contains("--") || outsideQuotes.contains("/*") || outsideQuotes.contains("#")) {
            return ValidationResult.reject("SQL comments are not allowed.");
        }

        Statement statement;
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(sql);
            if (statements.size() != 1) {
                return ValidationResult.reject("Exactly one SQL statement is allowed.");
            }
            statement = statements.get(0);
        } catch (JSQLParserException exception) {
            return ValidationResult.reject("The generated SQL could not be parsed safely.");
        }

        String actualIntent = classify(statement);
        if (actualIntent == null) {
            return ValidationResult.reject("This SQL statement type is not allowed.");
        }
        if (!intent.equals(actualIntent)) {
            return ValidationResult.reject("The generated SQL does not match its declared operation type.");
        }
        if ("SELECT".equals(intent) && SELECT_SIDE_EFFECT.matcher(stripQuotedText(sql)).find()) {
            return ValidationResult.reject("Read queries may not contain write, locking, file, or procedure operations.");
        }

        ValidationResult parameters = validateParameters(sql, plan.params());
        if (!parameters.valid()) return parameters;

        try {
            new SchemaSqlPolicy(schema, expectedDialect).validate(statement);
        } catch (IllegalArgumentException exception) {
            return ValidationResult.reject(exception.getMessage());
        } catch (RuntimeException exception) {
            return ValidationResult.reject("The SQL could not be verified safely.");
        }

        return ValidationResult.allow();
    }

    public boolean requiresConfirmation(SqlPlan plan) {
        return plan != null && (plan.requiresConfirmation() || WRITE_INTENTS.contains(normalize(plan.intent())));
    }

    private ValidationResult validateParameters(String sql, Map<String, Object> params) {
        Set<String> placeholders = new HashSet<>();
        Matcher matcher = NAMED_PARAMETER.matcher(stripQuotedText(sql));
        while (matcher.find()) placeholders.add(matcher.group(1));

        Set<String> supplied = params == null ? Set.of() : params.keySet();
        if (!supplied.equals(placeholders)) {
            return ValidationResult.reject("SQL named parameters do not match the supplied parameter values.");
        }
        return ValidationResult.allow();
    }

    private String classify(Statement statement) {
        if (statement instanceof Select) return "SELECT";
        if (statement instanceof Insert) return "INSERT";
        if (statement instanceof Update) return "UPDATE";
        if (statement instanceof Delete) return "DELETE";
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String safeReason(String reason, String fallback) {
        return reason == null || reason.isBlank() ? fallback : reason;
    }

    private String stripQuotedText(String sql) {
        return sql
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)--.*$", " ")
                .replaceAll("'(?:''|[^'])*'", "''")
                .replaceAll("\"(?:\"\"|[^\"])*\"", "\"\"");
    }
}
