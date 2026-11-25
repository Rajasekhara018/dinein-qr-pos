package com.heuristiq.search.provider.elasticsearch;

import com.heuristiq.search.document.SearchDocument;
import com.heuristiq.search.elastic.ElasticController;
import com.heuristiq.search.indexing.SearchDocumentAssembler;
import com.heuristiq.search.scope.*;
import com.heuristiq.search.service.SearchIndexService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrganizationMetadataTest {

    private final SearchDocumentAssembler assembler = new SearchDocumentAssembler();

    @Test
    void organizationIdIsPersistedAsAKeywordAndSurvivesReadingTheStoredDocument() throws Exception {
        SearchDocument document = assembler.assemble("ROLE", "role-1", Map.of("tenantId", "org-a", "text1", "MANAGER"));
        ElasticsearchDocument stored = ElasticsearchDocumentMapper.toStored(document);
        MappingElasticsearchConverter converter = new MappingElasticsearchConverter(new SimpleElasticsearchMappingContext());
        converter.afterPropertiesSet();

        assertThat(converter.mapObject(stored)).containsEntry("tenantId", "org-a").containsEntry("ownerKey", "org-a");
        assertThat(ElasticsearchDocument.class.getDeclaredField("tenantId").getAnnotation(Field.class).type())
                .isEqualTo(FieldType.Keyword);
        assertThat(ElasticsearchDocumentMapper.toDocument(stored).getTenantId()).isEqualTo("org-a");
    }

    @Test
    void olderOrganizationEventsAndLegacyRowsGetExplicitOrganizationMetadata() {
        assertThat(assembler.assemble("ROLE", "1", Map.of("ownerKey", "org-a")).getTenantId()).isEqualTo("org-a");
        SearchDocument legacy = assembler.assemble("ROLE", "2", Map.of("text1", "LEGACY"));
        assertThat(legacy.getTenantId()).isEqualTo("default");
        assertThat(legacy.getOwnerKey()).isEqualTo("default");
    }

    @Test
    void perUserOwnerKeysAreNotMistakenForOrganizations() {
        SearchDocument profile = assembler.assemble("MATRIMONY_PROFILE", "p1", Map.of("ownerKey", "user-123"));
        assertThat(profile.getOwnerKey()).isEqualTo("user-123");
        assertThat(profile.getTenantId()).isNull();
    }

    @Test
    void conflictingSourceOwnershipIsRejected() {
        assertThatThrownBy(() -> assembler.assemble("ROLE", "1", Map.of("tenantId", "org-a", "ownerKey", "org-b")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("same organization");
    }

    @Test
    void configuredOrganizationModelsAlsoPersistTheirTenant() {
        TenantScopePolicy policy = new TenantScopePolicy(new TenantScopingProperties(true, null, Map.of("GROUP", TenantScoping.OWN)));
        SearchDocument document = new SearchDocumentAssembler(policy).assemble("GROUP", "g1", Map.of("ownerKey", "org-a"));
        assertThat(document.getTenantId()).isEqualTo("org-a");
    }

    @Test
    void anExistingIndexGetsTheKeywordMappingOnceBeforeWritingTenantMetadata() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        IndexOperations indexes = mock(IndexOperations.class);
        when(operations.indexOps(ElasticsearchDocument.class)).thenReturn(indexes);
        when(indexes.exists()).thenReturn(true);
        when(operations.save(any(ElasticsearchDocument.class))).thenAnswer(call -> call.getArgument(0));
        ElasticsearchSearchIndexProvider provider = new ElasticsearchSearchIndexProvider(operations, new ElasticsearchQueryTranslator());
        provider.index(assembler.assemble("ROLE", "1", Map.of("tenantId", "org-a")));
        provider.index(assembler.assemble("ROLE", "2", Map.of("tenantId", "org-b")));

        var mapping = org.mockito.ArgumentCaptor.forClass(Document.class);
        verify(indexes, times(1)).putMapping(mapping.capture());
        assertThat(mapping.getValue().get("properties")).isInstanceOfSatisfying(Map.class, properties -> {
            assertThat(properties.get("tenantId")).isEqualTo(Map.of("type", "keyword"));
            assertThat(properties.get("payload")).isEqualTo(Map.of("type", "object", "enabled", false));
        });
    }

    @Test
    void getallReturnsTheStoredOrganizationId() throws Exception {
        SearchIndexService index = mock(SearchIndexService.class);
        CurrentCaller caller = mock(CurrentCaller.class);
        when(caller.current()).thenReturn(new CallerScope("org-a", false));
        when(index.search(any(), any())).thenReturn(new PageImpl<>(List.of(
                assembler.assemble("ROLE", "1", Map.of("tenantId", "org-a", "text1", "MANAGER"))), org.springframework.data.domain.PageRequest.of(0, 10), 1));
        var controller = new ElasticController(new TenantScopedSearch(index, caller, new TenantScopePolicy(TenantScopingProperties.defaults())));

        MockMvcBuilders.standaloneSetup(controller).build().perform(
                        post("/api/v1/search/elastic/ROLE/getall").contentType(MediaType.APPLICATION_JSON).content("{\"requestType\":\"LIST\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object.content[0].tenantId").value("org-a"));
    }
}
