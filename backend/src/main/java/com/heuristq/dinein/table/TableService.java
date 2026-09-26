package com.heuristq.dinein.table;

import com.heuristq.dinein.shared.config.AppProperties;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.util.SecureTokens;
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

    public TableService(DiningTableRepository tableRepository, AppProperties properties) {
        this.tableRepository = tableRepository;
        String base = properties.publicBaseUrl();
        this.publicBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /** 32 random bytes, base64url: unguessable, so a table link cannot be forged or enumerated. */
    public static String newQrToken() {
        return SecureTokens.randomUrlSafe(32);
    }

    public String menuUrl(DiningTableEntity table) {
        return publicBaseUrl + "/menu?t=" + table.getQrToken();
    }

    @Transactional(readOnly = true)
    public List<TableResponse> list() {
        return tableRepository.findAllByOrderByLabelAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<DiningTableEntity> findForPrint(List<Long> ids) {
        List<DiningTableEntity> all = tableRepository.findAllByOrderByLabelAsc();
        if (ids == null || ids.isEmpty()) {
            return all.stream().filter(DiningTableEntity::isActive).toList();
        }
        return all.stream().filter(t -> ids.contains(t.getId())).toList();
    }

    @Transactional(readOnly = true)
    public DiningTableEntity get(Long id) {
        return tableRepository.findById(id).orElseThrow(() -> ApiException.notFound("Table"));
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
        String label = normalize(request.label());
        if (tableRepository.existsByLabelIgnoreCase(label)) {
            throw ApiException.conflict("DUPLICATE_LABEL", "A table with this label already exists");
        }
        DiningTableEntity table = new DiningTableEntity();
        table.setLabel(label);
        table.setQrToken(newQrToken());
        table.setActive(request.active() == null || request.active());
        tableRepository.save(table);
        log.info("table.created id={} label={}", table.getId(), label);
        return toResponse(table);
    }

    @Transactional
    public TableResponse update(Long id, TableRequest request) {
        DiningTableEntity table = get(id);
        String label = normalize(request.label());
        if (tableRepository.existsByLabelIgnoreCaseAndIdNot(label, id)) {
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
        return toResponse(table);
    }

    private static String normalize(String label) {
        return label.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private TableResponse toResponse(DiningTableEntity t) {
        return new TableResponse(t.getId(), t.getLabel(), t.isActive(), menuUrl(t),
                "/api/admin/tables/" + t.getId() + "/qr.png", t.getCreatedAt(), t.getUpdatedAt());
    }
}
