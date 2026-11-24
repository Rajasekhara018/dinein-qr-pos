package com.heuristiq.uam.orgstructure.domain;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.heuristiq.uam.orgstructure.domain.ImportModels.ImportReference;
import com.heuristiq.uam.orgstructure.domain.ImportModels.ImportRow;
import com.heuristiq.uam.orgstructure.domain.ImportModels.ImportRowInput;
import com.heuristiq.uam.orgstructure.domain.ImportModels.UnitRef;

/**
 * Checks every row of an import file against the organization as it is now and against the rest of
 * the file. Pure logic: it reads nothing and changes nothing, so the whole file can be reported on
 * before anything is applied. Messages are stable codes (see {@link ImportErrorCode}) the UI can translate.
 */
public final class ImportRowValidator {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private ImportRowValidator() {
    }

    /** Machine-readable reasons a row can be rejected. */
    public enum ImportErrorCode {
        EMPLOYEE_CODE_REQUIRED, EMPLOYEE_CODE_TOO_LONG, EMPLOYEE_CODE_DUPLICATE,
        FULL_NAME_REQUIRED, FULL_NAME_TOO_LONG, EMAIL_INVALID, JOINED_ON_INVALID,
        DEPARTMENT_REQUIRED, DEPARTMENT_UNKNOWN, DEPARTMENT_HAS_NO_LADDER,
        POSITION_REQUIRED, POSITION_NOT_IN_LADDER,
        MANAGER_UNKNOWN, MANAGER_IS_SELF, MANAGER_CYCLE,
        IS_HEAD_INVALID, HEAD_DUPLICATE
    }

    public static List<ImportRow> validate(List<ImportRowInput> rows, ImportReference reference) {
        Map<String, Integer> firstRowOfCode = new HashMap<>();
        Set<String> codesInFile = new HashSet<>();
        for (ImportRowInput row : rows) {
            if (row.employeeCode() != null) {
                firstRowOfCode.putIfAbsent(row.employeeCode(), row.rowNo());
                codesInFile.add(row.employeeCode());
            }
        }
        Map<String, String> managerAfterImport = new HashMap<>(reference.existingManagerByCode());
        rows.stream().filter(r -> r.employeeCode() != null && r.managerCode() != null)
                .forEach(r -> managerAfterImport.put(r.employeeCode(), r.managerCode()));

        Map<String, Integer> firstHeadRowOfDepartment = new HashMap<>();
        for (ImportRowInput row : rows) {
            if (row.departmentCode() != null && ImportFlags.isValid(row.isHead()) && ImportFlags.isYes(row.isHead())) {
                firstHeadRowOfDepartment.putIfAbsent(row.departmentCode(), row.rowNo());
            }
        }

        List<ImportRow> out = new ArrayList<>(rows.size());
        for (ImportRowInput row : rows) {
            List<String> errors = new ArrayList<>();
            checkEmployee(row, firstRowOfCode, errors);
            checkPlacement(row, reference, errors);
            checkManager(row, reference, codesInFile, managerAfterImport, errors);
            checkHead(row, firstHeadRowOfDepartment, errors);
            out.add(new ImportRow(row, errors.isEmpty() ? OrgStructureConstants.IMPORT_ROW_VALID
                    : OrgStructureConstants.IMPORT_ROW_INVALID, List.copyOf(errors)));
        }
        return out;
    }

    private static void checkHead(ImportRowInput row, Map<String, Integer> firstHeadRowOfDepartment, List<String> errors) {
        if (!ImportFlags.isValid(row.isHead())) {
            errors.add(ImportErrorCode.IS_HEAD_INVALID.name());
            return;
        }
        if (ImportFlags.isYes(row.isHead()) && row.departmentCode() != null
                && firstHeadRowOfDepartment.get(row.departmentCode()) != row.rowNo()) {
            errors.add(ImportErrorCode.HEAD_DUPLICATE.name() + ":" + row.departmentCode() + ":"
                    + firstHeadRowOfDepartment.get(row.departmentCode()));
        }
    }

    private static void checkEmployee(ImportRowInput row, Map<String, Integer> firstRowOfCode, List<String> errors) {
        if (row.employeeCode() == null) {
            errors.add(ImportErrorCode.EMPLOYEE_CODE_REQUIRED.name());
        } else {
            if (row.employeeCode().length() > OrgStructureConstants.IMPORT_MAX_CODE_LENGTH) {
                errors.add(ImportErrorCode.EMPLOYEE_CODE_TOO_LONG.name());
            }
            if (firstRowOfCode.get(row.employeeCode()) != row.rowNo()) {
                errors.add(ImportErrorCode.EMPLOYEE_CODE_DUPLICATE.name() + ":" + firstRowOfCode.get(row.employeeCode()));
            }
        }
        if (row.fullName() == null) {
            errors.add(ImportErrorCode.FULL_NAME_REQUIRED.name());
        } else if (row.fullName().length() > OrgStructureConstants.IMPORT_MAX_NAME_LENGTH) {
            errors.add(ImportErrorCode.FULL_NAME_TOO_LONG.name());
        }
        if (row.email() != null && !EMAIL.matcher(row.email()).matches()) {
            errors.add(ImportErrorCode.EMAIL_INVALID.name());
        }
        if (row.joinedOn() != null) {
            try {
                LocalDate.parse(row.joinedOn());
            } catch (DateTimeParseException e) {
                errors.add(ImportErrorCode.JOINED_ON_INVALID.name());
            }
        }
    }

    private static void checkPlacement(ImportRowInput row, ImportReference reference, List<String> errors) {
        if (row.departmentCode() == null) {
            errors.add(ImportErrorCode.DEPARTMENT_REQUIRED.name());
            return;
        }
        UnitRef unit = reference.unitsByCode().get(row.departmentCode());
        if (unit == null) {
            errors.add(ImportErrorCode.DEPARTMENT_UNKNOWN.name() + ":" + row.departmentCode());
            return;
        }
        if (row.positionName() == null) {
            errors.add(ImportErrorCode.POSITION_REQUIRED.name());
            return;
        }
        if (unit.ladderId() == null) {
            errors.add(ImportErrorCode.DEPARTMENT_HAS_NO_LADDER.name() + ":" + row.departmentCode());
            return;
        }
        Map<String, String> positions = reference.positionIdsByLadderAndName().get(unit.ladderId());
        if (positions == null || !positions.containsKey(row.positionName().toLowerCase(Locale.ROOT))) {
            errors.add(ImportErrorCode.POSITION_NOT_IN_LADDER.name() + ":" + row.positionName());
        }
    }

    private static void checkManager(ImportRowInput row, ImportReference reference, Set<String> codesInFile,
                                     Map<String, String> managerAfterImport, List<String> errors) {
        String manager = row.managerCode();
        if (manager == null) {
            return;
        }
        if (manager.equals(row.employeeCode())) {
            errors.add(ImportErrorCode.MANAGER_IS_SELF.name());
            return;
        }
        if (!codesInFile.contains(manager) && !reference.existingEmployeeCodes().contains(manager)) {
            errors.add(ImportErrorCode.MANAGER_UNKNOWN.name() + ":" + manager);
            return;
        }
        // Follow the chain upwards as it will be after the import; meeting this employee again is a cycle.
        Set<String> seen = new HashSet<>();
        String cursor = manager;
        while (cursor != null && seen.add(cursor)) {
            if (cursor.equals(row.employeeCode())) {
                errors.add(ImportErrorCode.MANAGER_CYCLE.name());
                return;
            }
            cursor = managerAfterImport.get(cursor);
        }
    }
}
