package com.railcab.dms.document.persistence.mongo;

import com.railcab.dms.document.persistence.DocumentSearchCriteria;
import com.railcab.dms.document.persistence.SearchPatterns;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Builds the MongoDB query of the lifecycle document search. The organization is always the first, mandatory
 * criterion; free text is escaped to a literal pattern. Stateless.
 */
final class MongoDocumentSearchQueries {

    private MongoDocumentSearchQueries() {
    }

    /** The filter (no paging or sorting) for one organization. */
    static Query build(String tenantId, DocumentSearchCriteria criteria) {
        List<Criteria> all = new ArrayList<>();
        all.add(Criteria.where("tenantId").is(tenantId));
        addEquals(all, "ownerId", criteria.ownerId());
        addEquals(all, "category", criteria.category());
        addEquals(all, "documentType", criteria.documentType());
        addEquals(all, "status", criteria.status());
        addEquals(all, "scanStatus", criteria.scanStatus());
        if (criteria.deleted() != null) {
            all.add(Criteria.where("deleted").is(criteria.deleted()));
        }
        if (criteria.from() != null || criteria.to() != null) {
            Criteria range = Criteria.where("createdAt");
            if (criteria.from() != null) {
                range = range.gte(criteria.from());
            }
            if (criteria.to() != null) {
                range = range.lte(criteria.to());
            }
            all.add(range);
        }
        if (StringUtils.hasText(criteria.query())) {
            String text = criteria.query().trim();
            all.add(new Criteria().orOperator(
                    Criteria.where("id").is(text),
                    Criteria.where("originalFileName")
                            .regex(Pattern.compile(SearchPatterns.escapeRegex(text), Pattern.CASE_INSENSITIVE)),
                    Criteria.where("checksumSha256")
                            .regex(Pattern.compile("^" + SearchPatterns.escapeRegex(text.toLowerCase(Locale.ROOT))))));
        }
        return new Query(new Criteria().andOperator(all));
    }

    private static void addEquals(List<Criteria> all, String field, String value) {
        if (StringUtils.hasText(value)) {
            all.add(Criteria.where(field).is(value));
        }
    }
}
