package com.heuristq.dinein.table.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DiningTableRepository extends JpaRepository<DiningTableEntity, Long> {

    Optional<DiningTableEntity> findByQrToken(String qrToken);

    boolean existsByLabelIgnoreCase(String label);

    boolean existsByLabelIgnoreCaseAndIdNot(String label, Long id);

    List<DiningTableEntity> findAllByOrderByLabelAsc();
}
