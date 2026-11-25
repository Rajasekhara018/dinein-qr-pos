package com.heuristiq.documents.exception;

import com.heuristiq.documents.common.dto.CommonResponseObject;
import com.heuristiq.documents.common.dto.FieldValidationError;
import com.heuristiq.documents.documenttype.domain.DocumentTypeException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void dmsExceptionKeepsItsStatusAndMessage() {
        ResponseEntity<CommonResponseObject> r = handler.handleDmsException(DmsException.forbidden("nope"));
        assertEquals(HttpStatus.FORBIDDEN, r.getStatusCode());
        assertEquals("nope", r.getBody().getMessage());
        assertFalse(r.getBody().getSuccess());
    }

    @Test
    void documentTypeKindsMapToHttpStatuses() {
        assertEquals(HttpStatus.BAD_REQUEST, handler.handleDocumentType(DocumentTypeException.invalid("x")).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, handler.handleDocumentType(DocumentTypeException.forbidden("x")).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, handler.handleDocumentType(DocumentTypeException.notFound("x")).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, handler.handleDocumentType(DocumentTypeException.conflict("x")).getStatusCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                handler.handleDocumentType(DocumentTypeException.approvalUnavailable("x")).getStatusCode());
    }

    @Test
    void unreadableRequestAndTypeMismatchAreBadRequests() {
        ResponseEntity<CommonResponseObject> r = handler.handleUnreadableRequest(
                new MissingServletRequestParameterException("p", "String"));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());

        MethodArgumentTypeMismatchException mismatch = mock(MethodArgumentTypeMismatchException.class);
        when(mismatch.getName()).thenReturn("deleted");
        ResponseEntity<CommonResponseObject> t = handler.handleTypeMismatch(mismatch);
        assertEquals(HttpStatus.BAD_REQUEST, t.getStatusCode());
        assertEquals("Parameter deleted is not valid", t.getBody().getMessage());
    }

    @Test
    void beanValidationListsFieldErrors() throws Exception {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "obj");
        binding.rejectValue(null, "x");
        binding.addError(new org.springframework.validation.FieldError("obj", "name", "must not be blank"));
        MethodParameter param = new MethodParameter(Object.class.getMethod("toString"), -1);
        ResponseEntity<CommonResponseObject> r = handler.handleValidation(new MethodArgumentNotValidException(param, binding));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertEquals("VALIDATION_ERROR", r.getBody().getErrorCode());
        @SuppressWarnings("unchecked")
        List<FieldValidationError> errors = (List<FieldValidationError>) r.getBody().getResponseObject();
        assertEquals(List.of(new FieldValidationError("name", "must not be blank")), errors);
    }

    @Test
    void constraintViolationListsPropertyPaths() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> v = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("size");
        when(v.getPropertyPath()).thenReturn(path);
        when(v.getMessage()).thenReturn("too big");
        ResponseEntity<CommonResponseObject> r = handler.handleConstraintViolation(new ConstraintViolationException(Set.of(v)));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        @SuppressWarnings("unchecked")
        List<FieldValidationError> errors = (List<FieldValidationError>) r.getBody().getResponseObject();
        assertEquals(new FieldValidationError("size", "too big"), errors.get(0));
    }

    @Test
    void unknownPathIs404AndUnexpectedErrorIs500WithoutLeakingDetail() {
        ResponseEntity<CommonResponseObject> nf = handler.handleNoResource(
                new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/nope"));
        assertEquals(HttpStatus.NOT_FOUND, nf.getStatusCode());

        ResponseEntity<CommonResponseObject> err = handler.handleException(new IllegalStateException("secret sql"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, err.getStatusCode());
        assertEquals("Unexpected DMS error", err.getBody().getMessage());
    }
}
