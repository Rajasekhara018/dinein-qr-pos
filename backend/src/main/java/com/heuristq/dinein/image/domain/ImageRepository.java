package com.heuristq.dinein.image.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ImageRepository extends JpaRepository<ImageEntity, Long> {

    interface ImageMeta {
        Long getId();

        String getContentType();

        String getSha256();

        Integer getWidth();

        Integer getHeight();
    }

    interface ImageBytes {
        String getContentType();

        String getSha256();

        byte[] getBytes();
    }

    @Query("select i.id as id, i.contentType as contentType, i.sha256 as sha256, i.width as width, i.height as height "
            + "from ImageEntity i where i.id = :id")
    Optional<ImageMeta> findMetaById(@Param("id") Long id);

    @Query("select i.id as id, i.contentType as contentType, i.sha256 as sha256, i.width as width, i.height as height "
            + "from ImageEntity i where i.sha256 = :sha256 order by i.id")
    List<ImageMeta> findMetaBySha256(@Param("sha256") String sha256);

    @Query("select i.contentType as contentType, i.sha256 as sha256, i.data as bytes from ImageEntity i where i.id = :id")
    Optional<ImageBytes> findMainBytes(@Param("id") Long id);

    @Query("select i.contentType as contentType, i.sha256 as sha256, i.thumbnail as bytes from ImageEntity i where i.id = :id")
    Optional<ImageBytes> findThumbBytes(@Param("id") Long id);

    /** Deletes images no category, item or settings row points at, keeping recent uploads that may still be linked. */
    @Modifying
    @Query(value = """
            DELETE FROM image i
            WHERE i.created_at < :cutoff
              AND NOT EXISTS (SELECT 1 FROM category c WHERE c.image_id = i.id)
              AND NOT EXISTS (SELECT 1 FROM item it WHERE it.image_id = i.id)
              AND NOT EXISTS (SELECT 1 FROM restaurant_settings s WHERE s.logo_image_id = i.id)
            """, nativeQuery = true)
    int deleteOrphansCreatedBefore(@Param("cutoff") Instant cutoff);
}
