package com.heuristiq.search.query;

/**
 * Generic slot-name prefixes and structural field names, mirroring the constants touidx's
 * {@code DomainConstants} declares for the same purpose ({@code TEXT}/{@code AMOUNT}/
 * {@code NUMBER}/{@code DATE}/{@code DATETIME} prefixes, {@code MODEL}/{@code MODELKEY}). Kept as
 * a small, RailCab-scoped class here rather than pulling in touidx's ~900-constant grab-bag, which
 * is almost entirely acquiring-domain-specific and irrelevant to this platform.
 */
public final class SearchFieldConstants {

    public static final String TEXT_PREFIX = "text";
    public static final String AMOUNT_PREFIX = "amount";
    public static final String NUMBER_PREFIX = "number";
    public static final String DATE_PREFIX = "date";
    public static final String DATETIME_PREFIX = "datetime";
    public static final String KEYWORD_SUFFIX = ".keyword";

    public static final String MODEL = "model";
    public static final String MODEL_KEY = "modelKey";
    public static final String OWNER_KEY = "ownerKey";
    public static final String TENANT_ID = "tenantId";
    public static final String CREATED_AT = "createdAt";
    public static final String UPDATED_AT = "updatedAt";

    /** A date value given as epoch milliseconds (the alternative to ISO-8601), for {@link String#matches}. */
    public static final String EPOCH_MILLIS_REGEX = "-?\\d{1,19}";

    private SearchFieldConstants() {
    }
}
