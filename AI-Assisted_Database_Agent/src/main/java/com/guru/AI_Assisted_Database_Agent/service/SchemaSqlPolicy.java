package com.guru.AI_Assisted_Database_Agent.service;

import com.guru.AI_Assisted_Database_Agent.model.DbType;
import com.guru.AI_Assisted_Database_Agent.model.SchemaInfo;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.update.Update;

import java.util.*;
import java.util.stream.Collectors;

/** Fail-closed SQL subset. Reparse a reconstruction to detect any unexamined clauses. */
final class SchemaSqlPolicy {
    private static final Set<String> FUNCTIONS = Set.of(
            "COUNT", "SUM", "AVG", "MIN", "MAX", "LOWER", "UPPER", "LENGTH", "ABS", "ROUND", "COALESCE", "NULLIF");
    private static final Set<String> BINARY = Set.of(
            "Addition", "Subtraction", "Multiplication", "Division", "Modulo", "EqualsTo", "NotEqualsTo",
            "GreaterThan", "GreaterThanEquals", "MinorThan", "MinorThanEquals", "AndExpression", "OrExpression", "LikeExpression");
    private final SchemaInfo schema;
    private final DbType dialect;
    private final List<Binding> bindings = new ArrayList<>();

    SchemaSqlPolicy(SchemaInfo schema, DbType dialect) {
        this.schema = schema;
        this.dialect = dialect;
    }

    void validate(Statement statement) {
        require(schema != null && !schema.tables().isEmpty(), "Schema metadata is unavailable. Reconnect before querying.");
        String checked;
        if (statement instanceof PlainSelect select) {
            checked = select(select);
        } else if (statement instanceof Update update) {
            String target = table(update.getTable());
            checked = "UPDATE " + target + " SET " + update.getUpdateSets().stream().map(set -> {
                require(set.getColumns().size() == 1 && set.getValues().size() == 1, "Use individual column assignments.");
                return expression(set.getColumns().get(0)) + " = " + expression(set.getValues().get(0));
            }).collect(Collectors.joining(", ")) + where(update.getWhere());
        } else if (statement instanceof Delete delete) {
            checked = "DELETE FROM " + table(delete.getTable()) + where(delete.getWhere());
        } else if (statement instanceof Insert insert) {
            String target = table(insert.getTable());
            require(insert.getColumns() != null && !insert.getColumns().isEmpty(), "INSERT requires explicit columns.");
            require(insert.getSelect() instanceof Values, "Only INSERT with VALUES is supported by the schema policy.");
            checked = "INSERT INTO " + target + " (" + expressions(insert.getColumns()) + ") VALUES "
                    + expression(((Values) insert.getSelect()).getExpressions());
        } else {
            throw rejected("This query structure is not supported by the schema policy.");
        }
        try {
            // An extra clause, hint, expression modifier, CTE, or dialect extension must not slip past validation.
            require(CCJSqlParserUtil.parse(checked).toString().equals(statement.toString()),
                    "The SQL contains clauses or expressions outside the supported safety policy.");
        } catch (net.sf.jsqlparser.JSQLParserException exception) {
            throw rejected("The SQL could not be verified against the schema safely.");
        }
    }

    private String select(PlainSelect select) {
        require(select.getFromItem() instanceof Table, "SELECT requires a schema table; derived tables are not supported.");
        String from = table((Table) select.getFromItem());
        StringBuilder joins = new StringBuilder();
        if (select.getJoins() != null) {
            for (Join join : select.getJoins()) {
                require(join.getRightItem() instanceof Table, "Only joins between schema tables are supported.");
                String right = table((Table) join.getRightItem());
                require(!join.isNatural() && (join.getUsingColumns() == null || join.getUsingColumns().isEmpty()),
                        "Use an explicit JOIN ON condition.");
                String kind = join.isLeft() ? "LEFT " : join.isRight() ? "RIGHT " : join.isFull() ? "FULL "
                        : join.isCross() ? "CROSS " : join.isInner() ? "INNER " : "";
                joins.append(join.isSimple() ? ", " : " " + kind + (join.isOuter() ? "OUTER " : "") + "JOIN ").append(right);
                if (join.getOnExpressions() != null) {
                    for (Expression on : join.getOnExpressions()) joins.append(" ON ").append(expression(on));
                }
            }
        }
        String items = select.getSelectItems().stream().map(item -> expression(item.getExpression())
                + alias(item.getAlias())).collect(Collectors.joining(", "));
        StringBuilder sql = new StringBuilder("SELECT ");
        if (select.getDistinct() != null) sql.append("DISTINCT ");
        sql.append(items).append(" FROM ").append(from).append(joins).append(where(select.getWhere()));
        if (select.getGroupBy() != null) sql.append(" GROUP BY ").append(expressions(select.getGroupBy().getGroupByExpressionList()));
        if (select.getHaving() != null) sql.append(" HAVING ").append(expression(select.getHaving()));
        if (select.getOrderByElements() != null) {
            sql.append(" ORDER BY ").append(select.getOrderByElements().stream().map(order ->
                    expression(order.getExpression()) + (order.isAscDescPresent() ? (order.isAsc() ? " ASC" : " DESC") : ""))
                    .collect(Collectors.joining(", ")));
        }
        if (select.getLimit() != null) {
            require(select.getLimit().getOffset() == null, "Use a separate OFFSET clause.");
            sql.append(" LIMIT ").append(expression(select.getLimit().getRowCount()));
        }
        if (select.getOffset() != null) {
            // Only numeric pagination is accepted; the round-trip also verifies vendor suffixes.
            String offset = select.getOffset().toString();
            require(offset.matches("(?i)\\s*OFFSET \\d+( ROWS?)?"), "Use a numeric OFFSET.");
            sql.append(" ").append(offset.trim());
        }
        if (select.getFetch() != null) {
            String fetch = select.getFetch().toString();
            require(fetch.matches("(?i)\\s*FETCH (FIRST|NEXT) \\d+ ROWS? ONLY"), "Use a numeric FETCH row limit.");
            sql.append(" ").append(fetch.trim());
        }
        return sql.toString();
    }

    private String table(Table table) {
        require(table != null && table.getNameParts().size() <= 2, "Cross-catalog references and database links are forbidden.");
        String name = identifier(table.getName());
        String namespace = table.getSchemaName() == null ? null : identifier(table.getSchemaName());
        List<SchemaInfo.TableInfo> matches = schema.tables().stream()
                .filter(t -> t.name().equals(name) && (namespace == null || namespace.equals(t.schema()))).toList();
        require(matches.size() == 1, "A referenced table is outside the available schema or is ambiguous.");
        SchemaInfo.TableInfo match = matches.get(0);
        require(namespace != null || match.schema() == null || match.schema().isBlank(),
                "Qualify table names with their schema to avoid search-path ambiguity.");
        String aliasName = table.getAlias() == null ? name : identifier(table.getAlias().getName());
        require(bindings.stream().noneMatch(b -> b.alias().equals(aliasName)), "Table aliases must be unique.");
        bindings.add(new Binding(aliasName, match, table.getAlias() != null));
        return (table.getSchemaName() == null ? "" : table.getSchemaName() + ".") + table.getName() + alias(table.getAlias());
    }

    private String alias(Alias alias) {
        if (alias == null) return "";
        identifier(alias.getName());
        return (alias.isUseAs() ? " AS " : " ") + alias.getName();
    }

    private String expression(Expression value) {
        require(value != null, "Missing SQL expression.");
        if (value instanceof Column column) {
            String name = identifier(column.getColumnName());
            Table qualifier = column.getTable();
            List<Binding> candidates = bindings;
            if (qualifier != null && qualifier.getName() != null) {
                require(qualifier.getNameParts().size() <= 2, "Cross-catalog column references are forbidden.");
                String owner = identifier(qualifier.getName());
                String namespace = qualifier.getSchemaName() == null ? null : identifier(qualifier.getSchemaName());
                candidates = bindings.stream().filter(b -> b.alias().equals(owner)
                        && (namespace == null || (!b.aliased() && namespace.equals(b.table().schema())))).toList();
            }
            long matches = candidates.stream().filter(b -> b.table().columns().stream().anyMatch(c -> c.name().equals(name))).count();
            require(matches == 1, "A referenced column is outside the available schema or is ambiguous.");
            return column.toString();
        }
        if (value instanceof JdbcNamedParameter parameter) {
            require(parameter.getName().matches("[A-Za-z][A-Za-z0-9_]*"), "Invalid named parameter.");
            return ":" + parameter.getName();
        }
        if (value instanceof LongValue || value instanceof DoubleValue || value instanceof NullValue || value instanceof BooleanValue)
            return value.toString();
        if (value instanceof BinaryExpression binary && BINARY.contains(value.getClass().getSimpleName()))
            return expression(binary.getLeftExpression()) + " " + binary.getStringExpression() + " " + expression(binary.getRightExpression());
        if (value instanceof SignedExpression signed) return signed.getSign() + expression(signed.getExpression());
        if (value instanceof NotExpression not) return "NOT " + expression(not.getExpression());
        if (value instanceof IsNullExpression isNull) return expression(isNull.getLeftExpression()) + (isNull.isNot() ? " IS NOT NULL" : " IS NULL");
        if (value instanceof Between between) return expression(between.getLeftExpression()) + (between.isNot() ? " NOT BETWEEN " : " BETWEEN ")
                + expression(between.getBetweenExpressionStart()) + " AND " + expression(between.getBetweenExpressionEnd());
        if (value instanceof InExpression in) return expression(in.getLeftExpression()) + (in.isNot() ? " NOT IN " : " IN ") + expression(in.getRightExpression());
        if (value instanceof Function function) {
            String name = function.getName();
            require(name != null && FUNCTIONS.contains(name.toUpperCase(Locale.ROOT)), "This SQL function is not allowed.");
            String args = function.getParameters() == null ? "" : function.getParameters().stream().map(arg ->
                    arg instanceof AllColumns && "COUNT".equalsIgnoreCase(name) ? "*" : expression(arg)).collect(Collectors.joining(", "));
            return name + "(" + (function.isDistinct() ? "DISTINCT " : "") + args + ")";
        }
        if (value instanceof ExpressionList<?> list) {
            String contents = expressions(list);
            return value instanceof ParenthesedExpressionList<?> ? "(" + contents + ")" : contents;
        }
        throw rejected("Unsupported SQL expression. Use explicit columns, named parameters, and approved functions; subqueries are not supported.");
    }

    private String expressions(Collection<? extends Expression> values) {
        require(values != null, "Missing SQL expressions.");
        return values.stream().map(this::expression).collect(Collectors.joining(", "));
    }

    private String where(Expression where) { return where == null ? "" : " WHERE " + expression(where); }

    private String identifier(String value) {
        require(value != null, "Missing SQL identifier.");
        boolean quoted = value.startsWith("\"") && value.endsWith("\"");
        boolean backtick = value.startsWith("`") && value.endsWith("`") && dialect == DbType.MYSQL;
        if (quoted || backtick) {
            String raw = value.substring(1, value.length() - 1);
            require(raw.matches("[A-Za-z_][A-Za-z0-9_$]*"), "Complex identifiers are not supported by the safety policy.");
            return raw;
        }
        require(value.matches("[A-Za-z_][A-Za-z0-9_$]*"), "Invalid SQL identifier.");
        return dialect == DbType.ORACLE ? value.toUpperCase(Locale.ROOT)
                : dialect == DbType.POSTGRES ? value.toLowerCase(Locale.ROOT) : value;
    }

    private static void require(boolean condition, String message) { if (!condition) throw rejected(message); }
    private static IllegalArgumentException rejected(String message) { return new IllegalArgumentException(message); }
    private record Binding(String alias, SchemaInfo.TableInfo table, boolean aliased) {}
}
