package com.heuristq.dinein.image.domain;

import com.heuristq.dinein.shared.persistence.BaseEntity;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * The only entity that maps image bytes. Menu entities reference images by id, so menu queries never touch BYTEA.
 * Serving goes through projections in {@link ImageRepository}; this entity is only used for inserts.
 */
@Getter
@Setter
@Entity
@Table(name = "image")
public class ImageEntity extends BaseEntity {

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] thumbnail;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    private Integer width;

    private Integer height;

    @Column(nullable = false, length = 64, columnDefinition = "bpchar(64)")
    private String sha256;
}
