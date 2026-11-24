package com.heuristiq.uam.userpreference;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.helper.UiPreferenceRules;
import com.heuristiq.uam.dto.UpsertUserPreferenceRequest;
import com.heuristiq.uam.feature.management.ManagementPageSupport;
import com.heuristiq.uam.security.TenantGuard;
import com.heuristiq.uam.common.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class UserPreferenceService implements UserPreferenceServiceUseCase {
    private final UserPreferenceRepository preferenceRepository;

    public UserPreferenceService(UserPreferenceRepository preferenceRepository) {
        this.preferenceRepository = preferenceRepository;
    }

    public UpsertOutput upsert(UpsertInput input) {
        UpsertUserPreferenceRequest request = input.request();
        String userId = normalizeRequired(request.userId());
        String applicationId = normalizeOptional(request.applicationId());
        LocalDateTime now = LocalDateTime.now();
        UserPreference preference = preferenceRepository.findByUserIdAndApplicationId(userId, applicationId)
                .orElseGet(() -> UserPreference.builder()
                        .userId(userId)
                        .applicationId(applicationId)
                        .active(true)
                        .createdAt(now)
                        .build());
        preference.setTenantId(normalizeOptional(request.tenantId()));
        preference.setUi(UiPreferenceRules.checked(request.ui()));
        preference.setNotification(request.notification());
        preference.setDashboard(request.dashboard());
        preference.setAccessibility(request.accessibility());
        preference.setRegional(request.regional());
        preference.setUpdatedAt(now);
        return new UpsertOutput(preferenceRepository.save(preference));
    }

    public GetOutput get(GetInput input) {
        UserPreference preference = preferenceRepository.findById(input.id())
                .orElseThrow(() -> UamException.notFound("User preference not found"));
        TenantGuard.assertAccessible(preference.getTenantId());
        return new GetOutput(preference);
    }

    public GetByUserOutput getByUser(GetByUserInput input) {
        UserPreference preference = preferenceRepository.findByUserIdAndApplicationId(
                        input.userId(), normalizeOptional(input.applicationId()))
                .orElseThrow(() -> UamException.notFound("User preference not found"));
        TenantGuard.assertAccessible(preference.getTenantId());
        return new GetByUserOutput(preference);
    }

    public ListOutput list(ListInput input) {
        return new ListOutput(preferenceRepository.findAll());
    }

    public ListPageOutput list(ListPageInput input) {
        return new ListPageOutput(PageResult.from(preferenceRepository.findAll(
                ManagementPageSupport.page(input.page(), input.size(), "userId"))));
    }

    public DeleteOutput delete(DeleteInput input) {
        preferenceRepository.delete(get(new GetInput(input.id())).preference());
        return new DeleteOutput();
    }

    private String normalizeRequired(String value) {
        if (!StringUtils.hasText(value)) {
            throw new UamException("User id is required");
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
