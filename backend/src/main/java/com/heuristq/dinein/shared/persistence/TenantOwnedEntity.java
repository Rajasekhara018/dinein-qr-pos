package com.heuristq.dinein.shared.persistence;

import com.heuristq.dinein.restaurant.domain.RestaurantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/**
 * Base for entities scoped to a single restaurant. Declares the {@code tenantFilter} Hibernate filter definition;
 * concrete entities must additionally annotate themselves with
 * {@code @Filter(name = TenantOwnedEntity.TENANT_FILTER, condition = "restaurant_id = :restaurantId")} to activate
 * it (Hibernate does not honor {@code @Filter} declared only on a mapped superclass).
 *
 * <p>{@code TenantFilterInterceptor} enables this filter per-request using the authenticated {@code StaffPrincipal}'s
 * restaurantId, so a query that forgets an explicit tenant condition still cannot return another tenant's rows.
 */
@Getter
@Setter
@MappedSuperclass
@FilterDef(name = TenantOwnedEntity.TENANT_FILTER, parameters = @ParamDef(name = "restaurantId", type = Long.class))
public abstract class TenantOwnedEntity extends BaseEntity {

    public static final String TENANT_FILTER = "tenantFilter";

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId = RestaurantEntity.DEFAULT_ID;
}
