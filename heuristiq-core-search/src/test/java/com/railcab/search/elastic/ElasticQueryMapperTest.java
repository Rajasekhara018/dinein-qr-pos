package com.heuristiq.search.elastic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.search.model.SearchModel;
import com.heuristiq.search.query.LogicOp;
import com.heuristiq.search.query.Operator;
import com.heuristiq.search.query.SearchQuery;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElasticQueryMapperTest {

    private final ObjectMapper json = new ObjectMapper();

    private ElasticGetAllRequest read(String body) throws Exception {
        return json.readValue(body, ElasticGetAllRequest.class);
    }

    @Test
    void franchiseLocationListingAcceptsModelKeyAsTheThirdSortField() throws Exception {
        Pageable page = ElasticQueryMapper.toPageable(read("""
                {"requestType":"LIST","pageNo":0,"pageSize":20,"sort":{
                  "field1":"number1","directionField1":"ASC",
                  "field2":"text1","directionField2":"ASC",
                  "field3":"modelKey","directionField3":"ASC"}}
                """));

        assertThat(page.getPageNumber()).isZero();
        assertThat(page.getPageSize()).isEqualTo(20);
        assertThat(page.getSort().toList()).containsExactly(
                Sort.Order.asc("number1"), Sort.Order.asc("text1"), Sort.Order.asc("modelKey"));
        assertThat(ElasticQueryMapper.toPageable(read("""
                {"sort":{"field1":"modelKey","directionField1":"DESC"}}
                """)).getSort().toList()).containsExactly(Sort.Order.desc("modelKey"));
    }

    @Test
    void aListingWithoutSearchQueryHasNoFilterAndTheRequestedPageAndSort() throws Exception {
        ElasticGetAllRequest request = read("""
                {"messageID":"/api/idx/application/getall","requestType":"LIST","pageNo":0,"pageSize":"25",
                 "sort":{"field1":"date1","directionField1":"DESC"}}""");

        SearchQuery query = ElasticQueryMapper.toSearchQuery(SearchModel.APPLICATION, request);
        Pageable page = ElasticQueryMapper.toPageable(request);

        assertThat(query.model()).isEqualTo(SearchModel.APPLICATION);
        assertThat(query.group()).isNull();
        assertThat(page.getPageNumber()).isZero();
        assertThat(page.getPageSize()).isEqualTo(25);
        assertThat(page.getSort().getOrderFor("date1").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void aSearchBecomesNestedGroupsWithTheirOwnLogic() throws Exception {
        ElasticGetAllRequest request = read("""
                {"requestType":"LIST","searchQuery":{"group":{"criteria1":0,"group":[
                   {"group":[{"field":"text2","operator":"LIKE","value":"[;plokij"}],"criteria1":0}]}},
                 "pageNo":0,"pageSize":"25","sort":{"field1":"date1","directionField1":"DESC"}}""");

        SearchQuery query = ElasticQueryMapper.toSearchQuery(SearchModel.APPLICATION, request);

        assertThat(query.group().logic()).isEqualTo(LogicOp.AND);
        assertThat(query.group().conditions()).isEmpty();
        assertThat(query.group().groups()).hasSize(1);
        var inner = query.group().groups().get(0);
        assertThat(inner.logic()).isEqualTo(LogicOp.AND);
        assertThat(inner.conditions()).singleElement().satisfies(c -> {
            assertThat(c.field()).isEqualTo("text2");
            assertThat(c.operator()).isEqualTo(Operator.LIKE);
            assertThat(c.value()).isEqualTo("[;plokij");
        });
    }

    @Test
    void criteriaOneOfOneIsOrAndConditionsMayShareAGroupWithSubGroups() throws Exception {
        ElasticGetAllRequest request = read("""
                {"requestType":"LIST","searchQuery":{"group":{"criteria1":0,"group":[
                   {"field":"text1","operator":"eq","value":"SAVED"},
                   {"criteria1":1,"group":[{"field":"text14","operator":"EQ","value":"MERWEB"},{"field":"text14","operator":"EQ","value":"MERAPP"}]}]}}}""");

        var group = ElasticQueryMapper.toSearchQuery(SearchModel.APPLICATION, request).group();

        assertThat(group.conditions()).singleElement().satisfies(c -> assertThat(c.operator()).isEqualTo(Operator.EQ));
        assertThat(group.groups()).singleElement().satisfies(g -> {
            assertThat(g.logic()).isEqualTo(LogicOp.OR);
            assertThat(g.conditions()).hasSize(2);
        });
    }

    @Test
    void aSortKeepsTheLogicalSlotNameAndNoSortMeansNewestFirst() throws Exception {
        Pageable sorted = ElasticQueryMapper.toPageable(read("{\"sort\":{\"field1\":\"text3\",\"directionField1\":\"asc\",\"field2\":\"date1\"}}"));
        assertThat(sorted.getSort().getOrderFor("text3").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(sorted.getSort().getOrderFor("date1").getDirection()).isEqualTo(Sort.Direction.ASC);

        Pageable unsorted = ElasticQueryMapper.toPageable(read("{}"));
        assertThat(unsorted.getPageSize()).isEqualTo(ElasticConstants.DEFAULT_PAGE_SIZE);
        assertThat(unsorted.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void thePageSizeDefaultsToTenAndIsCappedAtFifty() throws Exception {
        assertThat(ElasticQueryMapper.toPageable(read("{}")).getPageSize()).isEqualTo(10);
        assertThat(ElasticQueryMapper.toPageable(read("{\"pageSize\":50}")).getPageSize()).isEqualTo(50);
        assertThatThrownBy(() -> ElasticQueryMapper.toPageable(read("{\"pageSize\":51}"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ElasticQueryMapper.toPageable(read("{\"pageSize\":0}"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theModelComesFromThePathIgnoringCaseDashesAndUnderscores() {
        assertThat(ElasticQueryMapper.modelOf("application")).isEqualTo(SearchModel.APPLICATION);
        assertThat(ElasticQueryMapper.modelOf("matrimony-profile")).isEqualTo(SearchModel.MATRIMONY_PROFILE);
        assertThat(ElasticQueryMapper.modelOf("API_CLIENT")).isEqualTo(SearchModel.API_CLIENT);
        assertThat(ElasticQueryMapper.modelOf("report")).isEqualTo(SearchModel.REPORT);
        assertThat(ElasticQueryMapper.modelOf("report-configuration")).isEqualTo(SearchModel.REPORT_CONFIGURATION);
        assertThat(ElasticQueryMapper.modelOf("report-system-config")).isEqualTo(SearchModel.REPORT_SYSTEM_CONFIG);
        assertThat(ElasticQueryMapper.modelOf("report-custom-field")).isEqualTo(SearchModel.REPORT_CUSTOM_FIELD);
        assertThat(ElasticQueryMapper.modelOf("report-template")).isEqualTo(SearchModel.REPORT_TEMPLATE);
        assertThat(ElasticQueryMapper.modelOf("report-audit-log")).isEqualTo(SearchModel.REPORT_AUDIT_LOG);
        assertThat(ElasticQueryMapper.modelOf("report-merchant")).isEqualTo(SearchModel.REPORT_MERCHANT);
        assertThatThrownBy(() -> ElasticQueryMapper.modelOf("nope")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void badRequestsAreRefusedWithAReason() throws Exception {
        assertThatThrownBy(() -> ElasticQueryMapper.toPageable(read("{\"pageSize\":\"500\"}"))).hasMessageContaining("pageSize");
        assertThatThrownBy(() -> ElasticQueryMapper.toPageable(read("{\"pageNo\":-1}"))).hasMessageContaining("pageNo");
        assertThatThrownBy(() -> ElasticQueryMapper.toPageable(read("{\"sort\":{\"field1\":\"password\"}}"))).hasMessageContaining("Cannot sort");
        assertThatThrownBy(() -> ElasticQueryMapper.toPageable(read("{\"sort\":{\"field1\":\"date1\",\"directionField1\":\"UP\"}}"))).hasMessageContaining("direction");
        assertThatThrownBy(() -> ElasticQueryMapper.toSearchQuery(SearchModel.USER, read(
                "{\"searchQuery\":{\"group\":{\"criteria1\":7,\"group\":[{\"field\":\"text1\",\"operator\":\"EQ\",\"value\":\"x\"}]}}}"))).hasMessageContaining("criteria1");
        assertThatThrownBy(() -> ElasticQueryMapper.toSearchQuery(SearchModel.USER, read(
                "{\"searchQuery\":{\"group\":{\"group\":[{\"field\":\"text1\",\"operator\":\"CONTAINS\",\"value\":\"x\"}]}}}"))).hasMessageContaining("operator");
    }
}
