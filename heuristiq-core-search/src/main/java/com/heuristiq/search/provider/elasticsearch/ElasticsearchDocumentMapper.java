package com.heuristiq.search.provider.elasticsearch;

import com.heuristiq.search.document.SearchDocument;
import org.springframework.beans.BeanUtils;

/**
 * Copies between the engine-neutral {@link SearchDocument} and its Elasticsearch storage form. Both classes carry the
 * same properties, so the copy is by name; a test keeps the two in step when a slot is added.
 */
final class ElasticsearchDocumentMapper {

    private ElasticsearchDocumentMapper() {
    }

    static ElasticsearchDocument toStored(SearchDocument document) {
        ElasticsearchDocument stored = new ElasticsearchDocument();
        BeanUtils.copyProperties(document, stored);
        return stored;
    }

    static SearchDocument toDocument(ElasticsearchDocument stored) {
        if (stored == null) {
            return null;
        }
        SearchDocument document = new SearchDocument();
        BeanUtils.copyProperties(stored, document);
        return document;
    }
}
