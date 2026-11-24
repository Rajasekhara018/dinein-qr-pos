package com.heuristiq.search.provider.solr;

import com.heuristiq.search.document.SearchDocument;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OrganizationMetadataSolrTest {
    @Test
    void tenantMetadataSurvivesTheSolrRoundTrip() {
        SearchDocument document = SearchDocument.builder().id("role:1").model("ROLE").modelKey("1")
                .ownerKey("org-a").tenantId("org-a").build();
        var stored = SolrDocumentMapper.toSolr(document);
        assertThat(stored).containsEntry("tenantId_s", "org-a");
        assertThat(SolrDocumentMapper.fromSolr(stored).getTenantId()).isEqualTo("org-a");
    }
}
