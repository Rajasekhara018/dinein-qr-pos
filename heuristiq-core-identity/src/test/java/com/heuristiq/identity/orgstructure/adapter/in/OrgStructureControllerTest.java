package com.heuristiq.identity.orgstructure.adapter.in;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.identity.common.CommonRequestMapper;
import com.heuristiq.identity.common.CommonRequestObject;
import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.common.RequestValidationException;
import com.heuristiq.identity.orgstructure.application.DataScopeResolver;
import com.heuristiq.identity.orgstructure.application.OrgStructureUseCase;
import com.heuristiq.identity.orgstructure.domain.ChangeType;
import com.heuristiq.identity.orgstructure.domain.DataScope;
import com.heuristiq.identity.orgstructure.domain.Models.Change;
import com.heuristiq.identity.orgstructure.domain.Models.ChangeSet;
import com.heuristiq.identity.orgstructure.domain.Models.DraftResult;
import com.heuristiq.identity.orgstructure.domain.Models.Employee;
import com.heuristiq.identity.orgstructure.domain.Models.LadderView;
import com.heuristiq.identity.orgstructure.domain.Models.PersonView;
import com.heuristiq.identity.orgstructure.domain.Models.PositionView;
import com.heuristiq.identity.orgstructure.domain.Models.UnitView;
import com.heuristiq.identity.security.TenantContext;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The controller takes the organization from the caller, never from the request, and scopes reads by the caller's reach. */
class OrgStructureControllerTest {
    private OrgStructureUseCase useCase;
    private DataScopeResolver scopes;
    private OrgStructureController controller;
    private Authentication auth;
    private final DataScope limited = new DataScope(true, java.util.Set.of("unit-1"), java.util.Set.of("unit-1"));

    @BeforeEach
    void setUp() {
        useCase = mock(OrgStructureUseCase.class);
        scopes = mock(DataScopeResolver.class);
        CommonRequestMapper mapper = new CommonRequestMapper(new ObjectMapper().findAndRegisterModules(),
                Validation.buildDefaultValidatorFactory().getValidator());
        controller = new OrgStructureController(useCase, mapper, new CallerContext("default"), scopes);
        auth = new UsernamePasswordAuthenticationToken("actor-1", null, List.of(new SimpleGrantedAuthority("ROLE_HR")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        TenantContext.set("orgA");
        when(scopes.resolve(eq("orgA"), eq("actor-1"), any())).thenReturn(limited);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private static CommonRequestObject body(Map<String, Object> payload) {
        CommonRequestObject request = new CommonRequestObject();
        request.setRequestObject(payload);
        return request;
    }

    @Test
    void openDraftUsesCallerTenantAndActorAndReturns201() {
        ChangeSet draft = mock(ChangeSet.class);
        when(useCase.openDraft("orgA", "actor-1", "note")).thenReturn(draft);

        ResponseEntity<CommonResponseObject> out = controller.openDraft(auth, body(Map.of("note", "note")));

        assertThat(out.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(out.getBody().getResponseObject()).isSameAs(draft);
    }

    @Test
    void openDraftRejectsOversizedNote() {
        assertThatThrownBy(() -> controller.openDraft(auth, body(Map.of("note", "x".repeat(501)))))
                .isInstanceOf(RequestValidationException.class);
    }

    @Test
    void currentDraftAndChangesAreTenantScoped() {
        ChangeSet draft = mock(ChangeSet.class);
        Change change = mock(Change.class);
        when(useCase.currentDraft("orgA")).thenReturn(draft);
        when(useCase.changes("orgA", "d1")).thenReturn(List.of(change));

        assertThat(controller.currentDraft().getResponseObject()).isSameAs(draft);
        assertThat(controller.changes("d1").getResponseObject()).isEqualTo(List.of(change));
    }

    @Test
    void addChangeValidatesTypeAndPassesPayload() {
        Change change = mock(Change.class);
        when(useCase.addChange(eq("orgA"), eq("d1"), eq(ChangeType.UNIT_RENAME), any())).thenReturn(change);
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "UNIT_RENAME");
        payload.put("payload", Map.of("id", "u1", "name", "New"));

        ResponseEntity<CommonResponseObject> out = controller.addChange("d1", body(payload));

        assertThat(out.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(out.getBody().getResponseObject()).isSameAs(change);
        verify(useCase).addChange("orgA", "d1", ChangeType.UNIT_RENAME, Map.of("id", "u1", "name", "New"));
        assertThatThrownBy(() -> controller.addChange("d1", body(Map.of("payload", Map.of()))))
                .isInstanceOf(RequestValidationException.class);
    }

    @Test
    void removePreviewPublishDiscardRollbackDelegate() {
        DraftResult result = mock(DraftResult.class);
        when(useCase.preview("orgA", "d1")).thenReturn(result);
        when(useCase.publish("orgA", "d1", "actor-1")).thenReturn(result);

        assertThat(controller.removeChange("d1", "c1").getMessage()).isEqualTo("Change removed");
        verify(useCase).removeChange("orgA", "d1", "c1");
        assertThat(controller.preview("d1").getResponseObject()).isSameAs(result);
        assertThat(controller.publish(auth, "d1").getResponseObject()).isSameAs(result);
        assertThat(controller.discard(auth, "d1").getMessage()).isEqualTo("Draft discarded");
        verify(useCase).discard("orgA", "d1", "actor-1");
        assertThat(controller.rollback(auth).getMessage()).isEqualTo("Publish rolled back");
        verify(useCase).rollbackLatest("orgA", "actor-1");
    }

    @Test
    void treeReadsAreScopedToWhatTheCallerMaySee() {
        UnitView unit = mock(UnitView.class);
        PersonView person = mock(PersonView.class);
        when(useCase.children("orgA", "p1", limited)).thenReturn(List.of(unit));
        when(useCase.searchUnits("orgA", "eng", 20, limited)).thenReturn(List.of(unit));
        when(useCase.people("orgA", "u1", "after", 10, limited)).thenReturn(List.of(person));

        assertThat(controller.units("p1").getResponseObject()).isEqualTo(List.of(unit));
        assertThat(controller.searchUnits("eng", 20).getResponseObject()).isEqualTo(List.of(unit));
        assertThat(controller.people("u1", "after", 10).getResponseObject()).isEqualTo(List.of(person));
        verify(scopes, org.mockito.Mockito.times(3)).resolve(eq("orgA"), eq("actor-1"), any());
    }

    @Test
    void laddersAndPositions() {
        LadderView ladder = new LadderView("l1", "Grades");
        PositionView position = new PositionView("p1", "l1", "Manager", 3, true);
        when(useCase.ladders("orgA")).thenReturn(List.of(ladder));
        when(useCase.positions("orgA", "l1")).thenReturn(List.of(position));

        assertThat(controller.ladders().getResponseObject()).isEqualTo(List.of(ladder));
        assertThat(controller.positions("l1").getResponseObject()).isEqualTo(List.of(position));
    }

    @Test
    void employeesAreCreatedUpdatedAndListedInTheCallersOrganization() {
        Employee created = new Employee(null, "E1", "Jane", null, null, null, null, null);
        when(useCase.createEmployee(eq("orgA"), any())).thenReturn(created);
        when(useCase.updateEmployee(eq("orgA"), any())).thenReturn(created);
        when(useCase.employee("orgA", "e1")).thenReturn(created);
        when(useCase.listEmployees("orgA", "jan", null, 50, limited)).thenReturn(List.of(created));
        Map<String, Object> payload = new HashMap<>();
        payload.put("employeeCode", "E1");
        payload.put("fullName", "Jane");
        payload.put("email", "j@x.com");
        payload.put("joinedOn", "2025-01-02");

        ResponseEntity<CommonResponseObject> createdResponse = controller.createEmployee(body(payload));
        assertThat(createdResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(useCase).createEmployee(eq("orgA"), captor.capture());
        assertThat(captor.getValue().id()).isNull();
        assertThat(captor.getValue().employeeCode()).isEqualTo("E1");
        assertThat(captor.getValue().joinedOn()).isEqualTo(java.time.LocalDate.of(2025, 1, 2));

        assertThat(controller.updateEmployee("e1", body(payload)).getResponseObject()).isSameAs(created);
        ArgumentCaptor<Employee> updated = ArgumentCaptor.forClass(Employee.class);
        verify(useCase).updateEmployee(eq("orgA"), updated.capture());
        assertThat(updated.getValue().id()).isEqualTo("e1");

        assertThat(controller.employee("e1").getResponseObject()).isSameAs(created);
        assertThat(controller.employees("jan", null, 50).getResponseObject()).isEqualTo(List.of(created));
    }

    @Test
    void employeeRequestMissingRequiredFieldsIsRejected() {
        assertThatThrownBy(() -> controller.createEmployee(body(Map.of("fullName", "Jane"))))
                .isInstanceOf(RequestValidationException.class);
    }
}
