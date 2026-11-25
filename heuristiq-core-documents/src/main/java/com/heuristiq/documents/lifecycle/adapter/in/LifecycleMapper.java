package com.heuristiq.documents.lifecycle.adapter.in;

import com.heuristiq.documents.common.paging.PagedResult;
import com.heuristiq.documents.document.domain.DocumentMetadata;

import java.util.function.Function;

/** Maps stored shapes to the lifecycle response shapes. Stateless. {@link #toRow} is public so the search projection publishes exactly the row the list API returns. */
public final class LifecycleMapper {

    private LifecycleMapper() {
    }

    public static LifecycleDocumentRow toRow(DocumentMetadata m) {
        return new LifecycleDocumentRow(m.getId(), m.getOriginalFileName(), m.getCategory(), m.getDocumentType(),
                m.getOwnerId(), m.getContentType(), m.getSizeBytes(), m.getStatus(), m.getScanStatus(), m.isLegalHold(),
                m.isDeleted(), m.getRetentionUntil(), m.getCreatedAt());
    }

    static LifecycleDocumentDetail toDetail(DocumentMetadata m) {
        return new LifecycleDocumentDetail(m.getId(), m.getOriginalFileName(), m.getCategory(), m.getDocumentType(),
                m.getOwnerId(), m.getContentType(), m.getSizeBytes(), m.getStatus(), m.getScanStatus(), m.isLegalHold(),
                m.isDeleted(), m.getRetentionUntil(), m.getCreatedAt(), m.getStoredFileName(), m.getObjectKey(),
                m.getProvider(), m.getVersionId(), m.getChecksumSha256(), m.getEncryption(), m.getScanMessage(),
                m.getUploadedBy(), m.getDeletedAt(), m.getDeletedBy(), m.getLegalHoldChangedAt(),
                m.getLegalHoldChangedBy(), m.getTenantId());
    }

    static <S, T> LifecyclePageResponse<T> toPage(PagedResult<S> result, Function<S, T> mapper) {
        return new LifecyclePageResponse<>(result.content().stream().map(mapper).toList(), result.page(), result.size(),
                result.totalElements(), result.totalPages());
    }
}
