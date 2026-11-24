package com.heuristiq.uam.orgstructure.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Value types of the bulk ladders and positions import: one row of the file is one job title of one ladder. */
public final class LadderImportModels {
    private LadderImportModels() {
    }

    /** One data row of the uploaded file, exactly as read (blank cells are null; rank and managerial are the cells as typed). */
    public record LadderRowInput(int rowNo, String ladder, String position, String rank, String managerial) {
    }

    /** A staged row with the outcome of validating it; {@code errors} is empty for a valid row. */
    public record LadderRow(LadderRowInput input, String status, List<String> errors) {
        public boolean valid() {
            return errors.isEmpty();
        }
    }

    /** A staged row as stored, used while applying a confirmed batch. */
    public record StagedLadderRow(LadderRowInput input, String status) {
    }

    /** A position of a ladder as it is now. */
    public record ExistingPosition(String id, String name, int rank, boolean managerial) {
    }

    /**
     * What the file is checked against: for each ladder of the organization (key: its name without surrounding blanks, lower case, see
     * {@link LadderRowValidator#key}) its positions by the same kind of key. A ladder without positions has an empty map.
     */
    public record LadderReference(Map<String, Map<String, ExistingPosition>> positionsByLadder) {
    }

    /** A ladders import batch: the other imports' batch fields plus how many new ladders it created. */
    public record LadderBatch(String id, String status, String documentId, String fileName, int totalRows, int validRows, int errorRows,
                              int createdCount, int updatedCount, int laddersCreated, String message, String createdBy,
                              Instant createdAt, Instant appliedAt) {
    }

    /** The rows of an uploaded ladders file, as read. */
    public record LadderFile(String documentId, String fileName, List<LadderRowInput> rows) {
    }
}
