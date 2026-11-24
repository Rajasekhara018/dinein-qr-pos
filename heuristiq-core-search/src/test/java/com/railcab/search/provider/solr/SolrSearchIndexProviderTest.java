package com.heuristiq.search.provider.solr;

import com.heuristiq.search.document.SearchDocument;
import com.heuristiq.search.model.SearchModel;
import com.heuristiq.search.query.LogicOp;
import com.heuristiq.search.query.Operator;
import com.heuristiq.search.query.SearchCondition;
import com.heuristiq.search.query.SearchGroup;
import com.heuristiq.search.query.SearchQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The Solr provider against a mock Solr: what it sends and how it reads the answers. */
class SolrSearchIndexProviderTest {

    private static final String SOLR = "http://solr.test:8983/solr";

    private MockRestServiceServer solr;
    private SolrSearchIndexProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        solr = MockRestServiceServer.bindTo(builder).build();
        provider = new SolrSearchIndexProvider(builder.baseUrl(new SolrProperties(SOLR, "railcab-search", null, null).collectionUrl()).build(), new SolrQueryTranslator());
    }

    @Test
    void indexPostsTheFlatDocumentAndCommits() {
        solr.expect(requestTo(SOLR + "/railcab-search/update?commit=true"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$[0].id").value("application:abc"))
                .andExpect(jsonPath("$[0].text1_s").value("SAVED"))
                .andExpect(jsonPath("$[0].text1_t").value("SAVED"))
                .andExpect(jsonPath("$[0].date1_dt").value("2026-10-11T18:30:00Z"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        SearchDocument document = SearchDocument.builder().id("application:abc").model("APPLICATION").modelKey("abc")
                .text1("SAVED").date1(Instant.parse("2026-10-11T18:30:00Z")).build();

        assertThat(provider.index(document)).isSameAs(document);
        solr.verify();
    }

    @Test
    void deleteUsesTheCompositeId() {
        solr.expect(requestTo(SOLR + "/railcab-search/update?commit=true"))
                .andExpect(content().json("{\"delete\":{\"id\":\"application:abc\"}}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        provider.delete("APPLICATION", "abc");

        solr.verify();
    }

    @Test
    void findOneReadsTheDocumentBackAndAnUnknownIdIsNull() {
        solr.expect(requestTo(SOLR + "/railcab-search/get?id=application:abc"))
                .andRespond(withSuccess("{\"doc\":{\"id\":\"application:abc\",\"model_s\":\"APPLICATION\",\"modelKey_s\":\"abc\","
                        + "\"text1_s\":\"SAVED\",\"text1_t\":[\"SAVED\"],\"amount1_d\":12.5,\"number1_l\":7,\"date1_dt\":\"2026-10-11T18:30:00Z\"}}",
                        MediaType.APPLICATION_JSON));
        solr.expect(requestTo(SOLR + "/railcab-search/get?id=application:none"))
                .andRespond(withSuccess("{\"doc\":null}", MediaType.APPLICATION_JSON));

        SearchDocument found = provider.findOne("APPLICATION", "abc");

        assertThat(found.getModelKey()).isEqualTo("abc");
        assertThat(found.getText1()).isEqualTo("SAVED");
        assertThat(found.getAmount1()).isEqualTo(12.5);
        assertThat(found.getNumber1()).isEqualTo(7L);
        assertThat(found.getDate1()).isEqualTo(Instant.parse("2026-10-11T18:30:00Z"));
        assertThat(provider.findOne("APPLICATION", "none")).isNull();
    }

    @Test
    void searchSendsTheTranslatedQueryPageAndSortAndMapsTheAnswerToAPage() {
        solr.expect(method(HttpMethod.GET))
                .andExpect(request -> {
                    var params = org.springframework.web.util.UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
                    java.util.function.Function<String, List<String>> decoded = name -> params.getOrDefault(name, List.of()).stream()
                            .map(v -> org.springframework.web.util.UriUtils.decode(v, java.nio.charset.StandardCharsets.UTF_8)).toList();
                    assertThat(decoded.apply("q")).containsExactly("*:*");
                    assertThat(decoded.apply("start")).containsExactly("25");
                    assertThat(decoded.apply("rows")).containsExactly("25");
                    assertThat(decoded.apply("sort")).containsExactly("date1_dt desc");
                    assertThat(decoded.apply("fq")).containsExactly("model_s:\"APPLICATION\"", "(text2_s:*pl*)");
                })
                .andRespond(withSuccess("{\"response\":{\"numFound\":417,\"docs\":["
                        + "{\"id\":\"application:a\",\"model_s\":\"APPLICATION\",\"modelKey_s\":\"a\",\"text2_s\":\"plokij\"}]}}",
                        MediaType.APPLICATION_JSON));

        Page<SearchDocument> page = provider.search(new SearchQuery(SearchModel.APPLICATION,
                        new SearchGroup(LogicOp.AND, List.of(new SearchCondition("text2", Operator.LIKE, "pl")))),
                PageRequest.of(1, 25, Sort.by(Sort.Order.desc("date1"))));

        assertThat(page.getTotalElements()).isEqualTo(417);
        assertThat(page.getContent()).singleElement().satisfies(d -> assertThat(d.getText2()).isEqualTo("plokij"));
        solr.verify();
    }
}
