package com.heuristiq.search.provider.elasticsearch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;

import java.time.Instant;

/**
 * How a {@link com.heuristiq.search.document.SearchDocument} is stored in Elasticsearch: the same slots, with the index
 * name and the field mappings (every text slot also keeps an exact-value keyword sub-field, which EQ / IN / sorting use
 * while LIKE uses the analysed text). Values above 8191 characters remain in the source and analysed text, but are
 * omitted from keyword matching and sorting to stay below Lucene's term byte limit, including UTF-8 values.
 * It exists only inside this provider: the rest of the service never sees it, so
 * another engine brings its own storage class and mapper.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "railcab-search")
public class ElasticsearchDocument {

    /** Composite id: "{model}:{modelKey}". */
    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String model;

    /** The id of the owning entity in its source-of-truth service. */
    @Field(type = FieldType.Keyword)
    private String modelKey;

    /** Optional partition key (e.g. vendorId/customerId) for scoping search results. */
    @Field(type = FieldType.Keyword)
    private String ownerKey;

    /** Owning organization id, separate from model-specific user/owner partition keys. */
    @Field(type = FieldType.Keyword)
    private String tenantId;

    @Field(type = FieldType.Object, enabled = false)
    private java.util.Map<String, Object> payload;

    @MultiField(mainField = @Field(type = FieldType.Text), otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 8191))
    private String text1;
    @MultiField(mainField = @Field(type = FieldType.Text), otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 8191))
    private String text2;
    @MultiField(mainField = @Field(type = FieldType.Text), otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 8191))
    private String text3;
    @MultiField(mainField = @Field(type = FieldType.Text), otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 8191))
    private String text4;
    @MultiField(mainField = @Field(type = FieldType.Text), otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 8191))
    private String text5;
    @MultiField(mainField = @Field(type = FieldType.Text), otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 8191))
    private String text6;
    @MultiField(mainField = @Field(type = FieldType.Text), otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 8191))
    private String text7;
    @MultiField(mainField = @Field(type = FieldType.Text), otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 8191))
    private String text8;
    @MultiField(mainField = @Field(type = FieldType.Text), otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 8191))
    private String text9;
    @MultiField(mainField = @Field(type = FieldType.Text), otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 8191))
    private String text10;
    @MultiField(mainField = @Field(type = FieldType.Text), otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 8191))
    private String text11;
    @MultiField(mainField = @Field(type = FieldType.Text), otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 8191))
    private String text12;
    @MultiField(mainField = @Field(type = FieldType.Text), otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 8191))
    private String text13;
    @MultiField(mainField = @Field(type = FieldType.Text), otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 8191))
    private String text14;
    @MultiField(mainField = @Field(type = FieldType.Text), otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 8191))
    private String text15;

    @Field(type = FieldType.Double)
    private Double amount1;
    @Field(type = FieldType.Double)
    private Double amount2;
    @Field(type = FieldType.Double)
    private Double amount3;
    @Field(type = FieldType.Double)
    private Double amount4;
    @Field(type = FieldType.Double)
    private Double amount5;

    @Field(type = FieldType.Long)
    private Long number1;
    @Field(type = FieldType.Long)
    private Long number2;
    @Field(type = FieldType.Long)
    private Long number3;
    @Field(type = FieldType.Long)
    private Long number4;
    @Field(type = FieldType.Long)
    private Long number5;

    @Field(type = FieldType.Date)
    private Instant date1;
    @Field(type = FieldType.Date)
    private Instant date2;
    @Field(type = FieldType.Date)
    private Instant date3;
    @Field(type = FieldType.Date)
    private Instant date4;
    @Field(type = FieldType.Date)
    private Instant date5;

    @Field(type = FieldType.Date)
    private Instant datetime1;
    @Field(type = FieldType.Date)
    private Instant datetime2;
    @Field(type = FieldType.Date)
    private Instant datetime3;
    @Field(type = FieldType.Date)
    private Instant datetime4;
    @Field(type = FieldType.Date)
    private Instant datetime5;

    @Field(type = FieldType.Date)
    private Instant createdAt;
    @Field(type = FieldType.Date)
    private Instant updatedAt;
}
