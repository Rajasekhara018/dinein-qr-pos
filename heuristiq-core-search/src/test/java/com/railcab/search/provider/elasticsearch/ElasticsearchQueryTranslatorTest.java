package com.heuristiq.search.provider.elasticsearch;

import com.heuristiq.search.model.SearchModel;
import com.heuristiq.search.query.LogicOp;
import com.heuristiq.search.query.Operator;
import com.heuristiq.search.query.SearchCondition;
import com.heuristiq.search.query.SearchGroup;
import com.heuristiq.search.query.SearchQuery;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElasticsearchQueryTranslatorTest {

    private final ElasticsearchQueryTranslator translator = new ElasticsearchQueryTranslator();

    @Test
    void translatesModelOnlyQueryToTopLevelModelCriteria() {
        SearchQuery query = new SearchQuery(SearchModel.RIDE, null);

        CriteriaQuery criteriaQuery = translator.translate(query);

        assertThat(criteriaQuery.getCriteria().getField().getName()).isEqualTo("model");
    }

    @Test
    void appliesEqOnTextFieldUsingKeywordSubfield() {
        SearchQuery query = new SearchQuery(SearchModel.RIDE, new SearchGroup(LogicOp.AND,
                List.of(new SearchCondition("text3", Operator.EQ, "COMPLETED"))));

        CriteriaQuery criteriaQuery = translator.translate(query);

        assertThat(criteriaQuery).isNotNull();
    }

    @Test
    void supportsRangeOperatorsOnAmountFields() {
        SearchQuery query = new SearchQuery(SearchModel.RIDE, new SearchGroup(LogicOp.AND,
                List.of(new SearchCondition("amount1", Operator.GE, "100.0"))));

        assertThat(translator.translate(query)).isNotNull();
    }

    @Test
    void supportsRangeOperatorsOnDateFields() {
        SearchQuery query = new SearchQuery(SearchModel.RIDE, new SearchGroup(LogicOp.AND,
                List.of(new SearchCondition("date1", Operator.GE, "2026-01-01T00:00:00Z"))));

        assertThat(translator.translate(query)).isNotNull();
    }

    @Test
    void supportsOrLogicAcrossConditions() {
        SearchQuery query = new SearchQuery(SearchModel.RIDE, new SearchGroup(LogicOp.OR,
                List.of(
                        new SearchCondition("text3", Operator.EQ, "COMPLETED"),
                        new SearchCondition("text3", Operator.EQ, "CANCELLED"))));

        assertThat(translator.translate(query)).isNotNull();
    }

    @Test
    void rejectsLikeOperatorOnAmountField() {
        SearchQuery query = new SearchQuery(SearchModel.RIDE, new SearchGroup(LogicOp.AND,
                List.of(new SearchCondition("amount1", Operator.LIKE, "100"))));

        assertThatThrownBy(() -> translator.translate(query)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankField() {
        SearchQuery query = new SearchQuery(SearchModel.RIDE, new SearchGroup(LogicOp.AND,
                List.of(new SearchCondition(" ", Operator.EQ, "value"))));

        assertThatThrownBy(() -> translator.translate(query)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnparsableAmountValue() {
        SearchQuery query = new SearchQuery(SearchModel.RIDE, new SearchGroup(LogicOp.AND,
                List.of(new SearchCondition("amount1", Operator.EQ, "not-a-number"))));

        assertThatThrownBy(() -> translator.translate(query)).isInstanceOf(NumberFormatException.class);
    }

    @Test
    void nestedGroupsBecomeBracketedSubCriteria() {
        SearchGroup nested = new SearchGroup(LogicOp.OR, List.of(
                new SearchCondition("text14", Operator.EQ, "MERWEB"),
                new SearchCondition("text14", Operator.EQ, "MERAPP")));
        SearchQuery query = new SearchQuery(SearchModel.RIDE, new SearchGroup(LogicOp.AND,
                List.of(new SearchCondition("text1", Operator.EQ, "SAVED")), List.of(nested)));

        CriteriaQuery criteriaQuery = translator.translate(query);

        java.util.List<String> fields = new java.util.ArrayList<>();
        collectFields(criteriaQuery.getCriteria(), fields);
        assertThat(fields).contains("model", "text1.keyword", "text14.keyword");
        assertThat(hasSubCriteria(criteriaQuery.getCriteria())).isTrue();
    }

    private static void collectFields(org.springframework.data.elasticsearch.core.query.Criteria criteria, java.util.List<String> into) {
        if (criteria.getField() != null) {
            into.add(criteria.getField().getName());
        }
        criteria.getCriteriaChain().forEach(c -> { if (c != criteria) collectFields(c, into); });
        criteria.getSubCriteria().forEach(c -> collectFields(c, into));
    }

    private static boolean hasSubCriteria(org.springframework.data.elasticsearch.core.query.Criteria criteria) {
        return !criteria.getSubCriteria().isEmpty()
                || criteria.getCriteriaChain().stream().anyMatch(c -> c != criteria && hasSubCriteria(c));
    }

    @Test
    void aDateMayBeEpochMillisecondsOrIsoText() {
        SearchQuery epoch = new SearchQuery(SearchModel.RIDE, new SearchGroup(LogicOp.AND,
                List.of(new SearchCondition("date1", Operator.GE, "1791916200000"))));
        assertThat(translator.translate(epoch)).isNotNull();
        SearchQuery bad = new SearchQuery(SearchModel.RIDE, new SearchGroup(LogicOp.AND,
                List.of(new SearchCondition("date1", Operator.GE, "yesterday"))));
        assertThatThrownBy(() -> translator.translate(bad)).isInstanceOf(java.time.format.DateTimeParseException.class);
    }

    @Test
    void anEmptyGroupAddsNoFilter() {
        SearchQuery query = new SearchQuery(SearchModel.RIDE, new SearchGroup(LogicOp.AND, List.of(), List.of()));
        assertThat(translator.translate(query).getCriteria().getField().getName()).isEqualTo("model");
    }

    @Test
    void emptyMeansTheFieldHasNoValueAndIsAcceptedOnStructuralAndTextFields() {
        SearchQuery owner = new SearchQuery(SearchModel.ROLE, new SearchGroup(LogicOp.AND,
                List.of(new SearchCondition("ownerKey", Operator.EMPTY, null))));
        SearchQuery text = new SearchQuery(SearchModel.ROLE, new SearchGroup(LogicOp.AND,
                List.of(new SearchCondition("text4", Operator.EMPTY, null))));

        assertThat(translator.translate(owner)).isNotNull();
        assertThat(translator.translate(text)).isNotNull();
    }
}
