package com.heuristiq.search.indexing;

import com.heuristiq.search.query.SearchFieldConstants;
import com.heuristiq.search.document.SearchDocument;
import com.heuristiq.search.service.SearchIndexService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Builds a {@link SearchDocument} from the generic slot-keyed payload map a producer service
 * already published (e.g. {@code {"text1": "MG Road", "text2": "Airport", "amount1": 245.0,
 * "date1": "2026-07-19T10:00:00Z"}}). Producers are responsible for mapping their own domain
 * fields into these generic slot names (their own per-service {@code SearchDocumentMapper}) --
 * this class does no per-model interpretation, it just assembles whatever slots are present.
 */
@Component
public class SearchDocumentAssembler {

    private final com.heuristiq.search.scope.TenantScopePolicy tenantPolicy;

    @org.springframework.beans.factory.annotation.Autowired
    public SearchDocumentAssembler(com.heuristiq.search.scope.TenantScopePolicy tenantPolicy) {
        this.tenantPolicy = tenantPolicy;
    }

    public SearchDocumentAssembler() {
        this(new com.heuristiq.search.scope.TenantScopePolicy(com.heuristiq.search.scope.TenantScopingProperties.defaults()));
    }

    public SearchDocument assemble(String model, String modelKey, Map<String, Object> payload) {
        SearchDocument doc = new SearchDocument();
        doc.setId(SearchIndexService.compositeId(model, modelKey));
        doc.setModel(model);
        doc.setModelKey(modelKey);
        Object sourcePayload = payload.get("payload");
        if (sourcePayload instanceof Map<?, ?> values && model != null && (model.startsWith("FRANCHISE_")
                || com.heuristiq.search.projection.WebSearchCatalog.contains(model))) {
            Map<String, Object> publicRow = new java.util.LinkedHashMap<>();
            values.forEach((key, value) -> publicRow.put(String.valueOf(key), value));
            doc.setPayload(publicRow);
        }
        doc.setUpdatedAt(Instant.now());

        Object existingCreatedAt = payload.get(SearchFieldConstants.CREATED_AT);
        doc.setCreatedAt(existingCreatedAt != null ? toInstant(existingCreatedAt) : Instant.now());

        setIfPresent(payload, SearchFieldConstants.OWNER_KEY, v -> doc.setOwnerKey(asString(v)));
        String tenant = optionalText(payload.get(SearchFieldConstants.TENANT_ID));
        if (tenantPolicy.of(model) != com.heuristiq.search.scope.TenantScoping.NONE) {
            String owner = optionalText(doc.getOwnerKey());
            if (tenant != null && owner != null && !tenant.equals(owner)) {
                throw new IllegalArgumentException("tenantId and ownerKey must identify the same organization for " + model);
            }
            tenant = tenant != null ? tenant : owner != null ? owner : com.heuristiq.search.scope.TenantScopeConstants.DEFAULT_TENANT_ID;
            doc.setOwnerKey(tenant);
        }
        doc.setTenantId(tenant);

        setIfPresent(payload, "text1", v -> doc.setText1(asString(v)));
        setIfPresent(payload, "text2", v -> doc.setText2(asString(v)));
        setIfPresent(payload, "text3", v -> doc.setText3(asString(v)));
        setIfPresent(payload, "text4", v -> doc.setText4(asString(v)));
        setIfPresent(payload, "text5", v -> doc.setText5(asString(v)));
        setIfPresent(payload, "text6", v -> doc.setText6(asString(v)));
        setIfPresent(payload, "text7", v -> doc.setText7(asString(v)));
        setIfPresent(payload, "text8", v -> doc.setText8(asString(v)));
        setIfPresent(payload, "text9", v -> doc.setText9(asString(v)));
        setIfPresent(payload, "text10", v -> doc.setText10(asString(v)));
        setIfPresent(payload, "text11", v -> doc.setText11(asString(v)));
        setIfPresent(payload, "text12", v -> doc.setText12(asString(v)));
        setIfPresent(payload, "text13", v -> doc.setText13(asString(v)));
        setIfPresent(payload, "text14", v -> doc.setText14(asString(v)));
        setIfPresent(payload, "text15", v -> doc.setText15(asString(v)));

        setIfPresent(payload, "amount1", v -> doc.setAmount1(toDouble(v)));
        setIfPresent(payload, "amount2", v -> doc.setAmount2(toDouble(v)));
        setIfPresent(payload, "amount3", v -> doc.setAmount3(toDouble(v)));
        setIfPresent(payload, "amount4", v -> doc.setAmount4(toDouble(v)));
        setIfPresent(payload, "amount5", v -> doc.setAmount5(toDouble(v)));

        setIfPresent(payload, "number1", v -> doc.setNumber1(toLong(v)));
        setIfPresent(payload, "number2", v -> doc.setNumber2(toLong(v)));
        setIfPresent(payload, "number3", v -> doc.setNumber3(toLong(v)));
        setIfPresent(payload, "number4", v -> doc.setNumber4(toLong(v)));
        setIfPresent(payload, "number5", v -> doc.setNumber5(toLong(v)));

        setIfPresent(payload, "date1", v -> doc.setDate1(toInstant(v)));
        setIfPresent(payload, "date2", v -> doc.setDate2(toInstant(v)));
        setIfPresent(payload, "date3", v -> doc.setDate3(toInstant(v)));
        setIfPresent(payload, "date4", v -> doc.setDate4(toInstant(v)));
        setIfPresent(payload, "date5", v -> doc.setDate5(toInstant(v)));

        setIfPresent(payload, "datetime1", v -> doc.setDatetime1(toInstant(v)));
        setIfPresent(payload, "datetime2", v -> doc.setDatetime2(toInstant(v)));
        setIfPresent(payload, "datetime3", v -> doc.setDatetime3(toInstant(v)));
        setIfPresent(payload, "datetime4", v -> doc.setDatetime4(toInstant(v)));
        setIfPresent(payload, "datetime5", v -> doc.setDatetime5(toInstant(v)));

        return doc;
    }

    private static String optionalText(Object value) {
        String text = value == null ? null : String.valueOf(value).trim();
        return text == null || text.isEmpty() ? null : text;
    }

    private void setIfPresent(Map<String, Object> payload, String key, Consumer<Object> setter) {
        Object value = payload.get(key);
        if (value != null) {
            setter.accept(value);
        }
    }

    private String asString(Object value) {
        return String.valueOf(value);
    }

    private Double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
    }

    private Long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private Instant toInstant(Object value) {
        if (value instanceof Number number) {
            return Instant.ofEpochMilli(number.longValue());
        }
        return Instant.parse(String.valueOf(value));
    }
}
