package com.heuristiq.identity.users;

import com.heuristiq.identity.common.CommonRequestMapper;
import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.helper.RPhoneNumber;
import com.heuristiq.identity.identity.IdentityProviderSyncService;
import com.heuristiq.identity.security.TenantContext;
import com.heuristiq.identity.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.identity.users.repository.UamUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Batch user lookup used by sibling services (for example Train admin screens) to show who a user id is. */
class UamUserBatchLookupTest {

    private final UamUserRepository repository = mock(UamUserRepository.class);
    private final UamUserService service = new UamUserService(repository, mock(UserCredentialServiceUseCase.class),
            mock(IdentityProviderSyncService.class), mock(com.heuristiq.identity.role.repository.UamRoleRepository.class),
            new com.heuristiq.identity.metrics.UamMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static UamUser user(String id, String tenant) {
        return UamUser.builder().id(id).tenantId(tenant).userName("user-" + id).displayName("Name " + id)
                .email(id + "@x.com").phoneNumber(new RPhoneNumber("+91", "9876543210")).build();
    }

    @SuppressWarnings("unchecked")
    private Collection<String> requestedIds() {
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(repository).findAllByIds(captor.capture());
        return captor.getValue();
    }

    @Test
    void looksUpTrimmedDistinctIdsInOneRepositoryCall() {
        when(repository.findAllByIds(any())).thenReturn(List.of(user("a", null), user("b", null)));

        var out = service.findByIds(new UamUserServiceUseCase.FindByIdsInput(java.util.Arrays.asList("a", " b ", "a", "", null, "  ")));

        assertThat(out.users()).extracting(UamUser::getId).containsExactly("a", "b");
        assertThat(requestedIds()).containsExactly("a", "b");
    }

    @Test
    void emptyInputNeverTouchesTheRepository() {
        assertThat(service.findByIds(new UamUserServiceUseCase.FindByIdsInput(List.of())).users()).isEmpty();
        assertThat(service.findByIds(new UamUserServiceUseCase.FindByIdsInput(null)).users()).isEmpty();
        verify(repository, never()).findAllByIds(any());
    }

    @Test
    void moreThanTheCapIsRejected() {
        List<String> ids = IntStream.range(0, UamUserConstants.MAX_BATCH_LOOKUP + 1).mapToObj(i -> "u" + i).collect(Collectors.toList());
        assertThatThrownBy(() -> service.findByIds(new UamUserServiceUseCase.FindByIdsInput(ids)))
                .isInstanceOf(UamException.class).hasMessageContaining("At most");
        verify(repository, never()).findAllByIds(any());

        List<String> atCap = ids.subList(0, UamUserConstants.MAX_BATCH_LOOKUP);
        when(repository.findAllByIds(any())).thenReturn(List.of());
        assertThat(service.findByIds(new UamUserServiceUseCase.FindByIdsInput(atCap)).users()).isEmpty();
    }

    @Test
    void usersOfAnotherTenantAreLeftOutForATenantScopedCaller() {
        TenantContext.set("tenant-1");
        when(repository.findAllByIds(any())).thenReturn(List.of(user("mine", "tenant-1"), user("theirs", "tenant-2"),
                user("shared", null)));

        var out = service.findByIds(new UamUserServiceUseCase.FindByIdsInput(List.of("mine", "theirs", "shared")));

        assertThat(out.users()).extracting(UamUser::getId).containsExactly("mine", "shared");
    }

    @Test
    void aCallerWithoutATenantSeesEveryTenant() {
        when(repository.findAllByIds(any())).thenReturn(List.of(user("x", "tenant-2")));
        assertThat(service.findByIds(new UamUserServiceUseCase.FindByIdsInput(List.of("x"))).users()).hasSize(1);
    }

    @Test
    void theEndpointReturnsOnlyDisplayDetails() {
        UamUserServiceUseCase useCase = mock(UamUserServiceUseCase.class);
        UamUser u = user("a", null);
        when(useCase.findByIds(any())).thenReturn(new UamUserServiceUseCase.FindByIdsOutput(List.of(u)));
        UserController controller = new UserController(useCase, mock(CommonRequestMapper.class));

        ResponseEntity<CommonResponseObject> res = controller.batch(List.of("a"));

        assertThat(res.getBody().getResponseObject()).isEqualTo(List.of(new UserSummaryResponse("a", "user-a", "Name a",
                "a@x.com", new RPhoneNumber("+91", "9876543210"))));
    }
}
