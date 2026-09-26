package com.heuristq.dinein.order.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long>, JpaSpecificationExecutor<OrderEntity> {

    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdAndGuestSessionId(Long id, String guestSessionId);

    /** Row lock used by every status change so concurrent verify/webhook/kitchen calls serialise. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id")
    Optional<OrderEntity> findByIdForUpdate(@Param("id") Long id);

    List<OrderEntity> findByGuestSessionIdOrderByPlacedAtDesc(String guestSessionId);

    List<OrderEntity> findByStatusInOrderByPaidAtAsc(Collection<OrderStatus> statuses);

    @Query("select o.id from OrderEntity o where o.status = :status and o.placedAt < :cutoff "
            + "and o.paymentFlagged = false order by o.placedAt")
    List<Long> findIdsByStatusPlacedBefore(@Param("status") OrderStatus status, @Param("cutoff") Instant cutoff);

    Page<OrderEntity> findByPlacedAtGreaterThanEqualAndPlacedAtLessThan(Instant from, Instant to, Pageable pageable);

    List<OrderEntity> findByPlacedAtGreaterThanEqualAndPlacedAtLessThanOrderByPlacedAtAsc(Instant from, Instant to);
}
