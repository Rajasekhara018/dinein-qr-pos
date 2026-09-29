package com.heuristq.dinein.table;

import com.heuristq.dinein.audit.AuditService;
import com.heuristq.dinein.shared.config.AppProperties;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.security.CurrentStaff;
import com.heuristq.dinein.shared.util.SecureTokens;
import com.heuristq.dinein.shared.web.ApiPaths;
import com.heuristq.dinein.table.domain.DiningTableEntity;
import com.heuristq.dinein.table.domain.DiningTableRepository;
import com.heuristq.dinein.table.dto.TableDtos.TableRequest;
import com.heuristq.dinein.table.dto.TableDtos.TableResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
public class TableService {

    private final DiningTableRepository tableRepository;
    private final String publicBaseUrl;
    private final AuditService auditService;

    public TableService(DiningTableRepository tableRepository, AppProperties properties, AuditService auditService) {
        this.tableRepository = tableRepository;
        String base = properties.publicBaseUrl();
        this.publicBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.auditService = auditService;
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
        return tableRepository.findAllByRestaurantIdOrderByLabelAsc(currentRestaurantId())
                .stream().map(this::toResponse).toList();
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

    private static String normalize(String label) {
        return label.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private TableResponse toResponse(DiningTableEntity t) {
        return new TableResponse(t.getId(), t.getLabel(), t.isActive(), menuUrl(t),
                qrImageUrl(t), t.getCreatedAt(), t.getUpdatedAt());
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
