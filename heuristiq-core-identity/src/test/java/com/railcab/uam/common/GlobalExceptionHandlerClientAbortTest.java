package com.heuristiq.uam.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

/** A caller that hangs up mid-response is not a server fault; every other failure is still a 500. */
class GlobalExceptionHandlerClientAbortTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void aClosedConnectionIsNotReportedAsAServerError() {
        Exception wrapped = new IllegalStateException("could not write",
                new AsyncRequestNotUsableException("Broken pipe", new IOException("Broken pipe")));

        assertThat(handler.handleAny(wrapped)).isNull();
        handler.handleClientAbort(new AsyncRequestNotUsableException("Broken pipe", new IOException("Broken pipe")));
    }

    @Test
    void anyOtherFailureIsStillAnInternalServerError() {
        ResponseEntity<CommonResponseObject> response = handler.handleAny(new IllegalStateException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void aConstraintViolationIsAConflictWithAReadableMessageAndNoSql() {
        ResponseEntity<CommonResponseObject> response = handler.handleDataIntegrity(
                new org.springframework.dao.DataIntegrityViolationException("could not execute statement; SQL [delete from applications]"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).contains("still in use").doesNotContain("SQL");
    }
}
