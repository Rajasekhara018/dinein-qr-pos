package com.heuristq.dinein.audit;

import com.heuristq.dinein.audit.domain.AuditLogEntity;
import com.heuristq.dinein.audit.domain.AuditLogRepository;
import com.heuristq.dinein.audit.dto.AuditLogDtos.AuditLogEntry;
import com.heuristq.dinein.shared.security.CurrentStaff;
import com.heuristq.dinein.shared.security.StaffPrincipal;
import com.heuristq.dinein.shared.web.PageResponse;
import com.heuristq.dinein.staff.domain.StaffUserEntity;
import com.heuristq.dinein.staff.domain.StaffUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Records a sensitive admin action against the acting staff member's own restaurant. Best-effort: if there is no
 * authenticated staff principal (e.g. a background job), the action is not recorded rather than guessed at.
 */
@Service
public class AuditService {

    private static final int MAX_VALUE_LENGTH = 500;

    private final AuditLogRepository repository;
    private final StaffUserRepository staffUserRepository;

    public AuditService(AuditLogRepository repository, StaffUserRepository staffUserRepository) {
        this.repository = repository;
        this.staffUserRepository = staffUserRepository;
    }

    @Transactional
    public void record(String action, String entityType, Long entityId, String previousValue, String newValue) {
        CurrentStaff.find().ifPresent(actor -> record(actor, action, entityType, entityId, previousValue, newValue));
    }

    /**
     * For platform-level actions whose subject isn't the acting principal's own restaurant (e.g. onboarding a new
     * tenant) -- records against the given restaurant regardless of the actor's own tenant, and works even when
     * there is no authenticated principal at all (platform key-authenticated tooling).
     */
    @Transactional
    public void recordForRestaurant(Long restaurantId, String action, String entityType, Long entityId,
                                    String previousValue, String newValue) {
        AuditLogEntity log = new AuditLogEntity();
        log.setRestaurantId(restaurantId);
        log.setStaffUserId(CurrentStaff.find().map(StaffPrincipal::userId).orElse(null));
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setPreviousValue(truncate(previousValue));
        log.setNewValue(truncate(newValue));
        repository.save(log);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogEntry> list(Pageable pageable) {
        Long restaurantId = CurrentStaff.require().restaurantId();
        Page<AuditLogEntity> page = repository.findAllByRestaurantIdOrderByCreatedAtDesc(restaurantId, pageable);
        Map<Long, String> usernames = staffUserRepository
                .findAllById(page.getContent().stream().map(AuditLogEntity::getStaffUserId).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(StaffUserEntity::getId, StaffUserEntity::getUsername));
        return PageResponse.of(page, e -> new AuditLogEntry(e.getId(), e.getStaffUserId(),
                usernames.get(e.getStaffUserId()), e.getAction(), e.getEntityType(), e.getEntityId(),
                e.getPreviousValue(), e.getNewValue(), e.getCreatedAt()));
    }

    private void record(StaffPrincipal actor, String action, String entityType, Long entityId,
                        String previousValue, String newValue) {
        AuditLogEntity log = new AuditLogEntity();
        log.setRestaurantId(actor.restaurantId());
        log.setStaffUserId(actor.userId());
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setPreviousValue(truncate(previousValue));
        log.setNewValue(truncate(newValue));
        repository.save(log);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > MAX_VALUE_LENGTH ? value.substring(0, MAX_VALUE_LENGTH) : value;
    }
}
