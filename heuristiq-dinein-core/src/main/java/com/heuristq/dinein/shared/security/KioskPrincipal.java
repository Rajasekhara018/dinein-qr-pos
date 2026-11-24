package com.heuristq.dinein.shared.security;

/**
 * An authenticated self-order kiosk. Deliberately not a {@link StaffPrincipal}: a kiosk has no user account and no
 * staff role, so it can only reach {@code /kiosk/**} and can never be mistaken for staff by other code.
 */
public record KioskPrincipal(Long deviceId, Long restaurantId, String deviceName) {
}
