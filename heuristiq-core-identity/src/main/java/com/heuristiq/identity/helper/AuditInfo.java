package com.heuristiq.identity.helper;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class AuditInfo {

    /**
     * Whether deleted.
     */
    @Indexed
    private Boolean deleted;

    /**
     * Created At.
     */
    @Indexed
    private LocalDateTime createdAt;

    /**
     * Created By.
     */
    private String createdBy;

    /**
     * Updated At.
     */
    private LocalDateTime updatedAt;

    /**
     * Updated By.
     */
    private String updatedBy;

        /**
     * Soft delete timestamp.
     */
    private LocalDateTime deletedAt;

    /**
     * Deleted by.
     */
    private String deletedBy;

    /**
     * Optimistic Locking.
     */
    @Version
    private Long version;
}
