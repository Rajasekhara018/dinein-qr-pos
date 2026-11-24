package com.railcab.dms.document.persistence.postgres;

import com.railcab.dms.document.persistence.DocumentSearchCriteria;
import com.railcab.dms.document.persistence.SearchPatterns;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the JPA predicate of the lifecycle document search. The organization is always the first, mandatory
 * predicate; free text is escaped so it can never act as a LIKE wildcard. Stateless.
 */
final class DocumentSearchSpecifications {

    private DocumentSearchSpecifications() {
    }

    static Specification<DocumentMetadataJpaEntity> forTenant(String tenantId, DocumentSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            all.add(cb.equal(root.get("tenantId"), tenantId));
            equalsIfText(all, root.get("ownerId"), criteria.ownerId(), cb);
            equalsIfText(all, root.get("category"), criteria.category(), cb);
            equalsIfText(all, root.get("documentType"), criteria.documentType(), cb);
            equalsIfText(all, root.get("status"), criteria.status(), cb);
            equalsIfText(all, root.get("scanStatus"), criteria.scanStatus(), cb);
            if (criteria.deleted() != null) {
                all.add(cb.equal(root.get("deleted"), criteria.deleted()));
            }
            if (criteria.from() != null) {
                all.add(cb.greaterThanOrEqualTo(root.<java.time.Instant>get("createdAt"), criteria.from()));
            }
            if (criteria.to() != null) {
                all.add(cb.lessThanOrEqualTo(root.<java.time.Instant>get("createdAt"), criteria.to()));
            }
            if (StringUtils.hasText(criteria.query())) {
                String text = criteria.query().trim();
                all.add(cb.or(
                        cb.equal(root.get("id"), text),
                        cb.like(cb.lower(root.<String>get("originalFileName")), SearchPatterns.likeContains(text),
                                SearchPatterns.LIKE_ESCAPE),
                        cb.like(root.<String>get("checksumSha256"), SearchPatterns.likePrefix(text.toLowerCase(Locale.ROOT)),
                                SearchPatterns.LIKE_ESCAPE)));
            }
            return cb.and(all.toArray(new Predicate[0]));
        };
    }

    private static void equalsIfText(List<Predicate> all, jakarta.persistence.criteria.Path<Object> path, String value,
                                     jakarta.persistence.criteria.CriteriaBuilder cb) {
        if (StringUtils.hasText(value)) {
            all.add(cb.equal(path, value));
        }
    }
}
