package com.heuristiq.search.elastic;

import com.heuristiq.search.document.SearchDocument;
import com.heuristiq.search.query.SearchQuery;
import com.heuristiq.search.scope.CallerScope;
import com.heuristiq.search.scope.CurrentCaller;
import com.heuristiq.search.scope.TenantScopePolicy;
import com.heuristiq.search.scope.TenantScopedSearch;
import com.heuristiq.search.scope.TenantScopingProperties;
import com.heuristiq.search.service.SearchIndexService;
import com.heuristiq.search.service.SearchIndexUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

class ElasticControllerTest {

    private SearchIndexService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(SearchIndexService.class);
        CurrentCaller caller = mock(CurrentCaller.class);
        when(caller.current()).thenReturn(new CallerScope("org-1", false));
        mvc = MockMvcBuilders.standaloneSetup(new ElasticController(new TenantScopedSearch(service, caller, new TenantScopePolicy(TenantScopingProperties.defaults())))).build();
    }

    private static SearchDocument application() {
        return SearchDocument.builder().id("application:abc").model("APPLICATION").modelKey("abc")
                .text1("BOARDED").text2("810139931748449440").text4("")
                .date1(Instant.ofEpochMilli(1791916200000L)).build();
    }

    @Test
    void listsTheRecordsOfAModelInTheClientEnvelopeAndShape() throws Exception {
        when(service.search(any(), any())).thenAnswer(inv -> new PageImpl<>(List.of(application()), inv.<Pageable>getArgument(1), 417));

        mvc.perform(post("/api/v1/search/elastic/application/getall").contentType(MediaType.APPLICATION_JSON).content("""
                        {"messageID":"/api/idx/application/getall","requestType":"LIST","pageNo":0,"pageSize":"25",
                         "sort":{"field1":"date1","directionField1":"DESC"}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestType").value("LIST"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.object.totalElements").value(417))
                .andExpect(jsonPath("$.object.size").value(25))
                .andExpect(jsonPath("$.object.content[0].id").value("application-abc"))
                .andExpect(jsonPath("$.object.content[0].type").value("APPLICATION"))
                .andExpect(jsonPath("$.object.content[0].modelKey").value("abc"))
                .andExpect(jsonPath("$.object.content[0].allianceOrgKey").doesNotExist())
                .andExpect(jsonPath("$.object.content[0].text1").value("BOARDED"))
                .andExpect(jsonPath("$.object.content[0].text4").value(""))
                .andExpect(jsonPath("$.object.content[0].date1").value(1791916200000L))
                .andExpect(jsonPath("$.object.content[0].text3").doesNotExist());
    }

    @Test
    void aSearchPassesItsNestedFilterAndSortToTheIndex() throws Exception {
        when(service.search(any(), any())).thenAnswer(inv -> new PageImpl<SearchDocument>(List.of(), inv.<Pageable>getArgument(1), 0));

        mvc.perform(post("/api/v1/search/elastic/application/getall").contentType(MediaType.APPLICATION_JSON).content("""
                        {"messageID":"/api/idx/application/getall","requestType":"LIST","searchQuery":{"group":{"criteria1":0,"group":[
                          {"group":[{"field":"text2","operator":"LIKE","value":"[;plokij"}],"criteria1":0}]}},
                         "pageNo":0,"pageSize":"25","sort":{"field1":"date1","directionField1":"DESC"}}"""))
                .andExpect(status().isOk());

        ArgumentCaptor<SearchQuery> query = ArgumentCaptor.forClass(SearchQuery.class);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(service).search(query.capture(), page.capture());
        assertThat(query.getValue().group().groups().get(0).conditions().get(0).value()).isEqualTo("[;plokij");
        assertThat(page.getValue().getPageSize()).isEqualTo(25);
    }

    @Test
    void aBadRequestIsAnsweredInTheSameEnvelopeWithTheReason() throws Exception {
        mvc.perform(post("/api/v1/search/elastic/nothing/getall").contentType(MediaType.APPLICATION_JSON).content("{\"requestType\":\"LIST\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0]").value("Unknown model: nothing"))
                .andExpect(jsonPath("$.object").doesNotExist());

        mvc.perform(post("/api/v1/search/elastic/application/getall").contentType(MediaType.APPLICATION_JSON).content("{\"requestType\":\"SAVE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("Unsupported requestType: SAVE"));

        mvc.perform(post("/api/v1/search/elastic/application/getall").contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void anUnavailableIndexIsA503InTheSameEnvelope() throws Exception {
        when(service.search(any(), any())).thenThrow(new SearchIndexUnavailableException("down", new RuntimeException()));

        mvc.perform(post("/api/v1/search/elastic/application/getall").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));
    }
}
