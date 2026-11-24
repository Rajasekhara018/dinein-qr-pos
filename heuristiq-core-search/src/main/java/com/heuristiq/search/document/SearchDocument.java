package com.heuristiq.search.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Generic EAV-style search document, one shape for every {@link com.heuristiq.search.model.SearchModel}: each producer's
 * {@code SearchDocumentMapper} decides which text / amount / number / date slot a domain field lands in, and the field
 * to meaning contract lives in that mapper. This class is engine-neutral: how it is stored (index, field mappings,
 * exact-value sub-fields) is the business of the active {@code SearchIndexPort} implementation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchDocument {

    /** Composite id: "{model}:{modelKey}". */
    private String id;

    private String model;

    /** The id of the owning entity in its source-of-truth service. */
    private String modelKey;

    /** Optional partition key (e.g. vendorId/customerId) for scoping search results. */
    private String ownerKey;

    /** Owning organization id, separate from model-specific user/owner partition keys. */
    private String tenantId;

    /** Source-owned public list DTO; optional, never used to authorize or filter a search. */
    private java.util.Map<String, Object> payload;

    private String text1;
    private String text2;
    private String text3;
    private String text4;
    private String text5;
    private String text6;
    private String text7;
    private String text8;
    private String text9;
    private String text10;
    private String text11;
    private String text12;
    private String text13;
    private String text14;
    private String text15;

    private Double amount1;
    private Double amount2;
    private Double amount3;
    private Double amount4;
    private Double amount5;

    private Long number1;
    private Long number2;
    private Long number3;
    private Long number4;
    private Long number5;

    private Instant date1;
    private Instant date2;
    private Instant date3;
    private Instant date4;
    private Instant date5;

    private Instant datetime1;
    private Instant datetime2;
    private Instant datetime3;
    private Instant datetime4;
    private Instant datetime5;

    private Instant createdAt;
    private Instant updatedAt;
}
