package com.heuristiq.uam.orgstructure.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Plain value types of the organization structure. */
public final class Models {
    private Models() {
    }

    public enum Severity { ERROR, WARNING }

    public record ValidationIssue(Severity severity, String code, String message, String subjectId) {
    }

    public record UnitView(String id, String parentId, String code, String name, String unitType, String ladderId,
                           String headEmployeeId, String headName, String headUserId, String status, int childCount,
                           int headcount) {
    }

    /** A department as the workflow service sees it: whether it exists, its name, and the user accounts of everyone in it. */
    public record DepartmentMembers(boolean found, String code, String name, List<String> userIds) {
    }

    /** One person below a manager (at any depth) with a user account, and the user account of their own manager. */
    public record TeamNode(String userId, String fullName, String managerUserId) {
    }

    /** An active department and the user account of its head (departments without a head, or whose head has no account, are left out). */
    public record DepartmentHead(String unitId, String unitCode, String unitName, String headUserId, String headName) {
    }

    /** A person who reports directly to a manager and has a user account. */
    public record TeamMember(String userId, String fullName) {
    }

    public record LadderView(String id, String name) {
    }

    public record PositionView(String id, String ladderId, String name, int rank, boolean managerial) {
    }

    public record PersonView(String employeeId, String employeeCode, String fullName, String email, String unitId,
                             String positionId, String positionName, String managerEmployeeId, String userId) {
    }

    public record Employee(String id, String employeeCode, String fullName, String email, String phone, String status,
                           String userId, LocalDate joinedOn) {
    }

    public record ChangeSet(String id, String status, String note, String createdBy, Instant createdAt,
                            String publishedBy, Instant publishedAt, long version) {
    }

    public record Change(String id, String changeSetId, int seq, ChangeType type, Map<String, Object> payload) {
    }

    /** Outcome of applying a draft in a throw-away transaction (preview) or for real (publish). */
    public record DraftResult(boolean applied, List<ValidationIssue> issues, List<UnitView> rootUnits) {
    }
}
