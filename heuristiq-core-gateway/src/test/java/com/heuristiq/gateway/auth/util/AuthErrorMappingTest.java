package com.heuristiq.gateway.auth.util;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class AuthErrorMappingTest {

    @Test
    void knownStatusesMapToTheirOwnStatus() {
        assertThat(AuthErrorMapping.get(401).status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(AuthErrorMapping.get(403).status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(AuthErrorMapping.get(400).status()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownStatusFallsBackToInternalServerError() {
        AuthErrorMapping.ErrorDetails details = AuthErrorMapping.get(418);

        assertThat(details.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(details.error()).isNotBlank();
        assertThat(details.message()).isNotBlank();
    }
}
