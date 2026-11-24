package com.heuristq.dinein.order;

import com.heuristq.dinein.audit.AuditService;
import com.heuristq.dinein.order.OrderNumberService.Allocated;
import com.heuristq.dinein.order.domain.OrderEntity;
import com.heuristq.dinein.order.domain.OrderItemEntity;
import com.heuristq.dinein.order.domain.OrderRepository;
import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.order.dto.OrderDtos.AdminOrderView;
import com.heuristq.dinein.settings.SettingsService;
import com.heuristq.dinein.settings.domain.RestaurantSettingsEntity;
import com.heuristq.dinein.shared.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderSplitServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderNumberService orderNumberService = mock(OrderNumberService.class);
    private final PricingService pricingService = new PricingService();
    private final SettingsService settingsService = mock(SettingsService.class);
    private final OrderLifecycleService lifecycle = mock(OrderLifecycleService.class);
    private final OrderViewMapper viewMapper = mock(OrderViewMapper.class);
    private final AuditService auditService = mock(AuditService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);

    private final OrderSplitService service = new OrderSplitService(orderRepository, orderNumberService,
            pricingService, settingsService, lifecycle, viewMapper, auditService, clock);

    private OrderEntity order;
    private OrderItemEntity burger;
    private OrderItemEntity fries;

    @BeforeEach
    void setUp() {
        order = new OrderEntity();
        order.setId(1L);
        order.setRestaurantId(7L);
        order.setOrderNumber("ORIGINAL-1");
        order.setStatus(OrderStatus.PENDING_PAYMENT);

        burger = new OrderItemEntity();
        burger.setId(101L);
        burger.setItemName("Burger");
        burger.setUnitPrice(new BigDecimal("200.00"));
        burger.setQuantity(1);
        burger.setGstPercent(new BigDecimal("5.00"));

        fries = new OrderItemEntity();
        fries.setId(102L);
        fries.setItemName("Fries");
        fries.setUnitPrice(new BigDecimal("100.00"));
        fries.setQuantity(2);
        fries.setGstPercent(new BigDecimal("5.00"));

        order.addItem(burger);
        order.addItem(fries);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderNumberService.next())
                .thenReturn(new Allocated("SPLIT-1", 501))
                .thenReturn(new Allocated("SPLIT-2", 502));

        RestaurantSettingsEntity settings = new RestaurantSettingsEntity();
        settings.setRestaurantId(7L);
        settings.setPricesIncludeGst(false);
        when(settingsService.forRestaurant(7L)).thenReturn(settings);

        when(viewMapper.tableLabel(any())).thenReturn(null);
        when(viewMapper.toAdminView(any(), any(), any())).thenAnswer(inv -> null);
    }

    @Test
    void splitsItemsIntoSeparateOrdersWithRecomputedTotalsAndCancelsTheOriginal() {
        ArgumentCaptor<OrderEntity> saved = ArgumentCaptor.forClass(OrderEntity.class);

        List<AdminOrderView> result = service.split(1L, List.of(List.of(101L), List.of(102L)), "user:1");

        assertThat(result).hasSize(2);
        verify(orderRepository, org.mockito.Mockito.times(2)).saveAndFlush(saved.capture());
        List<OrderEntity> created = saved.getAllValues();

        OrderEntity burgerOrder = created.get(0);
        assertThat(burgerOrder.getOrderNumber()).isEqualTo("SPLIT-1");
        assertThat(burgerOrder.getRestaurantId()).isEqualTo(7L);
        assertThat(burgerOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(burgerOrder.getGrandTotal()).isEqualByComparingTo("210.00"); // 200 + 5%
        assertThat(burgerOrder.getItems()).hasSize(1);
        assertThat(burgerOrder.getItems().get(0).getItemName()).isEqualTo("Burger");

        OrderEntity friesOrder = created.get(1);
        assertThat(friesOrder.getGrandTotal()).isEqualByComparingTo("210.00"); // 2x100 + 5%
        assertThat(friesOrder.getItems()).hasSize(1);
        assertThat(friesOrder.getItems().get(0).getItemName()).isEqualTo("Fries");

        // The original never gets a new item added to it; only cancelled.
        assertThat(order.getStatus()).isNotEqualTo(OrderStatus.CANCELLED); // lifecycle is mocked, so it doesn't actually flip
        verify(lifecycle).transition(order, OrderStatus.CANCELLED, "user:1");
        assertThat(order.getCancelReason()).contains("2 orders");
    }

    @Test
    void rejectsSplittingAnOrderThatIsAlreadyPaid() {
        order.setStatus(OrderStatus.CONFIRMED);

        assertThatCode("user:1", List.of(List.of(101L), List.of(102L)), "NOT_SPLITTABLE");

        verify(lifecycle, never()).transition(any(), any(), anyString());
    }

    @Test
    void rejectsASingleGroupCoveringEverything() {
        assertThatCode("user:1", List.of(List.of(101L, 102L)), "SPLIT_REQUIRES_GROUPS");
    }

    @Test
    void rejectsAnItemListedInTwoGroups() {
        assertThatCode("user:1", List.of(List.of(101L), List.of(101L, 102L)), "ITEM_LISTED_TWICE");
    }

    @Test
    void rejectsAnIncompleteSplitThatLeavesAnItemUnassigned() {
        assertThatCode("user:1", List.of(List.of(101L), List.of()), "INCOMPLETE_SPLIT");
    }

    @Test
    void rejectsAnUnknownItemId() {
        assertThatCode("user:1", List.of(List.of(101L), List.of(999L)), "UNKNOWN_ITEM");
    }

    private void assertThatCode(String actor, List<List<Long>> itemGroups, String expectedCode) {
        assertThatThrownBy(() -> service.split(1L, itemGroups, actor))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(expectedCode));
    }
}
