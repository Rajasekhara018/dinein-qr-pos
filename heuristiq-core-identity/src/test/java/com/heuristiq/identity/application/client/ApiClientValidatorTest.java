package com.heuristiq.identity.application.client;

import com.heuristiq.identity.common.UamException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Client id and redirect URI rules of {@link ApiClientValidator}. */
class ApiClientValidatorTest {

    @Test
    void validClientIdsPassIncludingTheLengthBoundaries() {
        for (String id : List.of("web", "customer-app-web", "App.v2_prod", "9lives", "a".repeat(64))) {
            assertThatCode(() -> ApiClientValidator.requireValidClientId(id)).as(id).doesNotThrowAnyException();
        }
    }

    @Test
    void badClientIdsAreRefused() {
        for (String id : List.of("ab", "a".repeat(65), "my client", "client!", "-leading", ".leading", "_leading", "emojié", "")) {
            assertThatThrownBy(() -> ApiClientValidator.requireValidClientId(id)).as(id)
                    .isInstanceOf(UamException.class).hasMessageContaining("Client id");
        }
        assertThatThrownBy(() -> ApiClientValidator.requireValidClientId(null)).isInstanceOf(UamException.class);
    }

    @Test
    void goodRedirectUrisPass() {
        Set<String> uris = new LinkedHashSet<>(List.of(
                "https://app.example.com/callback", "HTTPS://app.example.com/cb?x=1", "http://localhost:4200/auth",
                "http://127.0.0.1:8080/cb", "https://sub.example.co.in:8443/a/b"));

        assertThatCode(() -> ApiClientValidator.requireValidRedirectUris(uris)).doesNotThrowAnyException();
        assertThatCode(() -> ApiClientValidator.requireValidRedirectUris(null)).doesNotThrowAnyException();
        assertThatCode(() -> ApiClientValidator.requireValidRedirectUris(Set.of())).doesNotThrowAnyException();
    }

    @Test
    void dangerousOrMalformedRedirectUrisAreRefused() {
        for (String uri : List.of("javascript:alert(1)", "JavaScript:alert(1)", "data:text/html;base64,AAAA", "file:///etc/passwd",
                "ftp://example.com/x", "myapp://callback", "//example.com/cb", "/relative/path", "example.com/cb", "https://",
                "https:///path-only", "https://user:pw@example.com/cb", "https://user@example.com/cb", "https://example.com/cb#frag",
                "http://example.com/cb", "http://localhost.evil.com/cb", "https://exa mple.com/cb", "https://example.com/\u0001")) {
            assertThatThrownBy(() -> ApiClientValidator.requireValidRedirectUris(Set.of(uri))).as(uri)
                    .isInstanceOf(UamException.class).hasMessageContaining("Redirect URI");
        }
    }

    @Test
    void lengthAndCountLimitsAreEnforcedAtTheBoundary() {
        String atLimit = "https://example.com/" + "a".repeat(ApiClientConstants.MAX_REDIRECT_URI_LENGTH - "https://example.com/".length());
        assertThat(atLimit).hasSize(ApiClientConstants.MAX_REDIRECT_URI_LENGTH);
        assertThatCode(() -> ApiClientValidator.requireValidRedirectUris(Set.of(atLimit))).doesNotThrowAnyException();
        assertThatThrownBy(() -> ApiClientValidator.requireValidRedirectUris(Set.of(atLimit + "a")))
                .isInstanceOf(UamException.class).hasMessageContaining("longer than");

        Set<String> twenty = IntStream.range(0, ApiClientConstants.MAX_REDIRECT_URIS).mapToObj(i -> "https://e" + i + ".example.com/cb")
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertThatCode(() -> ApiClientValidator.requireValidRedirectUris(twenty)).doesNotThrowAnyException();
        twenty.add("https://one-too-many.example.com/cb");
        assertThatThrownBy(() -> ApiClientValidator.requireValidRedirectUris(twenty))
                .isInstanceOf(UamException.class).hasMessageContaining("at most 20");
    }

    @Test
    void theMessageNeverEchoesControlCharactersOrAWholeLongValue() {
        String hostile = "javascript:" + "x".repeat(300) + "\n<script>";

        assertThatThrownBy(() -> ApiClientValidator.requireValidRedirectUris(Set.of(hostile)))
                .isInstanceOf(UamException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("\n").hasSizeLessThan(200));
    }
}
