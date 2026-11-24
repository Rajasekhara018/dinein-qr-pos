package com.heuristq.dinein.kiosk;

import com.heuristq.dinein.audit.AuditService;
import com.heuristq.dinein.auth.AuthService;
import com.heuristq.dinein.auth.dto.AuthDtos.StaffInfo;
import com.heuristq.dinein.kiosk.dto.KioskDtos.BrandingResponse;
import com.heuristq.dinein.kiosk.dto.KioskDtos.StaffUnlockRequest;
import com.heuristq.dinein.kiosk.dto.KioskDtos.StaffUnlockResponse;
import com.heuristq.dinein.kiosk.dto.KioskDtos.UpsellRuleView;
import com.heuristq.dinein.kiosk.dto.KioskDtos.HeartbeatRequest;
import com.heuristq.dinein.kiosk.dto.KioskDtos.KioskOrderRequest;
import com.heuristq.dinein.kiosk.dto.KioskDtos.KioskOrderResponse;
import com.heuristq.dinein.kiosk.dto.KioskDtos.PairRequest;
import com.heuristq.dinein.kiosk.dto.KioskDtos.PairResponse;
import com.heuristq.dinein.menu.PublicMenuService;
import com.heuristq.dinein.menu.dto.PublicMenuDtos.MenuResponse;
import com.heuristq.dinein.order.OrderPlacementService;
import com.heuristq.dinein.order.OrderPlacementService.PlacedOrder;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.security.KioskPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** What the Flutter self-order kiosk calls. Everything except {@code /pair} needs a kiosk device token. */
@RestController
@RequestMapping("/api/v1/kiosk")
public class KioskController {

    private final KioskDeviceService deviceService;
    private final KioskBrandingService brandingService;
    private final PublicMenuService menuService;
    private final OrderPlacementService placementService;
    private final KioskUpsellService upsellService;
    private final AuthService authService;
    private final AuditService auditService;

    public KioskController(KioskDeviceService deviceService, KioskBrandingService brandingService,
                           PublicMenuService menuService, OrderPlacementService placementService,
                           KioskUpsellService upsellService, AuthService authService, AuditService auditService) {
        this.deviceService = deviceService;
        this.brandingService = brandingService;
        this.menuService = menuService;
        this.placementService = placementService;
        this.upsellService = upsellService;
        this.authService = authService;
        this.auditService = auditService;
    }

    @PostMapping("/pair")
    public PairResponse pair(@Valid @RequestBody PairRequest request) {
        return deviceService.pair(request.code());
    }

    /** Same menu the QR guests see, revalidated by ETag so an idle kiosk's refresh costs almost nothing. */
    @GetMapping("/menu")
    public ResponseEntity<MenuResponse> menu(@AuthenticationPrincipal KioskPrincipal kiosk,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        PublicMenuService.CachedMenu cached = menuService.getMenu(kiosk.restaurantId());
        if (ifNoneMatch != null && ifNoneMatch.contains(cached.etag())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(cached.etag())
                    .cacheControl(CacheControl.noCache()).build();
        }
        return ResponseEntity.ok().eTag(cached.etag()).cacheControl(CacheControl.noCache()).body(cached.menu());
    }

    @GetMapping("/branding")
    public BrandingResponse branding(@AuthenticationPrincipal KioskPrincipal kiosk) {
        return brandingService.get(kiosk.restaurantId());
    }

    @PostMapping("/orders")
    public KioskOrderResponse placeOrder(@AuthenticationPrincipal KioskPrincipal kiosk,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody KioskOrderRequest request) {
        if (!KioskBrandingService.PAY_AT_COUNTER.equals(request.paymentMode())) {
            throw ApiException.badRequest("UNSUPPORTED_PAYMENT_MODE",
                    "This payment method is not available on the kiosk yet");
        }
        brandingService.assertKioskEnabled(kiosk.restaurantId());
        PlacedOrder placed = placementService.placeForKiosk(kiosk.restaurantId(), idempotencyKey,
                request.orderType(), request.items(), request.notes());
        return new KioskOrderResponse(placed.orderId(), placed.orderNumber(), placed.displayToken(),
                placed.status(), placed.grandTotal());
    }

    /** Upsell prompts as rules; the app matches them against the menu it already holds. */
    @GetMapping("/upsells")
    public List<UpsellRuleView> upsells(@AuthenticationPrincipal KioskPrincipal kiosk) {
        return upsellService.forKiosk(kiosk.restaurantId());
    }

    /**
     * Opens the kiosk's service menu (restart, exit kiosk mode, ...) for a staff member of this restaurant. A wrong
     * PIN is a 403, not a 401: the app reads a 401 as "this device was revoked".
     */
    @PostMapping("/staff-unlock")
    public StaffUnlockResponse staffUnlock(@AuthenticationPrincipal KioskPrincipal kiosk,
            @Valid @RequestBody StaffUnlockRequest request) {
        StaffInfo staff = authService.verifyStaffPinForRestaurant(request.username(), request.pin(),
                kiosk.restaurantId());
        auditService.recordForRestaurant(kiosk.restaurantId(), "KIOSK_STAFF_UNLOCK", "KioskDevice",
                kiosk.deviceId(), null, staff.username());
        return new StaffUnlockResponse(staff.displayName() == null ? staff.username() : staff.displayName(),
                staff.role().name());
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(@AuthenticationPrincipal KioskPrincipal kiosk,
            @Valid @RequestBody(required = false) HeartbeatRequest request) {
        deviceService.heartbeat(kiosk.deviceId(), request == null ? null : request.applicationVersion());
        return ResponseEntity.noContent().build();
    }
}
