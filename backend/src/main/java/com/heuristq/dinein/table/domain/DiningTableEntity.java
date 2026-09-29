package com.heuristq.dinein.table.domain;

import com.heuristq.dinein.shared.persistence.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "dining_table")
@Filter(name = TenantOwnedEntity.TENANT_FILTER, condition = "restaurant_id = :restaurantId")
public class DiningTableEntity extends TenantOwnedEntity {

    /** Unique per restaurant (see uq_dining_table_restaurant_label), not globally. */
    @Column(nullable = false, length = 20)
    private String label;

    @Column(name = "qr_token", nullable = false, unique = true, length = 64)
    private String qrToken;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** Set means reserved until this moment; null means not reserved. Cleared automatically once it's in the past. */
    @Column(name = "reserved_until")
    private Instant reservedUntil;

    @Column(name = "reserved_note", length = 100)
    private String reservedNote;

    public boolean isReserved(Instant now) {
        return reservedUntil != null && reservedUntil.isAfter(now);
    }
}
