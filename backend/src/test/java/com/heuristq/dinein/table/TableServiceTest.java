package com.heuristq.dinein.table;

import com.heuristq.dinein.audit.AuditService;
import com.heuristq.dinein.order.domain.OrderEntity;
import com.heuristq.dinein.order.domain.OrderRepository;
import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.shared.config.AppProperties;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.security.StaffPrincipal;
import com.heuristq.dinein.staff.domain.StaffRole;
import com.heuristq.dinein.table.domain.DiningTableEntity;
import com.heuristq.dinein.table.domain.DiningTableRepository;
import com.heuristq.dinein.table.dto.TableDtos.ReserveRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TableServiceTest {

    private final DiningTableRepository tableRepository = mock(DiningTableRepository.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
    private final AppProperties properties = properties();

    private final TableService service = new TableService(tableRepository, orderRepository, properties,
            auditService, clock);

    private DiningTableEntity tableA;
    private DiningTableEntity tableB;

    @BeforeEach
    void setUp() {
        tableA = table(1L, "A1");
        tableB = table(2L, "A2");
        when(tableRepository.findByIdAndRestaurantId(1L, 7L)).thenReturn(Optional.of(tableA));
        when(tableRepository.findByIdAndRestaurantId(2L, 7L)).thenReturn(Optional.of(tableB));
        when(orderRepository.findOccupiedTableIds(eq(7L), any())).thenReturn(List.of());

        StaffPrincipal staff = new StaffPrincipal(1L, "owner", StaffRole.OWNER, 7L, null, false, false);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(staff, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private DiningTableEntity table(Long id, String label) {
        DiningTableEntity t = new DiningTableEntity();
        t.setId(id);
        t.setRestaurantId(7L);
        t.setLabel(label);
        t.setQrToken("qr-" + id);
        return t;
    }

    private OrderEntity openOrder(Long id, Long tableId) {
        OrderEntity o = new OrderEntity();
        o.setId(id);
        o.setRestaurantId(7L);
        o.setTableId(tableId);
        o.setStatus(OrderStatus.CONFIRMED);
        return o;
    }

    @Test
    void reservingATableSetsUntilAndNote() {
        Instant until = Instant.parse("2026-01-01T15:00:00Z");

        var response = service.reserve(1L, new ReserveRequest(until, "Sharma, party of 6"));

        assertThat(response.reserved()).isTrue();
        assertThat(tableA.getReservedUntil()).isEqualTo(until);
        assertThat(tableA.getReservedNote()).isEqualTo("Sharma, party of 6");
    }

    @Test
    void clearingAReservationRemovesItsUntilAndNote() {
        tableA.setReservedUntil(Instant.parse("2026-01-01T15:00:00Z"));
        tableA.setReservedNote("old note");

        var response = service.clearReservation(1L);

        assertThat(response.reserved()).isFalse();
        assertThat(tableA.getReservedUntil()).isNull();
        assertThat(tableA.getReservedNote()).isNull();
    }

    @Test
    void movingOrdersReassignsThemToAnEmptyDestinationTable() {
        OrderEntity order = openOrder(50L, 1L);
        when(orderRepository.findByTableIdAndStatusIn(eq(1L), any())).thenReturn(List.of(order));
        when(orderRepository.findByTableIdAndStatusIn(eq(2L), any())).thenReturn(List.of());

        service.moveOrders(1L, 2L);

        assertThat(order.getTableId()).isEqualTo(2L);
    }

    @Test
    void movingIntoAnAlreadyOccupiedTableIsRejected() {
        OrderEntity sourceOrder = openOrder(50L, 1L);
        OrderEntity destOrder = openOrder(51L, 2L);
        when(orderRepository.findByTableIdAndStatusIn(eq(1L), any())).thenReturn(List.of(sourceOrder));
        when(orderRepository.findByTableIdAndStatusIn(eq(2L), any())).thenReturn(List.of(destOrder));

        assertThatThrownBy(() -> service.moveOrders(1L, 2L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("TABLE_OCCUPIED"));

        assertThat(sourceOrder.getTableId()).isEqualTo(1L); // unchanged
    }

    @Test
    void movingIntoAReservedTableIsRejected() {
        OrderEntity sourceOrder = openOrder(50L, 1L);
        tableB.setReservedUntil(Instant.parse("2026-01-01T13:00:00Z")); // after the fixed clock instant
        when(orderRepository.findByTableIdAndStatusIn(eq(1L), any())).thenReturn(List.of(sourceOrder));
        when(orderRepository.findByTableIdAndStatusIn(eq(2L), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.moveOrders(1L, 2L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("TABLE_RESERVED"));
    }

    @Test
    void movingWithNoOpenOrderOnTheSourceTableIsRejected() {
        when(orderRepository.findByTableIdAndStatusIn(eq(1L), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.moveOrders(1L, 2L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("TABLE_EMPTY"));
    }

    @Test
    void mergingCombinesOrdersOntoTheDestinationEvenWhenItIsAlreadyOccupied() {
        OrderEntity sourceOrder = openOrder(50L, 1L);
        OrderEntity destOrder = openOrder(51L, 2L);
        when(orderRepository.findByTableIdAndStatusIn(eq(1L), any())).thenReturn(List.of(sourceOrder));

        service.mergeTables(1L, 2L);

        assertThat(sourceOrder.getTableId()).isEqualTo(2L);
        assertThat(destOrder.getTableId()).isEqualTo(2L); // untouched, already there
    }

    @Test
    void mergingIntoTheSameTableIsRejected() {
        assertThatThrownBy(() -> service.mergeTables(1L, 1L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("SAME_TABLE"));
    }

    private static AppProperties properties() {
        return new AppProperties("http://localhost", null, null, null, null, null, null, null, null);
    }
}
