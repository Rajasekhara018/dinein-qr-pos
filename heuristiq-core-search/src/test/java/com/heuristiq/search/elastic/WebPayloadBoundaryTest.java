package com.heuristiq.search.elastic;

import com.heuristiq.search.document.SearchDocument;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class WebPayloadBoundaryTest {
    @Test void webListsDoNotExposeIndexOnlySearchMetadata() {
        var document=new SearchDocument();document.setModel("WEB_DMS_LIFECYCLE_DOCUMENT");document.setModelKey("record-1");
        document.setText13("index-only-checksum");document.setText14("internal-owner");
        document.setPayload(Map.of("id","document-1","originalFileName","proof.pdf"));
        var result=ElasticDocument.of(document);
        assertThat(result.slots()).containsOnlyKeys("payload");
        assertThat(result.slots().get("payload")).isEqualTo(document.getPayload());
    }
    @Test void existingUamSlotResponsesRemainAvailable() {
        var document=new SearchDocument();document.setModel("USER");document.setModelKey("user-1");document.setText1("User");
        assertThat(ElasticDocument.of(document).slots()).containsEntry("text1","User");
    }
}
