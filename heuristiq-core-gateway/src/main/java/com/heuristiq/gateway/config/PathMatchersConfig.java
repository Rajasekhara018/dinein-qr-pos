package com.heuristiq.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Stream;

/**
 * Maps pathmatchers.* from application.yml to typed fields.
 * Each field is a URL pattern that requires no authentication.
 * Pre-expanded by the filter into exact + /** variants (same as heuristq pattern).
 */
@Data
@Component
@ConfigurationProperties(prefix = "pathmatchers")
public class PathMatchersConfig {


    // ── Admin API ────────────────────────────────────────────────────────────
    /** Public vendor registration + document upload (PublicVendorController) -- no account exists
     *  yet at this point, so no JWT is available. */
    private String adminVendorPublic;

    // ── Bookings API ─────────────────────────────────────────────────────────
    private String bookingsHealth;

    // ── IRCTC API ────────────────────────────────────────────────────────────
    private String irctcHealth;
    private String irctcSimulator;

    /** The single auth surface for every UserType (CUSTOMER, DRIVER, VENDOR, ADMIN, ...) —
     *  IdentityAuthController's /identity/auth/** (login, register, OTP, google, refresh, logout). */
    private String uamIdentityAuth;

    /** Same auth surface on the versioned path shapes RouteConfig's uam-auth-api route accepts:
     *  /api/v1/uam/identity/auth/** and /api/uam/v1/identity/auth/** respectively. */
    private String uamIdentityAuthVersioned;
    private String uamIdentityAuthVersionAfter;

    // ── Payment API ──────────────────────────────────────────────────────────
    /** Gateway success/failure/webhook postbacks (Razorpay/PayU/Toucan) — no user JWT available. */
    private String paymentCallback;

    // ── Rider PaymentService (extracted microservice) ───────────────────────
    /** Razorpay webhook postback into Rider_PaymentService — no user JWT available, same
     *  reasoning as paymentCallback above; HMAC signature verification happens inside the
     *  handler instead (see Rider_PaymentService's own SecurityConfig). */
    private String riderPaymentWebhook;

    /** Razorpay wallet top-up webhook postback into Rider_PaymentService — same reasoning as
     *  riderPaymentWebhook above. */
    private String riderWalletWebhook;


    /** WebSocket HTTP upgrade is anonymous; RideService authenticates the STOMP CONNECT frame. */
    private String riderRideWebsocket;


    /** Matrimony STOMP socket: the HTTP upgrade is anonymous (browsers cannot set headers on it);
     *  the matrimony API authenticates the STOMP CONNECT frame with the user's UAM token. */
    private String matrimonyWebsocket;

    /** Matrimony sign-up switches (GET only: is "Create account" offered, how plans behave). The sign-in page reads them
     *  before anyone has a token; the answer is the same for every caller and holds nothing secret. */
    private String matrimonySelfRegistrationConfig;

    /** Matrimony reference lists (religion, country, state, city, ...) the public sign-up form reads before login (GET only,
     *  read-only lists, nothing personal). */
    private String matrimonyPublicConfigOptions;

    /** Food STOMP socket: the food API checks who connects and who may follow each order. */
    private String foodWebsocket;

    /** Razorpay calls this with its own signature, which the food API verifies. */
    private String foodPaymentWebhook;

    /** Food photos, shown before sign-in. */
    private String foodMedia;

    private String foodUploads;

    /** Food catalogue, browsed before sign-in. */
    private String foodHome;
    private String foodCategories;
    private String foodRestaurants;
    private String foodItems;
    private String foodSearch;
    private String foodServiceability;

    /** RideService ride-call signaling socket: upgrade is anonymous; the handler validates the
     *  ?token= query param itself. */
    private String riderRideSignalingWebsocket;

    // ── NMS ──────────────────────────────────────────────────────────────────
    /** Same reasoning as riderRideWebsocket above: the HTTP upgrade itself carries no
     *  Authorization header (SockJS passes the token as a ?access_token= query param instead,
     *  which the gateway's JWT filter never reads), and NMS's own security config already
     *  permitAll()s this path and authenticates the STOMP CONNECT frame itself. Without this, the
     *  gateway's anyExchange().authenticated() rejects the upgrade before NMS ever sees it, which
     *  surfaces to the browser as a bare "400" on the WebSocket handshake. */
    private String nmsInAppWebsocket;

    // ── Rider OnboardingService ──────────────────────────────────────────────
    /** OnboardingRuntimeController's GET .../active-schema and POST/PATCH .../submissions** are
     *  public at the backend -- callers self-report identity via userId in the request body
     *  instead of a JWT, since a customer/driver may not have a session yet when they start
     *  onboarding. Scoped to exactly these two runtime leaves (NOT a blanket
     *  /api/rider/onboarding/**), so that the admin (.../onboarding/admin/**, still guarded by
     *  AdminJwtAuthFilter downstream) and internal (.../onboarding/internal/**, still requiring a
     *  SERVICE-role JWT downstream) sub-paths under the same rider-onboarding-api route are not
     *  also swept into this exemption at the gateway layer. */
    private String riderOnboardingActiveSchemaPublic;
    private String riderOnboardingSubmissionsPublic;
    /** Driver sign-up franchise list ({@code GET /api/v1/onboarding/{type}/franchise-options}): read before any login, only
     *  franchise id, code, name and city of an organization named by its public code. GET ONLY: listed in
     *  {@link #publicGetOnlyPaths()}. */
    private String riderOnboardingFranchiseOptionsPublic;
    /** Driver sign-up place search ({@code GET /api/v1/onboarding/{type}/franchise-places}): read before any login, only the
     *  shared locations master (id, name, type, code, parent name). GET ONLY: listed in {@link #publicGetOnlyPaths()}. */
    private String riderOnboardingFranchisePlacesPublic;

    // ── Train (Rider_TrainService) ───────────────────────────────────────────
    /** Guest train search (GET-only endpoints on the service): stations, availability, PNR, schedule. */
    private String riderTrainSearchPublic;
    /** IRCTC web view page and signed return callback -- IRCTC and the in-app browser cannot send
     *  a bearer token; the service protects them with a per-booking HMAC link instead. */
    private String riderTrainPublic;

    // ── Commerce (Rider_CommerceService) ─────────────────────────────────────
    /** Browsing a shop before sign-in ({@code GET /api/v1/commerce/public/{tenantId}/...}): active categories and products
     *  only, public fields only. The service itself allows GET only on this path. */
    private String riderCommercePublic;

    // ── Config (Rider_ConfigService) ─────────────────────────────────────────
    /** App content the mobile apps read before login: banners, FAQs and the app-version check. */
    private String riderConfigPublic;

    // ── Ride (Rider_RideService) ─────────────────────────────────────────────
    /** Public live-trip tracking ({@code GET /api/v1/ride/public/track}): the shared link carries no sign-in, the share
     *  token arrives in the {@code X-Share-Token} header and RideService checks it. GET ONLY and the exact path only:
     *  listed in {@link #publicGetOnlyPaths()}, not with the any-method paths above. */
    private String riderRidePublicTrack;

    // ── NMS provider delivery receipts ───────────────────────────────────────
    /** Provider callbacks (SMS/WhatsApp/email delivery receipts); each endpoint authenticates itself. */
    private String nmsCallbacks;

    // ── Gateway-level ────────────────────────────────────────────────────────
    private String actuator;

    /**
     * Paths that are public for {@code GET} only, matched exactly (no {@code /**} expansion). Every other method on them
     * needs a token like any protected path. Shared by GatewaySecurityConfig, ReactiveJwtAuthenticationFilter and
     * PrivilegeBasedAccessFilter so the three lists cannot drift apart. Not a bean property (no "get" prefix).
     *
     * @return the configured, non-blank GET-only public paths
     */
    public List<String> publicGetOnlyPaths() {
        return Stream.of(riderRidePublicTrack, riderOnboardingFranchiseOptionsPublic, riderOnboardingFranchisePlacesPublic)
                .filter(StringUtils::hasText).toList();
    }
}
