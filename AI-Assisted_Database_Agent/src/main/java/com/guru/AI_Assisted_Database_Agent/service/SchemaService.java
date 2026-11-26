package com.guru.AI_Assisted_Database_Agent.service;

import com.guru.AI_Assisted_Database_Agent.model.SchemaInfo;
import com.guru.AI_Assisted_Database_Agent.model.SchemaSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Converts live JDBC metadata into API-safe schema data and a bounded LLM summary. */
@Service
public class SchemaService {

    private static final Logger log = LoggerFactory.getLogger(SchemaService.class);
    private static final int MAX_TABLES = 200;
    private static final int MAX_COLUMNS_PER_TABLE = 200;

    public SchemaSnapshot introspect(DataSource dataSource, String schemaFilter) {
        List<SchemaInfo.TableInfo> tableInfos = new ArrayList<>();
        StringBuilder prompt = new StringBuilder("tables:\n");
        String normalizedFilter = normalize(schemaFilter);

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            List<TableRef> tables = readTables(metadata, connection.getCatalog(), normalizedFilter);

            for (TableRef table : tables) {
                Set<String> primaryKeys = readPrimaryKeys(metadata, table);
                Map<String, String> foreignKeys = readForeignKeys(metadata, table);
                List<SchemaInfo.ColumnInfo> columns = readColumns(metadata, table, primaryKeys, foreignKeys);

                tableInfos.add(new SchemaInfo.TableInfo(table.schema(), table.name(), columns));
                appendPromptTable(prompt, table, columns);
            }
        } catch (Exception exception) {
            log.warn("Schema introspection failed: {}", exception.getClass().getSimpleName());
            throw new SchemaIntrospectionException("Unable to inspect the selected database schema.", exception);
        }

        return new SchemaSnapshot(new SchemaInfo(tableInfos), prompt.toString());
    }

    public String buildSchemaSummary(DataSource dataSource, String schemaFilter) {
        return introspect(dataSource, schemaFilter).promptSummary();
    }

    private List<TableRef> readTables(DatabaseMetaData metadata, String catalog, String schemaFilter) throws Exception {
        List<TableRef> tables = new ArrayList<>();
        try (ResultSet resultSet = metadata.getTables(catalog, schemaFilter, "%", new String[]{"TABLE"})) {
            while (resultSet.next() && tables.size() < MAX_TABLES) {
                tables.add(new TableRef(
                        catalog,
                        resultSet.getString("TABLE_SCHEM"),
                        resultSet.getString("TABLE_NAME")
                ));
            }
        }
        return tables;
    }

    private Set<String> readPrimaryKeys(DatabaseMetaData metadata, TableRef table) throws Exception {
        Set<String> primaryKeys = new LinkedHashSet<>();
        try (ResultSet resultSet = metadata.getPrimaryKeys(table.catalog(), table.schema(), table.name())) {
            while (resultSet.next()) {
                primaryKeys.add(resultSet.getString("COLUMN_NAME"));
            }
        }
        return primaryKeys;
    }

    private Map<String, String> readForeignKeys(DatabaseMetaData metadata, TableRef table) throws Exception {
        Map<String, String> foreignKeys = new LinkedHashMap<>();
        try (ResultSet resultSet = metadata.getImportedKeys(table.catalog(), table.schema(), table.name())) {
            while (resultSet.next()) {
                String foreignColumn = resultSet.getString("FKCOLUMN_NAME");
                String targetSchema = resultSet.getString("PKTABLE_SCHEM");
                String targetTable = resultSet.getString("PKTABLE_NAME");
                String targetColumn = resultSet.getString("PKCOLUMN_NAME");
                String qualifiedTarget = (targetSchema == null || targetSchema.isBlank() ? "" : targetSchema + ".")
                        + targetTable + "." + targetColumn;
                foreignKeys.put(foreignColumn, qualifiedTarget);
            }
        }
        return foreignKeys;
    }

    private List<SchemaInfo.ColumnInfo> readColumns(
            DatabaseMetaData metadata,
            TableRef table,
            Set<String> primaryKeys,
            Map<String, String> foreignKeys
    ) throws Exception {
        List<SchemaInfo.ColumnInfo> columns = new ArrayList<>();
        try (ResultSet resultSet = metadata.getColumns(table.catalog(), table.schema(), table.name(), "%")) {
            while (resultSet.next() && columns.size() < MAX_COLUMNS_PER_TABLE) {
                String name = resultSet.getString("COLUMN_NAME");
                columns.add(new SchemaInfo.ColumnInfo(
                        name,
                        resultSet.getString("TYPE_NAME"),
                        resultSet.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                        primaryKeys.contains(name),
                        foreignKeys.get(name)
                ));
            }
        }
        return columns;
    }

    private void appendPromptTable(
            StringBuilder prompt,
            TableRef table,
            List<SchemaInfo.ColumnInfo> columns
    ) {
        String qualifiedName = table.schema() == null || table.schema().isBlank()
                ? table.name()
                : table.schema() + "." + table.name();
        prompt.append("- ").append(qualifiedName).append("(");
        List<String> definitions = columns.stream().map(column -> {
            StringBuilder value = new StringBuilder(column.name()).append(" ").append(column.dataType());
            if (column.primaryKey()) value.append(" PK");
            if (column.foreignKeyReference() != null) value.append(" FK->").append(column.foreignKeyReference());
            return value.toString();
        }).toList();
        prompt.append(String.join(", ", definitions)).append(")\n");
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record TableRef(String catalog, String schema, String name) {}

    public static class SchemaIntrospectionException extends RuntimeException {
        public SchemaIntrospectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
