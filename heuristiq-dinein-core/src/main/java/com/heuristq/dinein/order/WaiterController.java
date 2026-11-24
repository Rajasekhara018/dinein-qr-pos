package com.heuristq.dinein.order;

import com.heuristq.dinein.auth.AuthService;
import com.heuristq.dinein.auth.dto.AuthDtos.StaffInfo;
import com.heuristq.dinein.menu.PublicMenuService;
import com.heuristq.dinein.menu.dto.PublicMenuDtos.MenuResponse;
import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.order.dto.OrderDtos.GuestOrderView;
import com.heuristq.dinein.order.dto.OrderDtos.KitchenOrderView;
import com.heuristq.dinein.order.dto.OrderDtos.StaffPlaceOrderRequest;
import com.heuristq.dinein.order.dto.OrderDtos.WaiterTableView;
import com.heuristq.dinein.payment.PaymentService;
import com.heuristq.dinein.payment.dto.PaymentDtos.CheckoutResponse;
import com.heuristq.dinein.payment.infrastructure.gateway.razorpay.RazorpayPaymentGateway;
import com.heuristq.dinein.settings.SettingsService;
import com.heuristq.dinein.settings.domain.RestaurantSettingsEntity;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.security.CurrentStaff;
import com.heuristq.dinein.shared.security.StaffPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Waiter screen (WAITER, MANAGER, OWNER; see {@code SecurityConfig}): tables with open orders, the active order
 * list, serving READY orders, and staff-assisted ordering paid online or offline.
 */
@RestController
@RequestMapping("/api/v1/waiter")
public class WaiterController {

    /** Waiters only hand food over: READY -> COMPLETED. */
    private static final Set<OrderStatus> WAITER_TARGETS = EnumSet.of(OrderStatus.COMPLETED);

    /**
     * @param onlinePaymentsAvailable whether {@code paymentMethod = ONLINE} can be offered (active gateway configured)
     */
    public record WaiterConfig(String restaurantName, String currency, boolean acceptingOrders, boolean openNow,
                               boolean takeawayEnabled, boolean pricesIncludeGst, boolean onlinePaymentsAvailable,
                               int kitchenWarnMinutes, int kitchenAlertMinutes, StaffInfo staff) {
    }

    private final OrderQueryService queryService;
    private final OrderLifecycleService lifecycle;
    private final OrderPlacementService placementService;
    private final PaymentService paymentService;
    private final PublicMenuService menuService;
    private final SettingsService settingsService;
    private final AuthService authService;

    public WaiterController(OrderQueryService queryService, OrderLifecycleService lifecycle,
                            OrderPlacementService placementService, PaymentService paymentService,
                            PublicMenuService menuService, SettingsService settingsService, AuthService authService) {
        this.queryService = queryService;
        this.lifecycle = lifecycle;
        this.placementService = placementService;
        this.paymentService = paymentService;
        this.menuService = menuService;
        this.settingsService = settingsService;
        this.authService = authService;
    }

    @GetMapping("/config")
    public WaiterConfig config() {
        RestaurantSettingsEntity s = settingsService.forRestaurant(CurrentStaff.require().restaurantId());
        return new WaiterConfig(s.getName(), s.getCurrency(), s.isAcceptingOrders(), settingsService.isOpenNow(s),
                s.isTakeawayEnabled(), s.isPricesIncludeGst(), paymentService.onlinePaymentsAvailable(),
                s.getKitchenWarnMinutes(), s.getKitchenAlertMinutes(),
                authService.infoFor(CurrentStaff.require().userId()));
    }

    @GetMapping("/tables")
    public List<WaiterTableView> tables() {
        return queryService.waiterTables();
    }

    /** Active orders, oldest paid first; READY orders stay until served. */
    @GetMapping("/orders")
    public List<KitchenOrderView> orders(
            @RequestParam(name = "status", defaultValue = "CONFIRMED,PREPARING,READY") List<OrderStatus> statuses) {
        return queryService.waiterOrders(statuses);
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<GuestOrderView> order(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(queryService.staffOrder(id));
    }

    /** READY -> COMPLETED. Idempotent when already COMPLETED; 409 ILLEGAL_TRANSITION from any other status. */
    @PatchMapping("/orders/{id}/serve")
    public KitchenOrderView serve(@PathVariable Long id) {
        return lifecycle.changeByStaff(id, OrderStatus.COMPLETED, WAITER_TARGETS, "user:" + CurrentStaff.require().userId());
    }

    /**
     * The guest menu, including items that are currently unavailable ({@code available = false}) so the waiter can
     * tell the guest; ordering one is rejected with 409 ITEM_UNAVAILABLE exactly as for guests.
     */
    @GetMapping("/menu")
    public ResponseEntity<MenuResponse> menu(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        PublicMenuService.CachedMenu cached = menuService.getMenu(CurrentStaff.require().restaurantId());
        if (ifNoneMatch != null && ifNoneMatch.contains(cached.etag())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(cached.etag())
                    .cacheControl(CacheControl.noCache()).build();
        }
        return ResponseEntity.ok().eTag(cached.etag()).cacheControl(CacheControl.noCache()).body(cached.menu());
    }

    /** Staff-assisted order; see {@link OrderPlacementService#placeForStaff}. */
    @PostMapping("/orders")
    public CheckoutResponse place(@Valid @RequestBody StaffPlaceOrderRequest request) {
        StaffPrincipal staff = CurrentStaff.require();
        return placementService.placeForStaff(staff, request);
    }

    /** New or reused checkout for a staff-placed order that is still PENDING_PAYMENT (409 otherwise). */
    @PostMapping("/orders/{id}/retry-payment")
    public CheckoutResponse retryPayment(@PathVariable Long id) {
        paymentService.assertPlacedByStaff(id);
        return paymentService.ensureCheckout(id, true);
    }

    /**
     * Client-side verification after an SDK checkout run on the waiter's device (same body as
     * {@code POST /api/v1/public/payments/verify}). The provider signature authenticates the payment.
     */
    @PostMapping("/payments/verify")
    public GuestOrderView verify(@RequestBody Map<String, String> body) {
        if (body == null || body.size() > 30) {
            throw ApiException.badRequest("BAD_REQUEST", "Malformed request");
        }
        String provider = body.containsKey("provider") ? body.get("provider")
                : body.containsKey("razorpay_order_id") ? RazorpayPaymentGateway.CODE : paymentService.activeProvider();
        Long orderId = paymentService.verifyCallback(provider, body, null);
        return queryService.staffOrder(orderId);
    }
}
