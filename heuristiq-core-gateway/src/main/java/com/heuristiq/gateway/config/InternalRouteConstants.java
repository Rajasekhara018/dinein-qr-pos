package com.heuristiq.gateway.config;

import java.util.regex.Pattern;

/**
 * The service-token-only ({@code /internal}) paths of the services that have no route-specific pattern of their own:
 * Config ({@code /api/v1/config/internal/**}), NMS and Reports ({@code /api/v1/<service>/internal/**}), and the shared
 * {@code /api/v1/internal/**} namespace of Pricing, Location, Ride, Dispatch, Customer, Payment and Onboarding, plus
 * AdminAPI's unversioned {@code /api/internal/**}. No gateway route matches those paths directly, but a dot segment
 * ({@code /api/v1/ride/../internal/...}) or an encoded one would still be forwarded under a service's prefix; the
 * pattern is matched against the normalised path (see {@code InternalPathGuard}), so every spelling answers 404.
 *
 * <p>Shape: {@code /api}, an optional version, at most one service segment, an optional version, then {@code internal}.
 */
public final class InternalRouteConstants {

    public static final Pattern SERVICE_INTERNAL_PATH =
            Pattern.compile("^/api/(?:v\\d+/)?(?:[^/]+/)?(?:v\\d+/)?internal(/|$)");

    private InternalRouteConstants() {
    }
}
