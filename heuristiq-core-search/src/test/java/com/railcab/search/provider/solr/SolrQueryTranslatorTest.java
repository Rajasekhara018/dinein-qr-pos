package com.heuristiq.search.provider.solr;

import com.heuristiq.search.model.SearchModel;
import com.heuristiq.search.query.LogicOp;
import com.heuristiq.search.query.Operator;
import com.heuristiq.search.query.SearchCondition;
import com.heuristiq.search.query.SearchGroup;
import com.heuristiq.search.query.SearchQuery;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SolrQueryTranslatorTest {

    private final SolrQueryTranslator translator = new SolrQueryTranslator();

    @Test
    void franchiseLocationsCanUseModelKeyAsTheFinalSortField() {
        var query = translator.translate(new SearchQuery(SearchModel.FRANCHISE_LOCATION, null),
                Sort.by(Sort.Order.asc("number1"), Sort.Order.asc("text1"), Sort.Order.asc("modelKey")));

        assertThat(query.sort()).isEqualTo("number1_l asc,text1_s asc,modelKey_s asc");
        assertThat(run(null, Sort.by(Sort.Order.desc("modelKey"))).sort()).isEqualTo("modelKey_s desc");
    }

    private SolrQueryTranslator.SolrQuery run(SearchGroup group, Sort sort) {
        return translator.translate(new SearchQuery(SearchModel.APPLICATION, group), sort);
    }

    private static SearchCondition c(String field, Operator op, String value) {
        return new SearchCondition(field, op, value);
    }

    @Test
    void aModelOnlyQueryMatchesEverythingOfTheModel() {
        var q = run(null, Sort.unsorted());

        assertThat(q.q()).isEqualTo("*:*");
        assertThat(q.filters()).containsExactly("model_s:\"APPLICATION\"");
        assertThat(q.sort()).isEmpty();
    }

    @Test
    void textOperatorsMeanTheSameAsInElasticsearch() {
        assertThat(run(new SearchGroup(LogicOp.AND, List.of(c("text2", Operator.EQ, "A \"x\""))), Sort.unsorted()).filters().get(1))
                .isEqualTo("(text2_s:\"A \\\"x\\\"\")");
        assertThat(run(new SearchGroup(LogicOp.AND, List.of(c("text2", Operator.NE, "A"))), Sort.unsorted()).filters().get(1))
                .isEqualTo("((*:* -text2_s:\"A\"))");
        assertThat(run(new SearchGroup(LogicOp.AND, List.of(c("text2", Operator.LIKE, "ab c"))), Sort.unsorted()).filters().get(1))
                .isEqualTo("(text2_s:*ab\\ c*)");
        assertThat(run(new SearchGroup(LogicOp.AND, List.of(c("text1", Operator.IN, "SAVED, BOARDED"))), Sort.unsorted()).filters().get(1))
                .isEqualTo("(text1_s:(\"SAVED\" OR \"BOARDED\"))");
    }

    @Test
    void wildcardInputCannotInjectSolrSyntax() {
        String filter = run(new SearchGroup(LogicOp.AND, List.of(c("text1", Operator.LIKE, "x* OR text2_s:*"))), Sort.unsorted()).filters().get(1);

        assertThat(filter).isEqualTo("(text1_s:*x\\*\\ OR\\ text2_s\\:\\**)");
    }

    @Test
    void rangesOnAmountsNumbersAndDatesUseTheirTypedFields() {
        var q = run(new SearchGroup(LogicOp.AND, List.of(
                c("amount1", Operator.GE, "100"),
                c("number2", Operator.LT, "7"),
                c("date1", Operator.GT, "1791916200000"),
                c("datetime1", Operator.LE, "2026-01-01T00:00:00Z"))), Sort.unsorted());

        assertThat(q.filters().get(1)).isEqualTo(
                "(amount1_d:[100.0 TO *] AND number2_l:[* TO 7} AND date1_dt:{2026-10-13T18:30:00Z TO *] AND datetime1_dt:[* TO 2026-01-01T00:00:00Z])");
    }

    @Test
    void nestedGroupsBecomeParenthesesWithTheirOwnLogic() {
        var nested = new SearchGroup(LogicOp.OR, List.of(c("text14", Operator.EQ, "MERWEB"), c("text14", Operator.EQ, "MERAPP")));
        var q = run(new SearchGroup(LogicOp.AND, List.of(c("text1", Operator.EQ, "SAVED")), List.of(nested)), Sort.unsorted());

        assertThat(q.filters().get(1)).isEqualTo("(text1_s:\"SAVED\" AND (text14_s:\"MERWEB\" OR text14_s:\"MERAPP\"))");
    }

    @Test
    void sortUsesTheStoredFieldOfEachSlot() {
        var q = run(null, Sort.by(Sort.Order.desc("date1"), Sort.Order.asc("text3"), Sort.Order.desc("createdAt"), Sort.Order.asc("amount2")));

        assertThat(q.sort()).isEqualTo("date1_dt desc,text3_s asc,createdAt_dt desc,amount2_d asc");
        assertThatThrownBy(() -> run(null, Sort.by("password"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unsupportedOrBadInputIsRefused() {
        assertThatThrownBy(() -> run(new SearchGroup(LogicOp.AND, List.of(c("amount1", Operator.LIKE, "1"))), Sort.unsorted()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> run(new SearchGroup(LogicOp.AND, List.of(c(" ", Operator.EQ, "1"))), Sort.unsorted()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> run(new SearchGroup(LogicOp.AND, List.of(c("amount1", Operator.EQ, "x"))), Sort.unsorted()))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void emptyMeansNoValueInTheStoredField() {
        assertThat(run(new SearchGroup(LogicOp.AND, List.of(c("ownerKey", Operator.EMPTY, null))), Sort.unsorted()).filters().get(1))
                .isEqualTo("((*:* -ownerKey_s:[* TO *]))");
        assertThat(run(new SearchGroup(LogicOp.AND, List.of(c("text4", Operator.EMPTY, null))), Sort.unsorted()).filters().get(1))
                .isEqualTo("((*:* -text4_s:[* TO *]))");
    }
}
