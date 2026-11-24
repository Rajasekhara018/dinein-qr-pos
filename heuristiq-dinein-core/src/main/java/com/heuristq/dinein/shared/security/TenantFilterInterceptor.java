package com.heuristq.dinein.shared.security;

import com.heuristq.dinein.shared.persistence.TenantOwnedEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.Session;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Activates the {@link TenantOwnedEntity#TENANT_FILTER} Hibernate filter for the current request using the
 * authenticated {@link StaffPrincipal}'s restaurantId, so every tenant-owned entity load/query is structurally
 * confined to that restaurant even if a repository query forgets an explicit restaurantId condition.
 *
 * <p>Runs as an MVC {@link HandlerInterceptor} rather than a servlet {@code Filter} because it needs the
 * request-scoped {@link EntityManager}, which (via {@code spring.jpa.open-in-view}) is only bound to the thread
 * once the servlet filter chain -- including {@code OpenEntityManagerInViewFilter} -- has finished running.
 *
 * <p>No-op for unauthenticated requests (login, public/guest, webhooks, platform) -- those either don't touch
 * tenant-owned entities under an authenticated principal, or resolve their own restaurantId explicitly.
 */
@Component
public class TenantFilterInterceptor implements HandlerInterceptor {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long restaurantId = currentRestaurantId();
        if (restaurantId != null) {
            entityManager.unwrap(Session.class)
                    .enableFilter(TenantOwnedEntity.TENANT_FILTER)
                    .setParameter("restaurantId", restaurantId);
        }
        return true;
    }

    @Nullable
    private static Long currentRestaurantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof StaffPrincipal staff) {
            return staff.restaurantId();
        }
        if (principal instanceof KioskPrincipal kiosk) {
            return kiosk.restaurantId();
        }
        return null;
    }
}
