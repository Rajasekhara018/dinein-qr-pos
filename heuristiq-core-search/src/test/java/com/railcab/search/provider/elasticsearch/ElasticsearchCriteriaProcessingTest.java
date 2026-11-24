package com.heuristiq.search.provider.elasticsearch;

import com.heuristiq.search.model.SearchModel;
import com.heuristiq.search.query.LogicOp;
import com.heuristiq.search.query.Operator;
import com.heuristiq.search.query.SearchCondition;
import com.heuristiq.search.query.SearchGroup;
import com.heuristiq.search.query.SearchQuery;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.query.Criteria;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the translated criteria through Spring Data's own processor, which is where malformed trees are rejected. */
class ElasticsearchCriteriaProcessingTest {

    private final ElasticsearchQueryTranslator translator = new ElasticsearchQueryTranslator();

    private static SearchCondition eq(String field, String value) {
        return new SearchCondition(field, Operator.EQ, value);
    }

    /** Spring Data's package-private processor, called reflectively: it is where a malformed criteria tree is rejected. */
    private static Object process(Criteria criteria) {
        try {
            Class<?> type = Class.forName("org.springframework.data.elasticsearch.client.elc.CriteriaQueryProcessor");
            java.lang.reflect.Method method = type.getDeclaredMethod("createQuery", Criteria.class);
            method.setAccessible(true);
            return method.invoke(null, criteria);
        } catch (java.lang.reflect.InvocationTargetException ex) {
            throw new IllegalStateException(ex.getCause());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void aFilterAndedWithAnOrganizationScopeBuildsAValidQuery() {
        SearchGroup asked = new SearchGroup(LogicOp.AND, List.of(eq("text5", "default")));
        SearchGroup scope = new SearchGroup(LogicOp.AND, List.of(eq("ownerKey", "org")));
        SearchQuery query = new SearchQuery(SearchModel.USER, new SearchGroup(LogicOp.AND, List.of(), List.of(asked, scope)));

        String json = process(translator.translate(query).getCriteria()).toString();
        assertThat(json).contains("text5", "default", "ownerKey", "org").doesNotContain("should");
    }

    @Test
    void sharedRolesOrAnOwnedRoleBuildsAValidQuery() {
        SearchGroup scope = new SearchGroup(LogicOp.OR, List.of(eq("ownerKey", "org"),
                new SearchCondition("ownerKey", Operator.EMPTY, null)));
        SearchQuery query = new SearchQuery(SearchModel.ROLE, scope);

        String json = process(translator.translate(query).getCriteria()).toString();
        assertThat(json).contains("should", "org", "ownerKey", "must_not", "exists");
    }

    @Test
    void plainAndNestedGroupsBuildValidQueries() {
        SearchGroup inner = new SearchGroup(LogicOp.OR, List.of(eq("text1", "a"), eq("text1", "b")));
        SearchQuery query = new SearchQuery(SearchModel.USER, new SearchGroup(LogicOp.AND, List.of(eq("text2", "x")), List.of(inner)));

        String json = process(translator.translate(query).getCriteria()).toString();
        assertThat(json).contains("text2", "should", "\"a\"", "\"b\"");
    }

    @Test
    void anOrGroupHoldingAGroupWithAnotherOperatorIsRejectedNotMisAnswered() {
        SearchGroup inner = new SearchGroup(LogicOp.AND, List.of(eq("text1", "a"), eq("text2", "b")));
        SearchQuery query = new SearchQuery(SearchModel.USER, new SearchGroup(LogicOp.OR, List.of(eq("text3", "c")), List.of(inner)));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> translator.translate(query))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sameOperatorGroupsAreMergedAndKeepEveryCondition() {
        SearchGroup inner = new SearchGroup(LogicOp.OR, List.of(eq("text1", "a"), eq("text1", "b")));
        SearchGroup outer = new SearchGroup(LogicOp.OR, List.of(eq("text3", "c")), List.of(inner));

        String json = process(translator.translate(new SearchQuery(SearchModel.USER, outer)).getCriteria()).toString();

        assertThat(json).contains("text3", "text1", "should", "\"a\"", "\"b\"", "\"c\"");
    }
}
