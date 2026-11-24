package com.heuristiq.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class JavadocIndexConfigTest {

    private final WebTestClient client = WebTestClient.bindToRouterFunction(new JavadocIndexConfig().javadocIndexRedirect()).build();

    @Test
    void javadocRedirectsToTheStaticIndex() {
        client.get().uri("/javadoc").exchange().expectStatus().isTemporaryRedirect()
                .expectHeader().valueEquals("Location", "/javadoc/index.html");
    }

    @Test
    void trailingSlashRedirectsToo() {
        client.get().uri("/javadoc/").exchange().expectStatus().isTemporaryRedirect()
                .expectHeader().valueEquals("Location", "/javadoc/index.html");
    }

    @Test
    void otherPathsAreNotHandled() {
        client.get().uri("/other").exchange().expectStatus().isNotFound();
    }
}
