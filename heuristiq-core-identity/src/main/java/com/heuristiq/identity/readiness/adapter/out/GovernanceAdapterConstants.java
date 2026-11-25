package com.heuristiq.identity.readiness.adapter.out;

import java.util.Map;

/** Fixed values of the governance adapters (SQL, collection names, language mapping). */
final class GovernanceAdapterConstants {

    /** Atomic take-or-renew of a lease; parameters: name, holder, lease milliseconds. Database clock on both sides. */
    static final String LEASE_ACQUIRE_SQL = "INSERT INTO scheduler_locks (name, holder, locked_until) "
            + "VALUES (?, ?, now() + (? * interval '1 millisecond')) "
            + "ON CONFLICT (name) DO UPDATE SET holder = EXCLUDED.holder, locked_until = EXCLUDED.locked_until "
            + "WHERE scheduler_locks.locked_until < now() OR scheduler_locks.holder = EXCLUDED.holder";

    /** Gives a lease back early; parameters: name, holder. */
    static final String LEASE_RELEASE_SQL = "UPDATE scheduler_locks SET locked_until = now() - interval '1 second' "
            + "WHERE name = ? AND holder = ?";

    /** Mongo collection of leases (same name as the Postgres table). */
    static final String LEASE_COLLECTION = "scheduler_locks";

    static final String LEASE_FIELD_HOLDER = "holder";
    static final String LEASE_FIELD_LOCKED_UNTIL = "lockedUntil";

    /** Holder id used when the host name can not be resolved. */
    static final String FALLBACK_HOLDER = "instance";

    /** Account language codes mapped to NMS template languages (OD has no templates: it falls back to English). */
    static final Map<String, String> LANGUAGE_BY_ACCOUNT_LANGUAGE = Map.of(
            "EN", "en", "HI", "hi", "TA", "ta", "TE", "te", "KA", "kn", "ML", "ml", "AR", "ar");

    /** Origin entity type sent to NMS. */
    static final String ENTITY_TYPE = "TENANT_APPLICATION";

    /** Page size used to look up SUPER_ADMIN accounts (also capped by the recipient limit). */
    static final int SUPER_ADMIN_PAGE_MAX = 200;

    private GovernanceAdapterConstants() {
    }
}
