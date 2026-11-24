package com.heuristq.dinein.kiosk.domain;

import com.heuristq.dinein.shared.persistence.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * How a restaurant's kiosk looks. Every field except {@code kioskEnabled} is optional: null means "use the app's
 * built-in default", so a restaurant with no row (or a half-filled one) still gets a working welcome page.
 */
@Getter
@Setter
@Entity
@Table(name = "kiosk_branding")
@Filter(name = TenantOwnedEntity.TENANT_FILTER, condition = "restaurant_id = :restaurantId")
public class KioskBrandingEntity extends TenantOwnedEntity {

    /** Kill switch: when false the kiosk refuses new orders (staff can still take them at the counter). */
    @Column(name = "kiosk_enabled", nullable = false)
    private boolean kioskEnabled = true;

    @Column(name = "primary_color", length = 7)
    private String primaryColor;

    @Column(name = "secondary_color", length = 7)
    private String secondaryColor;

    @Column(length = 80)
    private String headline;

    @Column(length = 120)
    private String subtext;

    @Column(name = "start_button_label", length = 40)
    private String startButtonLabel;

    @Column(name = "idle_timeout_seconds")
    private Integer idleTimeoutSeconds;

    @Column(name = "logo_image_id")
    private Long logoImageId;

    @Column(name = "background_image_id")
    private Long backgroundImageId;
}
