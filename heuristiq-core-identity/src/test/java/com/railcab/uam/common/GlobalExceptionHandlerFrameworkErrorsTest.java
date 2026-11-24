package com.heuristiq.uam.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** A request the framework refuses (unknown path, wrong method, unreadable body) is the caller's mistake, never a 500, and never leaks internals. */
class GlobalExceptionHandlerFrameworkErrorsTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void anUnavailableDependencyIsA503WithAFixedMessageNotA500() {
        ResponseEntity<CommonResponseObject> response = handler.handleDownstreamUnavailable(new com.heuristiq.resilient.DownstreamUnavailableException(
                "franchise-service", com.heuristiq.resilient.DownstreamUnavailableException.Reason.CIRCUIT_OPEN, new IllegalStateException("secret detail")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getMessage()).isEqualTo(FrameworkErrorMessages.SERVICE_UNAVAILABLE).doesNotContain("franchise").doesNotContain("secret");
    }

    @Test
    void anUnknownPathIsA404WithAFixedMessage() {
        ResponseEntity<CommonResponseObject> response = handler.handleFrameworkRequestError(
                new NoResourceFoundException(HttpMethod.GET, "/api/v1/uam/users/abc/roles", "api/v1/uam/users/abc/roles"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo(FrameworkErrorMessages.NOT_FOUND);
    }

    @Test
    void theWrongMethodOnAKnownPathIsA405() {
        ResponseEntity<CommonResponseObject> response = handler.handleFrameworkRequestError(
                new HttpRequestMethodNotSupportedException("GET", List.of("POST")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().getMessage()).isEqualTo(FrameworkErrorMessages.METHOD_NOT_ALLOWED);
    }

    @Test
    void anUnsupportedContentTypeIsA415() {
        ResponseEntity<CommonResponseObject> response = handler.handleFrameworkRequestError(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void aMissingParameterAndAnUnreadableBodyAreA400WithoutInternals() {
        ResponseEntity<CommonResponseObject> missing = handler.handleFrameworkRequestError(
                new MissingServletRequestParameterException("cityCode", "String"));
        ResponseEntity<CommonResponseObject> unreadable = handler.handleFrameworkRequestError(
                new HttpMessageNotReadableException("JSON parse error: com.heuristiq.uam.SomeClass", new MockHttpInputMessage(new byte[0])));

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(unreadable.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(unreadable.getBody().getMessage()).isEqualTo(FrameworkErrorMessages.BAD_REQUEST).doesNotContain("com.heuristiq");
    }

    @Test
    void anythingElseStillGetsTheGenericFailureWithoutLeakingTheCause() {
        ResponseEntity<CommonResponseObject> response = handler.handleAny(new IllegalStateException("com.heuristiq.uam.Secret failed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).doesNotContain("com.heuristiq");
    }
}
