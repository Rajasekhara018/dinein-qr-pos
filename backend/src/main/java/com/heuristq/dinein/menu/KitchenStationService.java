package com.heuristq.dinein.menu;

import com.heuristq.dinein.audit.AuditService;
import com.heuristq.dinein.menu.domain.KitchenStationEntity;
import com.heuristq.dinein.menu.domain.KitchenStationRepository;
import com.heuristq.dinein.menu.dto.KitchenStationDtos.KitchenStationRequest;
import com.heuristq.dinein.menu.dto.KitchenStationDtos.KitchenStationResponse;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.security.CurrentStaff;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Freely-named KDS screens for one restaurant (see {@link KitchenStationEntity}). No fixed catalogue: a pizzeria,
 * a cafe and a dosa counter each name their own.
 */
@Slf4j
@Service
public class KitchenStationService {

    private final KitchenStationRepository repository;
    private final AuditService auditService;

    public KitchenStationService(KitchenStationRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<KitchenStationResponse> list() {
        return repository.findAllByRestaurantIdOrderByDisplayOrderAscNameAsc(currentRestaurantId())
                .stream().map(KitchenStationService::toResponse).toList();
    }

    @Transactional
    public KitchenStationResponse create(KitchenStationRequest request) {
        Long restaurantId = currentRestaurantId();
        String name = request.name().trim();
        if (repository.existsByRestaurantIdAndNameIgnoreCase(restaurantId, name)) {
            throw ApiException.conflict("DUPLICATE_NAME", "A station with this name already exists");
        }
        KitchenStationEntity station = new KitchenStationEntity();
        station.setRestaurantId(restaurantId);
        station.setName(name);
        station.setActive(request.active() == null || request.active());
        station.setDisplayOrder(repository.maxDisplayOrder(restaurantId) + 1);
        repository.save(station);
        log.info("kitchen_station.created id={} name={}", station.getId(), name);
        auditService.record("KITCHEN_STATION_CREATED", "KitchenStation", station.getId(), null, name);
        return toResponse(station);
    }

    @Transactional
    public KitchenStationResponse update(Long id, KitchenStationRequest request) {
        KitchenStationEntity station = find(id);
        String name = request.name().trim();
        if (repository.existsByRestaurantIdAndNameIgnoreCaseAndIdNot(station.getRestaurantId(), name, id)) {
            throw ApiException.conflict("DUPLICATE_NAME", "A station with this name already exists");
        }
        station.setName(name);
        if (request.active() != null) {
            station.setActive(request.active());
        }
        return toResponse(station);
    }

    @Transactional
    public void delete(Long id) {
        KitchenStationEntity station = find(id);
        station.setActive(false);
        log.info("kitchen_station.deactivated id={}", id);
    }

    KitchenStationEntity find(Long id) {
        return repository.findByIdAndRestaurantId(id, currentRestaurantId())
                .orElseThrow(() -> ApiException.notFound("Kitchen station"));
    }

    private static Long currentRestaurantId() {
        return CurrentStaff.require().restaurantId();
    }

    private static KitchenStationResponse toResponse(KitchenStationEntity s) {
        return new KitchenStationResponse(s.getId(), s.getName(), s.getDisplayOrder(), s.isActive());
    }
}
