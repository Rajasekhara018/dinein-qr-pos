package com.heuristq.dinein.kiosk;

import com.heuristq.dinein.audit.AuditService;
import com.heuristq.dinein.kiosk.domain.KioskUpsellRuleEntity;
import com.heuristq.dinein.kiosk.domain.KioskUpsellRuleRepository;
import com.heuristq.dinein.kiosk.dto.KioskDtos.UpsellRuleRequest;
import com.heuristq.dinein.kiosk.dto.KioskDtos.UpsellRuleView;
import com.heuristq.dinein.menu.domain.CategoryRepository;
import com.heuristq.dinein.menu.domain.ItemEntity;
import com.heuristq.dinein.menu.domain.ItemRepository;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.security.CurrentStaff;
import com.heuristq.dinein.shared.util.Text;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Owner-managed upsell prompts, served read-only to the kiosk. */
@Service
public class KioskUpsellService {

    private final KioskUpsellRuleRepository repository;
    private final ItemRepository itemRepository;
    private final CategoryRepository categoryRepository;
    private final AuditService auditService;

    public KioskUpsellService(KioskUpsellRuleRepository repository, ItemRepository itemRepository,
                              CategoryRepository categoryRepository, AuditService auditService) {
        this.repository = repository;
        this.itemRepository = itemRepository;
        this.categoryRepository = categoryRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<UpsellRuleView> forKiosk(Long restaurantId) {
        return repository.findAllByRestaurantIdAndActiveTrueOrderBySortOrderAscIdAsc(restaurantId).stream()
                .map(KioskUpsellService::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<UpsellRuleView> list() {
        return repository.findAllByRestaurantIdOrderBySortOrderAscIdAsc(CurrentStaff.require().restaurantId())
                .stream().map(KioskUpsellService::toView).toList();
    }

    @Transactional
    public UpsellRuleView create(UpsellRuleRequest request) {
        Long restaurantId = CurrentStaff.require().restaurantId();
        KioskUpsellRuleEntity rule = new KioskUpsellRuleEntity();
        rule.setRestaurantId(restaurantId);
        apply(rule, request, restaurantId);
        repository.save(rule);
        auditService.record("KIOSK_UPSELL_CREATED", "KioskUpsellRule", rule.getId(), null, describe(rule));
        return toView(rule);
    }

    @Transactional
    public UpsellRuleView update(Long id, UpsellRuleRequest request) {
        Long restaurantId = CurrentStaff.require().restaurantId();
        KioskUpsellRuleEntity rule = find(id, restaurantId);
        apply(rule, request, restaurantId);
        auditService.record("KIOSK_UPSELL_UPDATED", "KioskUpsellRule", rule.getId(), null, describe(rule));
        return toView(rule);
    }

    @Transactional
    public void delete(Long id) {
        KioskUpsellRuleEntity rule = find(id, CurrentStaff.require().restaurantId());
        repository.delete(rule);
        auditService.record("KIOSK_UPSELL_DELETED", "KioskUpsellRule", id, describe(rule), null);
    }

    private KioskUpsellRuleEntity find(Long id, Long restaurantId) {
        return repository.findByIdAndRestaurantId(id, restaurantId).orElseThrow(() -> ApiException.notFound("Upsell rule"));
    }

    private void apply(KioskUpsellRuleEntity rule, UpsellRuleRequest r, Long restaurantId) {
        if (r.triggerItemId() != null && r.triggerCategoryId() != null) {
            throw ApiException.badRequest("INVALID_UPSELL_TRIGGER", "Choose either a trigger item or a trigger category, not both");
        }
        ItemEntity suggested = itemRepository.findByIdAndRestaurantId(r.suggestedItemId(), restaurantId)
                .filter(ItemEntity::isActive)
                .orElseThrow(() -> ApiException.badRequest("INVALID_UPSELL_ITEM", "The suggested item does not exist"));
        if (r.triggerItemId() != null && itemRepository.findByIdAndRestaurantId(r.triggerItemId(), restaurantId).isEmpty()) {
            throw ApiException.badRequest("INVALID_UPSELL_TRIGGER", "The trigger item does not exist");
        }
        if (r.triggerCategoryId() != null
                && categoryRepository.findByIdAndRestaurantId(r.triggerCategoryId(), restaurantId).isEmpty()) {
            throw ApiException.badRequest("INVALID_UPSELL_TRIGGER", "The trigger category does not exist");
        }
        if (r.triggerItemId() != null && r.triggerItemId().equals(suggested.getId())) {
            throw ApiException.badRequest("INVALID_UPSELL_ITEM", "An item cannot suggest itself");
        }
        rule.setTriggerItemId(r.triggerItemId());
        rule.setTriggerCategoryId(r.triggerCategoryId());
        rule.setSuggestedItemId(suggested.getId());
        rule.setPlacement(r.placement());
        String message = Text.clean(r.message(), 80);
        rule.setMessage(message == null || message.isBlank() ? null : message);
        rule.setSortOrder(r.sortOrder() == null ? 0 : r.sortOrder());
        rule.setActive(r.active() == null || r.active());
    }

    private static UpsellRuleView toView(KioskUpsellRuleEntity r) {
        return new UpsellRuleView(r.getId(), r.getTriggerItemId(), r.getTriggerCategoryId(), r.getSuggestedItemId(),
                r.getPlacement(), r.getMessage(), r.getSortOrder(), r.isActive());
    }

    private static String describe(KioskUpsellRuleEntity r) {
        return r.getPlacement() + " item=" + r.getTriggerItemId() + " category=" + r.getTriggerCategoryId()
                + " -> " + r.getSuggestedItemId();
    }
}
