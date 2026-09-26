package com.heuristq.dinein.order;

import com.heuristq.dinein.guest.GuestSession;
import com.heuristq.dinein.menu.domain.AddonEntity;
import com.heuristq.dinein.menu.domain.CategoryEntity;
import com.heuristq.dinein.menu.domain.CategoryRepository;
import com.heuristq.dinein.menu.domain.ItemEntity;
import com.heuristq.dinein.menu.domain.ItemRepository;
import com.heuristq.dinein.menu.domain.ItemVariantEntity;
import com.heuristq.dinein.order.PricingService.LineInput;
import com.heuristq.dinein.order.domain.OrderEntity;
import com.heuristq.dinein.order.domain.OrderItemAddonEntity;
import com.heuristq.dinein.order.domain.OrderItemEntity;
import com.heuristq.dinein.order.domain.OrderRepository;
import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.order.domain.OrderType;
import com.heuristq.dinein.order.dto.OrderDtos.CartLine;
import com.heuristq.dinein.order.dto.OrderDtos.CartProblem;
import com.heuristq.dinein.order.dto.OrderDtos.PlaceOrderRequest;
import com.heuristq.dinein.order.dto.OrderDtos.StaffPlaceOrderRequest;
import com.heuristq.dinein.payment.OfflinePaymentService;
import com.heuristq.dinein.payment.PaymentService;
import com.heuristq.dinein.payment.domain.OfflinePaymentMethod;
import com.heuristq.dinein.payment.dto.PaymentDtos.CheckoutResponse;
import com.heuristq.dinein.settings.SettingsService;
import com.heuristq.dinein.settings.domain.RestaurantSettingsEntity;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.security.StaffPrincipal;
import com.heuristq.dinein.shared.util.Money;
import com.heuristq.dinein.shared.util.Text;
import com.heuristq.dinein.table.domain.DiningTableEntity;
import com.heuristq.dinein.table.domain.DiningTableRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Places orders: guest self-orders from the table QR, and staff-assisted orders taken by a waiter or at the counter.
 * Both go through the same core ({@link #createOrder}): the browser only sends ids and quantities; every price, tax
 * and total is recomputed here from the current menu and copied onto the order lines as a snapshot.
 *
 * <p>Staff orders are either paid online (same checkout and expiry as guests) or settled offline on the spot, in
 * which case the order is created and confirmed through {@link OfflinePaymentService} in one transaction.
 */
@Slf4j
@Service
public class OrderPlacementService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    private final OrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final CategoryRepository categoryRepository;
    private final DiningTableRepository tableRepository;
    private final SettingsService settingsService;
    private final PricingService pricingService;
    private final OrderNumberService orderNumberService;
    private final PaymentService paymentService;
    private final OfflinePaymentService offlinePaymentService;
    private final TransactionTemplate tx;
    private final Clock clock;

    public OrderPlacementService(OrderRepository orderRepository, ItemRepository itemRepository,
                                 CategoryRepository categoryRepository, DiningTableRepository tableRepository,
                                 SettingsService settingsService, PricingService pricingService,
                                 OrderNumberService orderNumberService, PaymentService paymentService,
                                 OfflinePaymentService offlinePaymentService, TransactionTemplate tx, Clock clock) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.categoryRepository = categoryRepository;
        this.tableRepository = tableRepository;
        this.settingsService = settingsService;
        this.pricingService = pricingService;
        this.orderNumberService = orderNumberService;
        this.paymentService = paymentService;
        this.offlinePaymentService = offlinePaymentService;
        this.tx = tx;
        this.clock = clock;
    }

    /** What the core needs to create an order, whoever places it. */
    record NewOrder(Long tableId, String guestSessionId, Long placedByStaffId, OrderType requestedType,
                    List<CartLine> items, String notes, String customerName, String customerPhone,
                    String idempotencyKey) {
    }

    // ----- Guest self-order ------------------------------------------------------------------------

    public CheckoutResponse place(GuestSession guest, String idempotencyKey, PlaceOrderRequest request) {
        requireIdempotencyKey(idempotencyKey);
        Optional<OrderEntity> replay = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (replay.isPresent()) {
            return replay(replay.get(), guest);
        }
        // Fail before creating an order that could never be paid (e.g. gateway credentials missing).
        paymentService.activeProvider();
        NewOrder draft = new NewOrder(guest.tableId(), guest.sessionId(), null, request.orderType(), request.items(),
                request.notes(), request.customerName(), request.customerPhone(), idempotencyKey);
        Long orderId;
        try {
            orderId = tx.execute(status -> createOrder(draft));
        } catch (DataIntegrityViolationException e) {
            // Two identical requests raced; the loser returns the winner's order.
            OrderEntity winner = orderRepository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> e);
            return replay(winner, guest);
        }
        // The Razorpay call happens after the order is committed; a failure here is recoverable via retry-payment.
        return paymentService.ensureCheckout(orderId, false);
    }

    private CheckoutResponse replay(OrderEntity existing, GuestSession guest) {
        if (!existing.belongsToGuest(guest.sessionId())) {
            throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "This request key was already used");
        }
        log.info("order.place.idempotent_replay orderId={}", existing.getId());
        return paymentService.ensureCheckout(existing.getId(), false);
    }

    // ----- Staff-assisted order (waiter / counter) -------------------------------------------------

    /**
     * Places an order on behalf of a guest. ONLINE returns the same checkout as the guest flow; an offline method
     * (CASH, UPI_AT_COUNTER, CARD_AT_COUNTER) records the payment and confirms the order immediately, returning
     * {@code status = CONFIRMED}, {@code provider = OFFLINE} and no checkout. Repeating the idempotency key returns
     * the same order.
     */
    public CheckoutResponse placeForStaff(StaffPrincipal staff, StaffPlaceOrderRequest request) {
        String idempotencyKey = request.idempotencyKey();
        requireIdempotencyKey(idempotencyKey);
        Optional<OrderEntity> replay = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (replay.isPresent()) {
            return staffReplay(replay.get());
        }
        OfflinePaymentMethod offline = request.paymentMethod().offline();
        if (offline == null) {
            paymentService.activeProvider();
        }
        String actor = "user:" + staff.userId();
        NewOrder draft = new NewOrder(request.tableId(), null, staff.userId(), request.orderType(), request.items(),
                request.note(), request.customerName(), request.customerPhone(), idempotencyKey);
        Long orderId;
        try {
            orderId = tx.execute(status -> {
                Long id = createOrder(draft);
                if (offline != null) {
                    offlinePaymentService.recordAndConfirm(id, offline, staff.userId(), actor);
                }
                return id;
            });
        } catch (DataIntegrityViolationException e) {
            OrderEntity winner = orderRepository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> e);
            return staffReplay(winner);
        }
        log.info("order.placed_by_staff orderId={} staffId={} payment={}", orderId, staff.userId(), request.paymentMethod());
        return offline == null ? paymentService.ensureCheckout(orderId, false) : offlineResponse(orderId);
    }

    private CheckoutResponse staffReplay(OrderEntity existing) {
        if (!existing.isPlacedByStaff()) {
            throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "This request key was already used");
        }
        log.info("order.place_by_staff.idempotent_replay orderId={}", existing.getId());
        return paymentService.offlineReceipt(existing.getId())
                .orElseGet(() -> paymentService.ensureCheckout(existing.getId(), false));
    }

    private CheckoutResponse offlineResponse(Long orderId) {
        return paymentService.offlineReceipt(orderId)
                .orElseThrow(() -> new IllegalStateException("offline payment missing for order " + orderId));
    }

    private static void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key header must be 8-64 URL-safe characters");
        }
    }

    // ----- Shared core ------------------------------------------------------------------------------

    private Long createOrder(NewOrder draft) {
        RestaurantSettingsEntity settings = settingsService.current();
        settingsService.assertAcceptingOrders(settings);
        boolean byStaff = draft.placedByStaffId() != null;
        OrderType orderType = OrderTypeRules.resolve(draft.requestedType(), settings.isTakeawayEnabled());
        if (byStaff) {
            OrderTypeRules.requireTableForDineIn(orderType, draft.tableId());
        }
        DiningTableEntity table = null;
        if (draft.tableId() != null) {
            table = tableRepository.findById(draft.tableId()).filter(DiningTableEntity::isActive)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INVALID_TABLE",
                            byStaff ? "This table does not exist or is inactive" : "Please scan the QR code on your table"));
        }

        List<ResolvedLine> lines = resolveCart(draft.items());
        PricingService.Bill bill = pricingService.calculate(
                lines.stream().map(l -> new LineInput(l.unitPrice(), l.line().quantity(), l.item().getGstPercent())).toList(),
                settings.isPricesIncludeGst());
        if (bill.grandTotal().signum() <= 0) {
            throw ApiException.badRequest("EMPTY_ORDER", "Order total must be greater than zero");
        }

        OrderNumberService.Allocated number = orderNumberService.next();
        OrderEntity order = new OrderEntity();
        order.setOrderNumber(number.orderNumber());
        order.setDisplayToken(number.displayToken());
        order.setTableId(table == null ? null : table.getId());
        order.setGuestSessionId(draft.guestSessionId());
        order.setPlacedByStaffId(draft.placedByStaffId());
        order.setOrderType(orderType);
        order.setCustomerName(Text.clean(draft.customerName(), 60));
        order.setCustomerPhone(draft.customerPhone() == null || draft.customerPhone().isBlank() ? null : draft.customerPhone());
        order.setNotes(Text.clean(draft.notes(), 300));
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setSubtotal(bill.subtotal());
        order.setTaxTotal(bill.taxTotal());
        order.setGrandTotal(bill.grandTotal());
        order.setPricesIncludeGst(settings.isPricesIncludeGst());
        order.setIdempotencyKey(draft.idempotencyKey());
        order.setPlacedAt(clock.instant());

        for (int i = 0; i < lines.size(); i++) {
            ResolvedLine rl = lines.get(i);
            PricingService.LineResult priced = bill.lines().get(i);
            OrderItemEntity oi = new OrderItemEntity();
            oi.setItemId(rl.item().getId());
            oi.setVariantId(rl.variant() == null ? null : rl.variant().getId());
            oi.setItemName(rl.item().getName());
            oi.setVariantName(rl.variant() == null ? null : rl.variant().getName());
            oi.setFoodType(rl.item().getFoodType());
            oi.setUnitPrice(rl.unitPrice());
            oi.setQuantity(rl.line().quantity());
            oi.setGstPercent(rl.item().getGstPercent());
            oi.setLineTotal(priced.lineTotal());
            oi.setTaxAmount(priced.taxAmount());
            oi.setNotes(Text.clean(rl.line().notes(), 200));
            for (AddonEntity addon : rl.addons()) {
                OrderItemAddonEntity oa = new OrderItemAddonEntity();
                oa.setAddonId(addon.getId());
                oa.setAddonName(addon.getName());
                oa.setPrice(addon.getPrice());
                oi.addAddon(oa);
            }
            order.addItem(oi);
        }
        orderRepository.saveAndFlush(order);
        log.info("order.placed orderId={} orderNumber={} tableId={} type={} byStaff={} lines={} grandTotal={}",
                order.getId(), order.getOrderNumber(), order.getTableId(), orderType, draft.placedByStaffId(),
                lines.size(), order.getGrandTotal());
        return order.getId();
    }

    record ResolvedLine(CartLine line, ItemEntity item, ItemVariantEntity variant, List<AddonEntity> addons,
                        BigDecimal unitPrice) {
    }

    /**
     * Validates every cart line against the live menu. All problems are collected and reported together so the
     * guest UI can fix the whole cart in one go.
     */
    List<ResolvedLine> resolveCart(List<CartLine> cart) {
        List<Long> itemIds = cart.stream().map(CartLine::itemId).distinct().toList();
        Map<Long, ItemEntity> items = itemRepository.findAllWithVariants(itemIds).stream()
                .collect(Collectors.toMap(ItemEntity::getId, Function.identity()));
        Map<Long, CategoryEntity> categories = categoryRepository.findAllById(
                        items.values().stream().map(ItemEntity::getCategoryId).distinct().toList())
                .stream().collect(Collectors.toMap(CategoryEntity::getId, Function.identity()));

        List<CartProblem> problems = new ArrayList<>();
        List<ResolvedLine> resolved = new ArrayList<>();
        for (int idx = 0; idx < cart.size(); idx++) {
            CartLine line = cart.get(idx);
            ItemEntity item = items.get(line.itemId());
            if (item == null || !item.isActive()) {
                problems.add(new CartProblem(idx, line.itemId(), line.variantId(), null, item == null ? null : item.getName(), "ITEM_NOT_FOUND"));
                continue;
            }
            CategoryEntity category = categories.get(item.getCategoryId());
            if (category == null || !category.isActive()) {
                problems.add(new CartProblem(idx, item.getId(), line.variantId(), null, item.getName(), "CATEGORY_UNAVAILABLE"));
                continue;
            }
            if (!item.isAvailable()) {
                problems.add(new CartProblem(idx, item.getId(), line.variantId(), null, item.getName(), "ITEM_UNAVAILABLE"));
                continue;
            }
            ItemVariantEntity variant = null;
            BigDecimal base;
            if (item.hasActiveVariants()) {
                if (line.variantId() == null) {
                    problems.add(new CartProblem(idx, item.getId(), null, null, item.getName(), "VARIANT_REQUIRED"));
                    continue;
                }
                variant = item.activeVariants().stream().filter(v -> v.getId().equals(line.variantId()))
                        .findFirst().orElse(null);
                if (variant == null) {
                    problems.add(new CartProblem(idx, item.getId(), line.variantId(), null, item.getName(), "VARIANT_UNAVAILABLE"));
                    continue;
                }
                base = variant.getPrice();
            } else {
                if (line.variantId() != null || item.getBasePrice() == null || item.getBasePrice().signum() <= 0) {
                    problems.add(new CartProblem(idx, item.getId(), line.variantId(), null, item.getName(), "VARIANT_UNAVAILABLE"));
                    continue;
                }
                base = item.getBasePrice();
            }
            List<AddonEntity> addons = new ArrayList<>();
            boolean addonProblem = false;
            for (Long addonId : new LinkedHashSet<>(line.addonIds() == null ? List.<Long>of() : line.addonIds())) {
                Optional<AddonEntity> addon = item.activeAddons().stream().filter(a -> a.getId().equals(addonId)).findFirst();
                if (addon.isEmpty()) {
                    problems.add(new CartProblem(idx, item.getId(), line.variantId(), addonId, item.getName(), "ADDON_UNAVAILABLE"));
                    addonProblem = true;
                } else {
                    addons.add(addon.get());
                }
            }
            if (addonProblem) {
                continue;
            }
            BigDecimal unitPrice = Money.round(addons.stream().map(AddonEntity::getPrice).reduce(base, BigDecimal::add));
            resolved.add(new ResolvedLine(line, item, variant, addons, unitPrice));
        }
        if (!problems.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "ITEM_UNAVAILABLE",
                    "Some items in your cart are no longer available. Please review your cart.", problems);
        }
        return resolved;
    }
}
