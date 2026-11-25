package com.heuristiq.identity.users;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.identity.common.CommonRequestMapper;
import com.heuristiq.identity.common.CommonRequestObject;
import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.common.PageResult;
import com.heuristiq.identity.common.RequestValidationException;
import com.heuristiq.identity.dto.UpsertUserRequest;
import com.heuristiq.identity.enums.UamEnums;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerTest {
    private UamUserServiceUseCase service;
    private UserController controller;
    private final UamUser user = UamUser.builder().id("u1").userName("jane").displayName("Jane").email("j@x.com").build();

    @BeforeEach
    void setUp() {
        service = mock(UamUserServiceUseCase.class);
        CommonRequestMapper mapper = new CommonRequestMapper(new ObjectMapper(),
                Validation.buildDefaultValidatorFactory().getValidator());
        controller = new UserController(service, mapper);
    }

    private static CommonRequestObject body(Map<String, Object> payload) {
        CommonRequestObject request = new CommonRequestObject();
        request.setRequestObject(payload);
        return request;
    }

    private static PageResult<UamUser> page(UamUser... users) {
        return new PageResult<>(List.of(users), users.length, 1, 50, 0, users.length, true, true, users.length == 0);
    }

    @Test
    void createReturns201WithTheCreatedUser() {
        when(service.create(any())).thenReturn(new UamUserServiceUseCase.CreateOutput(user));
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", "jane");
        payload.put("password", "Secret#123");
        payload.put("phoneNumber", "9876543210");
        payload.put("roles", List.of("ADMIN"));

        ResponseEntity<CommonResponseObject> out = controller.create(body(payload));

        assertThat(out.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(out.getBody().getResponseObject()).isSameAs(user);
        ArgumentCaptor<UamUserServiceUseCase.CreateInput> captor = ArgumentCaptor.forClass(UamUserServiceUseCase.CreateInput.class);
        verify(service).create(captor.capture());
        UpsertUserRequest sent = captor.getValue().request();
        assertThat(sent.username()).isEqualTo("jane");
        assertThat(sent.roles()).containsExactly("ADMIN");
    }

    @Test
    void createRejectsBlankUsernameBeforeReachingTheService() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", " ");
        assertThatThrownBy(() -> controller.create(body(payload))).isInstanceOf(RequestValidationException.class);
        verify(service, never()).create(any());
    }

    @Test
    void updateDelegatesWithIdAndPayload() {
        when(service.update(any())).thenReturn(new UamUserServiceUseCase.UpdateOutput(user));
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", "jane2");

        ResponseEntity<CommonResponseObject> out = controller.update("u1", body(payload));

        assertThat(out.getStatusCode()).isEqualTo(HttpStatus.OK);
        ArgumentCaptor<UamUserServiceUseCase.UpdateInput> captor = ArgumentCaptor.forClass(UamUserServiceUseCase.UpdateInput.class);
        verify(service).update(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo("u1");
        assertThat(captor.getValue().request().username()).isEqualTo("jane2");
    }

    @Test
    void statusAndTotpAreSimpleDelegations() {
        when(service.updateStatus(any())).thenReturn(new UamUserServiceUseCase.UpdateStatusOutput(user));
        when(service.setTotpRequired(any())).thenReturn(new UamUserServiceUseCase.SetTotpRequiredOutput(user));

        assertThat(controller.updateStatus("u1", UamEnums.UserStatus.SUSPENDED).getBody().getResponseObject()).isSameAs(user);
        verify(service).updateStatus(new UamUserServiceUseCase.UpdateStatusInput("u1", UamEnums.UserStatus.SUSPENDED));

        assertThat(controller.setTotpRequired("u1", true).getBody().getResponseObject()).isSameAs(user);
        verify(service).setTotpRequired(new UamUserServiceUseCase.SetTotpRequiredInput("u1", true));
    }

    @Test
    void setPasswordTreatsMissingTemporaryFlagAsFalse() {
        when(service.setPassword(any())).thenReturn(new UamUserServiceUseCase.SetPasswordOutput(user));
        controller.setPassword("u1", body(Map.of("password", "NewPass#1")));
        verify(service).setPassword(new UamUserServiceUseCase.SetPasswordInput("u1", "NewPass#1", false));

        controller.setPassword("u1", body(Map.of("password", "Tmp#1", "temporary", true)));
        verify(service).setPassword(new UamUserServiceUseCase.SetPasswordInput("u1", "Tmp#1", true));
    }

    @Test
    void batchReturnsMinimalSummaries() {
        when(service.findByIds(any())).thenReturn(new UamUserServiceUseCase.FindByIdsOutput(List.of(user)));

        Object payload = controller.batch(List.of("u1", "u2")).getBody().getResponseObject();

        assertThat(payload).isEqualTo(List.of(new UserSummaryResponse("u1", "jane", "Jane", "j@x.com", null)));
        verify(service).findByIds(new UamUserServiceUseCase.FindByIdsInput(List.of("u1", "u2")));
    }

    @Test
    void getAndGetByPhoneAndDelete() {
        when(service.get(any())).thenReturn(new UamUserServiceUseCase.GetOutput(user));
        when(service.findByPhone(new UamUserServiceUseCase.FindByPhoneInput("98"))).thenReturn(
                new UamUserServiceUseCase.FindByPhoneOutput(Optional.of(user)));
        when(service.findByPhone(new UamUserServiceUseCase.FindByPhoneInput("00"))).thenReturn(
                new UamUserServiceUseCase.FindByPhoneOutput(Optional.empty()));

        assertThat(controller.get("u1").getBody().getResponseObject()).isSameAs(user);
        assertThat(controller.getByPhone("98").getBody().getResponseObject()).isSameAs(user);
        CommonResponseObject none = controller.getByPhone("00").getBody();
        assertThat(none.getSuccess()).isTrue();
        assertThat(none.getResponseObject()).isNull();

        assertThat(controller.delete("u1").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).delete(new UamUserServiceUseCase.DeleteInput("u1"));
    }

    @Test
    void listWithoutFiltersUsesPlainPagedList() {
        when(service.list(any(UamUserServiceUseCase.ListPageInput.class)))
                .thenReturn(new UamUserServiceUseCase.ListPageOutput(page(user)));

        CommonResponseObject out = controller.list(0, 50, null, null, " ", null, "  ", null).getBody();

        assertThat(((PageResult<?>) out.getResponseObject()).content()).hasSize(1).first().isSameAs(user);
        verify(service).list(new UamUserServiceUseCase.ListPageInput(0, 50));
        verify(service, never()).search(any());
    }

    @Test
    void listFoldsUsernameAndEmailIntoSearchTerm() {
        when(service.search(any())).thenReturn(new UamUserServiceUseCase.SearchOutput(page()));

        controller.list(1, 10, null, null, " term ", "jane", "j@x.com", null);

        verify(service).search(new UamUserServiceUseCase.SearchInput(1, 10, null, null, "term jane j@x.com", null));
    }

    @Test
    void listWithAnySingleFilterSearches() {
        when(service.search(any())).thenReturn(new UamUserServiceUseCase.SearchOutput(page(user)));

        controller.list(0, 50, "ADMIN", null, null, null, null, null);
        controller.list(0, 50, null, UamEnums.UserStatus.ACTIVE, null, null, null, null);
        controller.list(0, 50, null, null, null, "only-username", null, null);
        controller.list(0, 50, null, null, null, null, null, "orgA");

        verify(service).search(new UamUserServiceUseCase.SearchInput(0, 50, "ADMIN", null, null, null));
        verify(service).search(new UamUserServiceUseCase.SearchInput(0, 50, null, UamEnums.UserStatus.ACTIVE, null, null));
        verify(service).search(new UamUserServiceUseCase.SearchInput(0, 50, null, null, "only-username", null));
        verify(service).search(new UamUserServiceUseCase.SearchInput(0, 50, null, null, null, "orgA"));
    }
}
