package com.heuristiq.documents.document.persistence.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.Column;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * DMS keeps its schema with hbm2ddl "update", which adds new columns to existing tables with ALTER TABLE ... ADD COLUMN.
 * Postgres refuses a NOT NULL column without a default on a table that has rows, hbm2ddl only logs that, and every later read
 * fails ("column validuntilconfirmed does not exist"). So every primitive (NOT NULL) column added after the first release must
 * carry a database default.
 */
class DocumentMetadataJpaEntitySchemaTest {

    /** Primitive columns that were in the table from its first release, so created with it, never added later. */
    private static final List<String> ORIGINAL_PRIMITIVES = List.of("sizeBytes", "legalHold", "deleted");

    @Test
    void validUntilConfirmedHasADefaultSoItCanBeAddedToAnExistingTable() throws NoSuchFieldException {
        Column column = DocumentMetadataJpaEntity.class.getDeclaredField("validUntilConfirmed").getAnnotation(Column.class);

        assertNotNull(column);
        assertEquals("boolean not null default false", column.columnDefinition());
    }

    @Test
    void everyLaterPrimitiveColumnHasADatabaseDefault() {
        List<String> missing = new ArrayList<>();
        for (Field field : DocumentMetadataJpaEntity.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || !field.getType().isPrimitive() || ORIGINAL_PRIMITIVES.contains(field.getName())) {
                continue;
            }
            Column column = field.getAnnotation(Column.class);
            if (column == null || !column.columnDefinition().toLowerCase().contains("default")) {
                missing.add(field.getName());
            }
        }
        assertTrue(missing.isEmpty(), "Primitive columns without a database default: " + missing);
    }
}
