package com.heuristiq.identity.organization.operatingmodel.domain;

/** Fixed values of the operating model. */
public final class OperatingModelConstants {

    /** Field names of a change in the audit trail. */
    public static final String MODEL_FIELD = "model";
    public static final String EFFECTIVE_FROM_FIELD = "effectiveFrom";
    /** Key of the schedule in the audit trail's before / after values. */
    public static final String SCHEDULE_FIELD = "schedule";

    /** The model of an organization that has never chosen one. */
    public static final OperatingModel DEFAULT_MODEL = OperatingModel.DIRECT;

    /** Entity type of the domain audit events. */
    public static final String AUDIT_ENTITY_TYPE = "ORGANIZATION_OPERATING_MODEL";

    public static final String ERROR_MODEL_REQUIRED = "Operating model is required";
    public static final String ERROR_DATE_REQUIRED = "Effective date is required";
    public static final String ERROR_DATE_IN_PAST = "The operating model can only change from today or a later date";
    public static final String ERROR_DATE_NOT_AFTER_LAST = "Each change must start after the one before it";
    public static final String ERROR_SAME_MODEL = "The organization already uses this operating model on that date";
    public static final String ERROR_ORGANIZATION_NOT_FOUND = "Organization not found";
    public static final String ERROR_CHANGED_MEANWHILE = "The operating model was changed meanwhile; reload and try again";
    public static final String ERROR_MALFORMED = "The organization's operating-model schedule is malformed";
    public static final String ERROR_NO_SESSION_ORGANIZATION = "The session is not associated with an organization";

    private OperatingModelConstants() {
    }
}
