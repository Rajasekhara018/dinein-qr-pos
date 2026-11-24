package com.heuristiq.uam.userpreference;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.dto.UpsertUserPreferenceRequest;
import com.heuristiq.uam.helper.UiPreference;
import com.heuristiq.uam.security.TenantContext;
import com.heuristiq.uam.userpreference.UserPreferenceServiceUseCase.DeleteInput;
import com.heuristiq.uam.userpreference.UserPreferenceServiceUseCase.GetByUserInput;
import com.heuristiq.uam.userpreference.UserPreferenceServiceUseCase.GetInput;
import com.heuristiq.uam.userpreference.UserPreferenceServiceUseCase.ListInput;
import com.heuristiq.uam.userpreference.UserPreferenceServiceUseCase.ListPageInput;
import com.heuristiq.uam.userpreference.UserPreferenceServiceUseCase.UpsertInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserPreferenceServiceTest {
    private UserPreferenceRepository repository;
    private UserPreferenceService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserPreferenceRepository.class);
        when(repository.save(any(UserPreference.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new UserPreferenceService(repository);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static UpsertUserPreferenceRequest request(String userId, String app, String tenant) {
        return new UpsertUserPreferenceRequest(tenant, userId, app, new UiPreference(), null, null, null, null);
    }

    @Test
    void upsertCreatesGlobalPreferenceWhenNoneExists() {
        when(repository.findByUserIdAndApplicationId("u1", null)).thenReturn(Optional.empty());

        UserPreference saved = service.upsert(new UpsertInput(request(" u1 ", " ", " t1 "))).preference();

        assertThat(saved.getUserId()).isEqualTo("u1");
        assertThat(saved.getApplicationId()).isNull();
        assertThat(saved.getTenantId()).isEqualTo("t1");
        assertThat(saved.getActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUi()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void upsertUpdatesExistingRowInPlace() {
        UserPreference existing = UserPreference.builder().id("p1").userId("u1").applicationId("app").build();
        when(repository.findByUserIdAndApplicationId("u1", "app")).thenReturn(Optional.of(existing));

        UserPreference saved = service.upsert(new UpsertInput(request("u1", "app", null))).preference();

        assertThat(saved).isSameAs(existing);
        assertThat(saved.getId()).isEqualTo("p1");
        assertThat(saved.getTenantId()).isNull();
    }

    @Test
    void upsertRequiresUserId() {
        assertThatThrownBy(() -> service.upsert(new UpsertInput(request(" ", null, null))))
                .isInstanceOf(UamException.class).hasMessageContaining("User id is required");
    }

    @Test
    void getEnforcesTenantIsolation() {
        UserPreference foreign = UserPreference.builder().id("p9").tenantId("orgB").build();
        when(repository.findById("p9")).thenReturn(Optional.of(foreign));
        when(repository.findById("none")).thenReturn(Optional.empty());
        TenantContext.set("orgA");

        assertThatThrownBy(() -> service.get(new GetInput("p9"))).hasMessageContaining("not found");
        assertThatThrownBy(() -> service.get(new GetInput("none")))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        TenantContext.set("orgB");
        assertThat(service.get(new GetInput("p9")).preference()).isSameAs(foreign);
    }

    @Test
    void getByUserNormalizesApplicationAndEnforcesTenant() {
        UserPreference mine = UserPreference.builder().id("p1").tenantId("orgA").build();
        UserPreference foreign = UserPreference.builder().id("p2").tenantId("orgB").build();
        when(repository.findByUserIdAndApplicationId("u1", null)).thenReturn(Optional.of(mine));
        when(repository.findByUserIdAndApplicationId("u2", "app")).thenReturn(Optional.of(foreign));
        when(repository.findByUserIdAndApplicationId("u3", null)).thenReturn(Optional.empty());
        TenantContext.set("orgA");

        assertThat(service.getByUser(new GetByUserInput("u1", " ")).preference()).isSameAs(mine);
        assertThatThrownBy(() -> service.getByUser(new GetByUserInput("u2", "app"))).isInstanceOf(UamException.class);
        assertThatThrownBy(() -> service.getByUser(new GetByUserInput("u3", null)))
                .hasMessageContaining("not found");
    }

    @Test
    void listsAndDelete() {
        UserPreference p = UserPreference.builder().id("p").build();
        when(repository.findAll()).thenReturn(List.of(p));
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(p)));
        when(repository.findById("p")).thenReturn(Optional.of(p));

        assertThat(service.list(new ListInput()).preferences()).containsExactly(p);
        assertThat(service.list(new ListPageInput(0, 10)).preferences().content()).containsExactly(p);

        service.delete(new DeleteInput("p"));
        verify(repository).delete(p);
    }

    @Test
    void upsertKeepsTheChosenThemeAndTextSizeAndRefusesAnUnknownTextSize() {
        when(repository.findByUserIdAndApplicationId("u1", null)).thenReturn(Optional.empty());
        UiPreference ui = new UiPreference();
        ui.setThemeId("ocean");
        ui.setFontScale("large");

        UserPreference saved = service.upsert(new UpsertInput(new UpsertUserPreferenceRequest("t1", "u1", null, ui, null, null, null, null))).preference();

        assertThat(saved.getUi().getThemeId()).isEqualTo("ocean");
        assertThat(saved.getUi().getFontScale()).isEqualTo("large");

        UiPreference bad = new UiPreference();
        bad.setFontScale("giant");
        assertThatThrownBy(() -> service.upsert(new UpsertInput(new UpsertUserPreferenceRequest("t1", "u1", null, bad, null, null, null, null))))
                .hasMessageContaining("text size");
    }
}
