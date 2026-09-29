package com.heuristq.dinein.table;

import com.heuristq.dinein.audit.AuditService;
import com.heuristq.dinein.order.domain.OrderEntity;
import com.heuristq.dinein.order.domain.OrderRepository;
import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.shared.config.AppProperties;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.security.CurrentStaff;
import com.heuristq.dinein.shared.util.SecureTokens;
import com.heuristq.dinein.shared.web.ApiPaths;
import com.heuristq.dinein.table.domain.DiningTableEntity;
import com.heuristq.dinein.table.domain.DiningTableRepository;
import com.heuristq.dinein.table.dto.TableDtos.ReserveRequest;
import com.heuristq.dinein.table.dto.TableDtos.TableRequest;
import com.heuristq.dinein.table.dto.TableDtos.TableResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class TableService {

    private final DiningTableRepository tableRepository;
    private final OrderRepository orderRepository;
    private final String publicBaseUrl;
    private final AuditService auditService;
    private final Clock clock;

    public TableService(DiningTableRepository tableRepository, OrderRepository orderRepository,
                        AppProperties properties, AuditService auditService, Clock clock) {
        this.tableRepository = tableRepository;
        this.orderRepository = orderRepository;
        String base = properties.publicBaseUrl();
        this.publicBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.auditService = auditService;
        this.clock = clock;
    }

    /** 32 random bytes, base64url: unguessable, so a table link cannot be forged or enumerated. */
    public static String newQrToken() {
        return SecureTokens.randomUrlSafe(32);
    }

    public String menuUrl(DiningTableEntity table) {
        return publicBaseUrl + "/menu?t=" + table.getQrToken() + "&r=" + table.getRestaurantId();
    }

    @Transactional(readOnly = true)
    public List<TableResponse> list() {
        Long restaurantId = currentRestaurantId();
        Set<Long> occupied = Set.copyOf(orderRepository.findOccupiedTableIds(restaurantId, OrderStatus.OCCUPIES_TABLE));
        return tableRepository.findAllByRestaurantIdOrderByLabelAsc(restaurantId)
                .stream().map(t -> toResponse(t, occupied.contains(t.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<DiningTableEntity> findForPrint(List<Long> ids) {
        List<DiningTableEntity> all = tableRepository.findAllByRestaurantIdOrderByLabelAsc(currentRestaurantId());
        if (ids == null || ids.isEmpty()) {
            return all.stream().filter(DiningTableEntity::isActive).toList();
        }
        return all.stream().filter(t -> ids.contains(t.getId())).toList();
    }

    @Transactional(readOnly = true)
    public DiningTableEntity get(Long id) {
        return tableRepository.findByIdAndRestaurantId(id, currentRestaurantId())
                .orElseThrow(() -> ApiException.notFound("Table"));
    }

    private static Long currentRestaurantId() {
        return CurrentStaff.require().restaurantId();
    }

    @Transactional(readOnly = true)
    public Optional<DiningTableEntity> findActiveByToken(String token) {
        if (token == null || token.isBlank() || token.length() > 64) {
            return Optional.empty();
        }
        return tableRepository.findByQrToken(token).filter(DiningTableEntity::isActive);
    }

    @Transactional
    public TableResponse create(TableRequest request) {
        Long restaurantId = currentRestaurantId();
        String label = normalize(request.label());
        if (tableRepository.existsByRestaurantIdAndLabelIgnoreCase(restaurantId, label)) {
            throw ApiException.conflict("DUPLICATE_LABEL", "A table with this label already exists");
        }
        DiningTableEntity table = new DiningTableEntity();
        table.setRestaurantId(restaurantId);
        table.setLabel(label);
        table.setQrToken(newQrToken());
        table.setActive(request.active() == null || request.active());
        tableRepository.save(table);
        log.info("table.created id={} label={}", table.getId(), label);
        auditService.record("TABLE_CREATED", "DiningTable", table.getId(), null, label);
        return toResponse(table);
    }

    @Transactional
    public TableResponse update(Long id, TableRequest request) {
        DiningTableEntity table = get(id);
        String label = normalize(request.label());
        if (tableRepository.existsByRestaurantIdAndLabelIgnoreCaseAndIdNot(table.getRestaurantId(), label, id)) {
            throw ApiException.conflict("DUPLICATE_LABEL", "A table with this label already exists");
        }
        table.setLabel(label);
        if (request.active() != null) {
            table.setActive(request.active());
        }
        log.info("table.updated id={} active={}", id, table.isActive());
        return toResponse(table);
    }

    /** Invalidates every printed QR for this table. */
    @Transactional
    public TableResponse regenerateQr(Long id) {
        DiningTableEntity table = get(id);
        table.setQrToken(newQrToken());
        log.info("table.qr_regenerated id={}", id);
        auditService.record("TABLE_QR_REGENERATED", "DiningTable", table.getId(), null, table.getLabel());
        return toResponse(table);
    }

    /** Books a table ahead of a group's arrival; a reservation on an already-reserved table replaces it. */
    @Transactional
    public TableResponse reserve(Long id, ReserveRequest request) {
        DiningTableEntity table = get(id);
        table.setReservedUntil(request.until());
        table.setReservedNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
        log.info("table.reserved id={} until={}", id, request.until());
        return toResponse(table);
    }

    @Transactional
    public TableResponse clearReservation(Long id) {
        DiningTableEntity table = get(id);
        table.setReservedUntil(null);
        table.setReservedNote(null);
        log.info("table.reservation_cleared id={}", id);
        return toResponse(table);
    }

    /**
     * Moves every open order from {@code fromTableId} to an empty, unreserved table (a group asked to switch
     * tables). Rejects if the destination already has an open order of its own -- use {@link #mergeTables} for
     * that instead, since combining bills onto one table needs to be an explicit choice.
     */
    @Transactional
    public TableResponse moveOrders(Long fromTableId, Long toTableId) {
        if (fromTableId.equals(toTableId)) {
            throw ApiException.badRequest("SAME_TABLE", "Choose a different table to move to");
        }
        DiningTableEntity from = get(fromTableId);
        DiningTableEntity to = get(toTableId);
        List<OrderEntity> openOrders = orderRepository.findByTableIdAndStatusIn(fromTableId, OrderStatus.OCCUPIES_TABLE);
        if (openOrders.isEmpty()) {
            throw ApiException.badRequest("TABLE_EMPTY", "This table has no open order to move");
        }
        if (!orderRepository.findByTableIdAndStatusIn(toTableId, OrderStatus.OCCUPIES_TABLE).isEmpty()) {
            throw ApiException.conflict("TABLE_OCCUPIED", "The destination table already has an open order");
        }
        if (to.isReserved(clock.instant())) {
            throw ApiException.conflict("TABLE_RESERVED", "The destination table is reserved");
        }
        openOrders.forEach(o -> o.setTableId(toTableId));
        log.info("table.orders_moved fromTableId={} toTableId={} orders={}", fromTableId, toTableId, openOrders.size());
        auditService.record("TABLE_ORDER_MOVED", "DiningTable", fromTableId, from.getLabel(), to.getLabel());
        return toResponse(to);
    }

    /**
     * Combines two tables' bills: every open order on {@code fromTableId} moves onto {@code toTableId} (which may
     * already have its own open order), and {@code fromTableId} ends up free.
     */
    @Transactional
    public TableResponse mergeTables(Long fromTableId, Long toTableId) {
        if (fromTableId.equals(toTableId)) {
            throw ApiException.badRequest("SAME_TABLE", "Choose a different table to merge into");
        }
        DiningTableEntity from = get(fromTableId);
        DiningTableEntity to = get(toTableId);
        List<OrderEntity> openOrders = orderRepository.findByTableIdAndStatusIn(fromTableId, OrderStatus.OCCUPIES_TABLE);
        if (openOrders.isEmpty()) {
            throw ApiException.badRequest("TABLE_EMPTY", "This table has no open order to merge");
        }
        openOrders.forEach(o -> o.setTableId(toTableId));
        log.info("table.merged fromTableId={} toTableId={} orders={}", fromTableId, toTableId, openOrders.size());
        auditService.record("TABLES_MERGED", "DiningTable", fromTableId, from.getLabel(), to.getLabel());
        return toResponse(to);
    }

    private static String normalize(String label) {
        return label.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    /** Single-table responses (create/update/regenerate) look occupancy up on demand rather than batched. */
    private TableResponse toResponse(DiningTableEntity t) {
        boolean occupied = orderRepository.findOccupiedTableIds(t.getRestaurantId(), OrderStatus.OCCUPIES_TABLE)
                .contains(t.getId());
        return toResponse(t, occupied);
    }

    private TableResponse toResponse(DiningTableEntity t, boolean occupied) {
        return new TableResponse(t.getId(), t.getLabel(), t.isActive(), occupied, t.isReserved(clock.instant()),
                t.getReservedUntil(), t.getReservedNote(), menuUrl(t), qrImageUrl(t), t.getCreatedAt(), t.getUpdatedAt());
    }

    /**
     * The QR image is generated fresh on every request (see {@link AdminTableController#qrPng}), but its URL is
     * versioned by a short fingerprint of the table's current {@code qrToken}, so it can still be cached by the
     * browser as immutable: the URL only ever repeats while the token it encodes is unchanged, and
     * {@link #regenerateQr} always changes the token. That also means a browser can never keep serving a stale
     * response under this URL after the QR is regenerated, the way it could when the URL was id-only.
     */
    private String qrImageUrl(DiningTableEntity t) {
        return ApiPaths.V1 + "/admin/tables/" + t.getId() + "/qr.png?v=" + qrVersion(t);
    }

    /** Short, non-secret fingerprint of the table's current QR token; changes exactly when the token does. */
    public String qrVersion(DiningTableEntity t) {
        return SecureTokens.sha256Hex(t.getQrToken()).substring(0, 10);
    }
}
