package com.heuristq.dinein.menu.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ItemRepository extends JpaRepository<ItemEntity, Long>, JpaSpecificationExecutor<ItemEntity> {

    interface CategoryCount {
        Long getCategoryId();

        long getCount();
    }

    @Query("select i.categoryId as categoryId, count(i) as count from ItemEntity i where i.active = true group by i.categoryId")
    List<CategoryCount> countActiveByCategory();

    List<ItemEntity> findByActiveTrueAndCategoryIdInOrderByDisplayOrderAscNameAsc(Collection<Long> categoryIds);

    boolean existsByCategoryIdAndNameIgnoreCaseAndIdNot(Long categoryId, String name, Long id);

    boolean existsByCategoryIdAndNameIgnoreCase(Long categoryId, String name);

    @Query("select coalesce(max(i.displayOrder), 0) from ItemEntity i where i.categoryId = :categoryId")
    int maxDisplayOrder(@Param("categoryId") Long categoryId);

    /** Loads items with their variants for order pricing; addons are batch-fetched. */
    @Query("select distinct i from ItemEntity i left join fetch i.variants where i.id in :ids")
    List<ItemEntity> findAllWithVariants(@Param("ids") Collection<Long> ids);
}
