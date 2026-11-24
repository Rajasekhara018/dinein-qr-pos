package com.heuristiq.search.provider.solr;

import java.util.regex.Pattern;

/**
 * Names of the Solr fields. Solr's dynamic-field suffixes carry the type, so the collection needs no per-slot schema
 * (see docs/solr-sample/README.md): {@code _s} string (exact), {@code _t} analysed text, {@code _d} double,
 * {@code _l} long, {@code _dt} date.
 */
final class SolrConstants {

    static final String ID = "id";
    static final String MODEL = "model_s";
    static final String MODEL_KEY = "modelKey_s";
    static final String OWNER_KEY = "ownerKey_s";
    static final String TENANT_ID = "tenantId_s";
    static final String CREATED_AT = "createdAt_dt";
    static final String UPDATED_AT = "updatedAt_dt";

    static final String EXACT_SUFFIX = "_s";
    static final String TEXT_SUFFIX = "_t";
    static final String DOUBLE_SUFFIX = "_d";
    static final String LONG_SUFFIX = "_l";
    static final String DATE_SUFFIX = "_dt";

    /** Query matching every document, the base of a filter-only search. */
    static final String MATCH_ALL = "*:*";

    /** A logical slot name (text1, amount2, datetime5, ...): group 1 is the slot type, group 2 its number. */
    static final Pattern SLOT_FIELD = Pattern.compile("^(text|amount|number|datetime|date)(\\d+)$");

    /** Update handler path; commit=true makes a change searchable at once. */
    static final String UPDATE_COMMIT_PATH = "/update?commit=true";
    /** Real-time get handler path and the key of the document in its answer. */
    static final String REALTIME_GET_PATH = "/get";
    static final String REALTIME_GET_DOC = "doc";
    /** Update command that deletes by id. */
    static final String DELETE_COMMAND = "delete";

    private SolrConstants() {
    }
}
