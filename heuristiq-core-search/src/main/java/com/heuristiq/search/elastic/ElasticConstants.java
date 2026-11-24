package com.heuristiq.search.elastic;

import com.heuristiq.search.query.SearchFieldConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Fixed values of the "getall" index API: the request types, criteria codes, paging limits and the slots a document exposes. */
public final class ElasticConstants {

    /** Request type the getall API answers (a list, searched or not). */
    public static final String REQUEST_TYPE_LIST = "LIST";

    /** {@code criteria1} of a group: how its entries combine. */
    public static final int CRITERIA_AND = 0;
    public static final int CRITERIA_OR = 1;

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 50;

    /** Sorted on when the client names no field: newest first, so pages are stable. */
    public static final String DEFAULT_SORT_FIELD = SearchFieldConstants.CREATED_AT;

    /** Sort directions accepted in a request, compared ignoring case. */
    public static final String SORT_ASC = "ASC";
    public static final String SORT_DESC = "DESC";

    /** {@code type} / {@code id} separator of a document returned to the client: {@code merchantapplication-<key>}. */
    public static final String ID_SEPARATOR = "-";

    /** Fields a client may sort on (slots, model key and audit timestamps); text slots sort on their keyword sub-field. */
    public static final Pattern SORTABLE_FIELD = Pattern.compile(
            "^(text([1-9]|1[0-5])|amount[1-5]|number[1-5]|date[1-5]|datetime[1-5]|modelKey|createdAt|updatedAt)$");

    /** How many sort fields the request carries (field1..field3). */
    public static final int MAX_SORT_FIELDS = 3;

    /** Every slot a returned document may carry, in output order. */
    public static final List<String> SLOT_NAMES = slotNames();

    private static List<String> slotNames() {
        List<String> names = new ArrayList<>();
        for (int i = 1; i <= 15; i++) names.add("text" + i);
        for (int i = 1; i <= 5; i++) names.add("amount" + i);
        for (int i = 1; i <= 5; i++) names.add("number" + i);
        for (int i = 1; i <= 5; i++) names.add("date" + i);
        for (int i = 1; i <= 5; i++) names.add("datetime" + i);
        return List.copyOf(names);
    }

    private ElasticConstants() {
    }
}
