package com.heuristiq.identity.orgstructure.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.heuristiq.identity.orgstructure.domain.LadderImportModels.ExistingPosition;
import com.heuristiq.identity.orgstructure.domain.LadderImportModels.LadderReference;
import com.heuristiq.identity.orgstructure.domain.LadderImportModels.LadderRow;
import com.heuristiq.identity.orgstructure.domain.LadderImportModels.LadderRowInput;

/**
 * Checks every row of a ladders file on its own and against the rest of the file and the positions that exist now. Pure logic: it
 * reads nothing and changes nothing. Messages are stable codes (see {@link LadderErrorCode}) the UI can translate; a code with a detail
 * is written {@code CODE:detail}.
 *
 * <p>A ladder or position matches an existing one ignoring case and surrounding blanks. A rank is unique inside a ladder (the database
 * says so), so a row asking for a rank that another position of the ladder already holds is rejected instead of failing half way
 * through the apply.
 */
public final class LadderRowValidator {

    private LadderRowValidator() {
    }

    /** Machine-readable reasons a row can be rejected. */
    public enum LadderErrorCode {
        LADDER_REQUIRED, LADDER_TOO_LONG, POSITION_REQUIRED, POSITION_TOO_LONG, RANK_INVALID, MANAGERIAL_INVALID,
        DUPLICATE_ROW, RANK_DUPLICATE, RANK_TAKEN
    }

    /** How a ladder or position name is compared: ignoring case and surrounding blanks. */
    public static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    /** The rank a cell holds, or null when it is blank or not a whole number from {@link LadderImportConstants#MIN_RANK} to {@link LadderImportConstants#MAX_RANK}. */
    public static Integer parseRank(String cell) {
        if (cell == null || !LadderImportConstants.RANK.matcher(cell.trim()).matches()) {
            return null;
        }
        int rank = Integer.parseInt(cell.trim());
        return rank < LadderImportConstants.MIN_RANK || rank > LadderImportConstants.MAX_RANK ? null : rank;
    }

    public static List<LadderRow> validate(List<LadderRowInput> rows, LadderReference reference) {
        Map<String, Integer> firstRowOfPosition = new HashMap<>();
        Map<String, LadderRowInput> firstRowOfRank = new HashMap<>();
        for (LadderRowInput row : rows) {
            if (row.ladder() != null && row.position() != null) {
                firstRowOfPosition.putIfAbsent(positionKey(row), row.rowNo());
                Integer rank = parseRank(row.rank());
                if (rank != null) {
                    firstRowOfRank.putIfAbsent(key(row.ladder()) + "#" + rank, row);
                }
            }
        }
        List<LadderRow> out = new ArrayList<>(rows.size());
        for (LadderRowInput row : rows) {
            List<String> errors = new ArrayList<>();
            checkRow(row, errors);
            checkAgainstFile(row, firstRowOfPosition, firstRowOfRank, errors);
            checkAgainstExisting(row, reference, errors);
            out.add(new LadderRow(row, errors.isEmpty() ? OrgStructureConstants.IMPORT_ROW_VALID : OrgStructureConstants.IMPORT_ROW_INVALID,
                    List.copyOf(errors)));
        }
        return out;
    }

    private static String positionKey(LadderRowInput row) {
        return key(row.ladder()) + "#" + key(row.position());
    }

    private static void checkRow(LadderRowInput row, List<String> errors) {
        if (row.ladder() == null) {
            errors.add(LadderErrorCode.LADDER_REQUIRED.name());
        } else if (row.ladder().length() > OrgStructureConstants.IMPORT_MAX_NAME_LENGTH) {
            errors.add(LadderErrorCode.LADDER_TOO_LONG.name());
        }
        if (row.position() == null) {
            errors.add(LadderErrorCode.POSITION_REQUIRED.name());
        } else if (row.position().length() > OrgStructureConstants.IMPORT_MAX_NAME_LENGTH) {
            errors.add(LadderErrorCode.POSITION_TOO_LONG.name());
        }
        if (parseRank(row.rank()) == null) {
            errors.add(LadderErrorCode.RANK_INVALID.name());
        }
        if (!ImportFlags.isValid(row.managerial())) {
            errors.add(LadderErrorCode.MANAGERIAL_INVALID.name());
        }
    }

    private static void checkAgainstFile(LadderRowInput row, Map<String, Integer> firstRowOfPosition,
                                         Map<String, LadderRowInput> firstRowOfRank, List<String> errors) {
        if (row.ladder() == null || row.position() == null) {
            return;
        }
        int first = firstRowOfPosition.get(positionKey(row));
        if (first != row.rowNo()) {
            errors.add(LadderErrorCode.DUPLICATE_ROW.name() + LadderImportConstants.ERROR_DETAIL_SEPARATOR + first);
            return;
        }
        Integer rank = parseRank(row.rank());
        if (rank != null) {
            LadderRowInput holder = firstRowOfRank.get(key(row.ladder()) + "#" + rank);
            if (holder != null && holder.rowNo() != row.rowNo() && !positionKey(holder).equals(positionKey(row))) {
                errors.add(LadderErrorCode.RANK_DUPLICATE.name() + LadderImportConstants.ERROR_DETAIL_SEPARATOR + holder.rowNo());
            }
        }
    }

    private static void checkAgainstExisting(LadderRowInput row, LadderReference reference, List<String> errors) {
        Integer rank = parseRank(row.rank());
        if (row.ladder() == null || row.position() == null || rank == null) {
            return;
        }
        Map<String, ExistingPosition> positions = reference.positionsByLadder().get(key(row.ladder()));
        if (positions == null) {
            return;
        }
        for (Map.Entry<String, ExistingPosition> entry : positions.entrySet()) {
            if (entry.getValue().rank() == rank && !entry.getKey().equals(key(row.position()))) {
                errors.add(LadderErrorCode.RANK_TAKEN.name() + LadderImportConstants.ERROR_DETAIL_SEPARATOR + entry.getValue().name());
                return;
            }
        }
    }
}
