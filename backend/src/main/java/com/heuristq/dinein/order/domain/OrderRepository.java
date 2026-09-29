package com.heuristq.dinein.order.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long>, JpaSpecificationExecutor<OrderEntity> {

    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdAndGuestSessionId(Long id, String guestSessionId);

    /**
     * Used to decide whether a table is currently occupied by a self-service guest (see
     * {@code PublicSessionController}). Staff-placed orders (no {@code guestSessionId}) don't count: a staff member
     * is already present at the table, so there's no risk of two unattended guest sessions colliding there.
     */
    boolean existsByTableIdAndStatusInAndGuestSessionIdIsNotNull(Long tableId, Collection<OrderStatus> statuses);

    /** Row lock used by every status change so concurrent verify/webhook/kitchen calls serialise. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id")
    Optional<OrderEntity> findByIdForUpdate(@Param("id") Long id);

    List<OrderEntity> findByGuestSessionIdOrderByPlacedAtDesc(String guestSessionId);

    Optional<OrderEntity> findByIdAndRestaurantId(Long id, Long restaurantId);

    List<OrderEntity> findByRestaurantIdAndStatusInOrderByPaidAtAsc(Long restaurantId, Collection<OrderStatus> statuses);

    @Query("select o.id from OrderEntity o where o.status = :status and o.placedAt < :cutoff "
            + "and o.paymentFlagged = false order by o.placedAt")
    List<Long> findIdsByStatusPlacedBefore(@Param("status") OrderStatus status, @Param("cutoff") Instant cutoff);

    Page<OrderEntity> findByPlacedAtGreaterThanEqualAndPlacedAtLessThan(Instant from, Instant to, Pageable pageable);

    List<OrderEntity> findByPlacedAtGreaterThanEqualAndPlacedAtLessThanOrderByPlacedAtAsc(Instant from, Instant to);

    /** Table ids currently in use (see {@link OrderStatus#OCCUPIES_TABLE}), for the admin floor view. */
    @Query("select distinct o.tableId from OrderEntity o "
            + "where o.restaurantId = :restaurantId and o.status in :statuses and o.tableId is not null")
    List<Long> findOccupiedTableIds(@Param("restaurantId") Long restaurantId,
                                    @Param("statuses") Collection<OrderStatus> statuses);

    /**
     * GDPR data minimisation: clears guest-identifying fields from terminal orders once they're older than the
     * configured retention period. Amounts, status and timestamps are kept -- only who the guest was is erased.
     */
    @Modifying(clearAutomatically = true)
    @Query("update OrderEntity o set o.customerName = null, o.customerPhone = null, o.notes = null "
            + "where o.status in :statuses and o.placedAt < :cutoff "
            + "and (o.customerName is not null or o.customerPhone is not null or o.notes is not null)")
    int redactGuestPiiOlderThan(@Param("statuses") Collection<OrderStatus> statuses, @Param("cutoff") Instant cutoff);
}
