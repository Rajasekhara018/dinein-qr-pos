package com.heuristiq.uam.audit;

/**
 * Port for UAM's domain audit events. UAM stores and queries nothing itself: the implementation hands the entry to the
 * central audit service (Rider_AuditService), which is the only place audit information is collected and read.
 */
public interface AuditLogServiceUseCase {

    record RecordInput(AuditLog entry) {}
    record RecordOutput(AuditLog entry) {}

    /** Reports the entry to the central audit service. Never throws and never blocks on the audit service. */
    RecordOutput record(RecordInput input);
}
