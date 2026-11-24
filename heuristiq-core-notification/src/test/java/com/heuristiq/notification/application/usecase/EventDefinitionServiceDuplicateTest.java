package com.heuristiq.notification.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.heuristiq.notification.domain.model.NotificationEventDefinition;
import com.heuristiq.notification.domain.repository.IEventDefinitionRepository;
import com.heuristiq.notification.shared.CommonResponseObject;
import com.heuristiq.notification.shared.GlobalExceptionHandler;
import com.heuristiq.notification.shared.NmsConflictException;

/** A second event with a value already in use is refused as a conflict, on create and on update, and nothing is written. */
class EventDefinitionServiceDuplicateTest {

    private IEventDefinitionRepository repository;
    private EventDefinitionService service;

    @BeforeEach
    void setUp() {
        repository = mock(IEventDefinitionRepository.class);
        service = new EventDefinitionService(repository);
        when(repository.save(any(NotificationEventDefinition.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static NotificationEventDefinition event(String id, String value, String label) {
        NotificationEventDefinition e = new NotificationEventDefinition();
        e.setId(id);
        e.setValue(value);
        e.setLabel(label);
        return e;
    }

    @Test
    void creatingAnEventWhoseValueExistsIsAConflict() {
        when(repository.existsByValue("ACCOUNT_CREATED")).thenReturn(true);

        NmsConflictException ex = assertThrows(NmsConflictException.class,
                () -> service.create(event(null, "account created", "Account created")));

        assertEquals("Event already exists: ACCOUNT_CREATED", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void renamingAnEventOntoAnotherEventsValueIsAConflict() {
        NotificationEventDefinition passwordReset = event("e2", "PASSWORD_RESET", "Password reset");
        when(repository.findById("e2")).thenReturn(Optional.of(passwordReset));
        when(repository.findByValue("ACCOUNT_CREATED")).thenReturn(Optional.of(event("e1", "ACCOUNT_CREATED", "Account created")));

        assertThrows(NmsConflictException.class, () -> service.update("e2", event("e2", "ACCOUNT_CREATED", "Password reset")));

        assertEquals("PASSWORD_RESET", passwordReset.getValue());
        verify(repository, never()).save(any());
    }

    @Test
    void savingAnEventUnderItsOwnValueIsNotAConflict() {
        NotificationEventDefinition existing = event("e1", "ACCOUNT_CREATED", "Account created");
        when(repository.findById("e1")).thenReturn(Optional.of(existing));
        when(repository.findByValue("ACCOUNT_CREATED")).thenReturn(Optional.of(existing));

        NotificationEventDefinition saved = service.update("e1", event("e1", "ACCOUNT_CREATED", "Account created (new)"));

        assertEquals("Account created (new)", saved.getLabel());
    }

    @Test
    void theConflictReachesTheCallerAs409InTheStandardEnvelope() {
        ResponseEntity<CommonResponseObject> response =
                new GlobalExceptionHandler().handleConflict(new NmsConflictException("Event already exists: ACCOUNT_CREATED"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Event already exists: ACCOUNT_CREATED", response.getBody().getMessage());
    }
}
