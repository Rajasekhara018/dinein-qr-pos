package com.railcab.dms.documenttype.support;

import com.railcab.dms.documenttype.domain.DocumentType;
import com.railcab.dms.documenttype.domain.DocumentTypeException;
import com.railcab.dms.documenttype.domain.DocumentTypeRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory fake of the store with the same observable behaviour as the Mongo and Postgres adapters: every method is
 * limited to one organization, the lock version starts at 0 and a save against an old version is a conflict, and a
 * code is unique per organization.
 */
public class InMemoryDocumentTypeRepository implements DocumentTypeRepository {

    private final Map<String, DocumentType> byId = new LinkedHashMap<>();

    @Override
    public Optional<DocumentType> findById(String tenantId, String id) {
        return Optional.ofNullable(byId.get(id)).filter(t -> t.tenantId().equals(tenantId));
    }

    @Override
    public Optional<DocumentType> findByCode(String tenantId, String code) {
        return byId.values().stream().filter(t -> t.tenantId().equals(tenantId) && t.code().equals(code)).findFirst();
    }

    @Override
    public Page list(String tenantId, Query query) {
        List<DocumentType> all = byId.values().stream()
                .filter(t -> t.tenantId().equals(tenantId))
                .filter(t -> query.category() == null || t.category().equals(query.category()))
                .filter(t -> query.flow() == null || query.flow().equals(t.flow()))
                .filter(t -> query.active() == null || query.active() == t.active())
                .sorted((a, b) -> a.code().compareTo(b.code()))
                .toList();
        int from = Math.min(query.page() * query.size(), all.size());
        int to = Math.min(from + query.size(), all.size());
        int pages = (int) Math.ceil(all.size() / (double) query.size());
        return new Page(all.subList(from, to), query.page(), query.size(), all.size(), pages);
    }

    @Override
    public DocumentType save(DocumentType type) {
        DocumentType stored = byId.get(type.id());
        if (type.version() == null) {
            if (stored != null || findByCode(type.tenantId(), type.code()).isPresent()) {
                throw DocumentTypeException.conflict("A document type with code " + type.code() + " already exists");
            }
            DocumentType inserted = type.withVersion(0L);
            byId.put(inserted.id(), inserted);
            return inserted;
        }
        if (stored == null || !stored.tenantId().equals(type.tenantId()) || !type.version().equals(stored.version())) {
            throw DocumentTypeException.conflict("The document type changed while it was being saved");
        }
        DocumentType updated = type.withVersion(type.version() + 1);
        byId.put(updated.id(), updated);
        return updated;
    }

    public int size() {
        return byId.size();
    }
}
