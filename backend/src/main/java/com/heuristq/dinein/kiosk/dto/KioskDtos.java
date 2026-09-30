package com.heuristq.dinein.kiosk.dto;

import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.order.domain.OrderType;
import com.heuristq.dinein.order.dto.OrderDtos.CartLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class KioskDtos {

    private KioskDtos() {
    }

    // ----- Kiosk app -----------------------------------------------------------------------------

    public record PairRequest(@NotBlank @Pattern(regexp = "^\\d{6}$", message = "Pairing code is 6 digits") String code) {
    }

    public record PairResponse(String deviceToken, Long deviceId, String restaurantName) {
    }

    /** Only identifiers and quantities: every price and tax is recomputed on the server. */
    public record KioskOrderRequest(
            @NotEmpty @Size(max = 50) List<@Valid @NotNull CartLine> items,
            @NotNull OrderType orderType,
            @NotBlank String paymentMode,
            @Size(max = 300) String notes) {
    }

    /** {@code tokenNumber} is the number the customer waits for; {@code total} is what they pay at the counter. */
    public record KioskOrderResponse(Long orderId, String orderNumber, int tokenNumber, OrderStatus status,
                                     BigDecimal total) {
    }

    public record HeartbeatRequest(@Size(max = 20) String applicationVersion) {
    }

    /** Field names match the Flutter app's {@code Branding.fromJson}. Null fields mean "use the app default". */
    public record BrandingResponse(String restaurantName, boolean kioskEnabled, String primaryColor,
                                   String secondaryColor, String headline, String subtext, String startButtonLabel,
                                   Integer idleTimeoutSeconds, String logoUrl, String backgroundUrl,
                                   List<String> paymentModes, Long logoImageId, Long backgroundImageId) {
    }

    // ----- Admin ---------------------------------------------------------------------------------

    public record CreateDeviceRequest(@NotBlank @Size(max = 60) String name) {
    }

    /** {@code pairingCode} is shown once, here; only its hash is stored. */
    public record PairingCodeResponse(Long id, String name, String pairingCode, Instant pairingExpiresAt) {
    }

    /** {@code status}: PENDING_PAIRING (code issued, not used), ACTIVE, or REVOKED. */
    public record KioskDeviceView(Long id, String name, String status, Instant pairedAt, Instant lastSeenAt,
                                  Instant pairingExpiresAt, String applicationVersion, Instant createdAt) {
    }

    /** Trigger item and trigger category are mutually exclusive; neither means "any order". */
    public record UpsellRuleRequest(
            Long triggerItemId,
            Long triggerCategoryId,
            @NotNull Long suggestedItemId,
            @NotNull @Pattern(regexp = "^(ITEM_ADDED|CHECKOUT)$", message = "Use ITEM_ADDED or CHECKOUT") String placement,
            @Size(max = 80) String message,
            Integer sortOrder,
            Boolean active) {
    }

    public record UpsellRuleView(Long id, Long triggerItemId, Long triggerCategoryId, Long suggestedItemId,
                                 String placement, String message, int sortOrder, boolean active) {
    }

    // ----- Staff on the kiosk / at the counter ------------------------------------------------------

    /** A staff member unlocking the kiosk's service menu with their username and PIN. */
    public record StaffUnlockRequest(@NotBlank @Size(max = 60) String username,
                                     @NotBlank @Pattern(regexp = "^\\d{4,8}$", message = "PIN is 4 to 8 digits") String pin) {
    }

    public record StaffUnlockResponse(String displayName, String role) {
    }

    public record CounterOrderLine(String name, String variantName, int quantity, String notes, List<String> addons) {
    }

    /** An unpaid kiosk order waiting at the counter. */
    public record CounterOrderView(Long id, String orderNumber, int displayToken, OrderType orderType,
                                   OrderStatus status, BigDecimal grandTotal, Instant placedAt,
                                   List<CounterOrderLine> items) {
    }

    public record CounterPayRequest(
            @NotNull @Pattern(regexp = "^(CASH|UPI_AT_COUNTER|CARD_AT_COUNTER)$",
                    message = "Use CASH, UPI_AT_COUNTER or CARD_AT_COUNTER") String method) {
    }

    public record UpdateBrandingRequest(
            @NotNull Boolean kioskEnabled,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Use a colour like #D9480F") String primaryColor,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Use a colour like #212529") String secondaryColor,
            @Size(max = 80) String headline,
            @Size(max = 120) String subtext,
            @Size(max = 40) String startButtonLabel,
            @Min(15) @Max(600) Integer idleTimeoutSeconds,
            Long logoImageId,
            Long backgroundImageId) {
    }
}
