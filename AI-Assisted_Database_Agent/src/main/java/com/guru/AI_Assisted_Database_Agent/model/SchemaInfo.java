package com.guru.AI_Assisted_Database_Agent.model;

import java.util.List;

public record SchemaInfo(List<TableInfo> tables) {

    public SchemaInfo {
        tables = tables == null ? List.of() : List.copyOf(tables);
    }

    public record TableInfo(String schema, String name, List<ColumnInfo> columns) {
        public TableInfo {
            columns = columns == null ? List.of() : List.copyOf(columns);
        }
    }

    public record ColumnInfo(
            String name,
            String dataType,
            boolean nullable,
            boolean primaryKey,
            String foreignKeyReference
    ) {}
}

