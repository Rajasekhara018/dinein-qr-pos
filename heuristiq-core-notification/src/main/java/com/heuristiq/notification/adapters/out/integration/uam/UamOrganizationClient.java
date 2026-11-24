package com.heuristiq.notification.adapters.out.integration.uam;

import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.heuristiq.notification.application.dto.OrganizationProfile;
import com.heuristiq.notification.application.port.out.OrganizationProfileSource;
import com.heuristiq.notification.infrastructure.config.NmsProperties;
import com.heuristiq.resilient.Idempotency;
import com.heuristiq.resilient.ResilientCaller;

import lombok.RequiredArgsConstructor;

/**
 * Reads an organization's name and support details from UAM (a GET, so it may be retried). It does no caching and no
 * degrading of its own: answers are cached, and failures turned into "no profile", by
 * {@code CachingOrganizationProfileResolver}. A failure is thrown (never returned as an empty result) so it cannot be
 * mistaken for "organization not found".
 */
@Component
@RequiredArgsConstructor
public class UamOrganizationClient implements OrganizationProfileSource {

    private final NmsProperties props;
    private final WebClient webClient;
    private final UamServiceTokenProvider tokenProvider;
    private final ResilientCaller uamResilientCaller;

    @Override
    public Optional<OrganizationProfile> fetch(String tenantId) {
        UamResponse response;
        try {
            response = uamResilientCaller.callBlocking(() -> webClient.get()
                    .uri(props.getIntegration().getUamBaseUrl() + UamClientConstants.ORGANIZATION_PATH, tenantId)
                    .header(HttpHeaders.AUTHORIZATION, UamClientConstants.BEARER_PREFIX + tokenProvider.mintServiceToken())
                    .retrieve()
                    .bodyToMono(UamResponse.class), Idempotency.SAFE);
        } catch (WebClientResponseException.NotFound notFound) {
            // UAM answered "no such organization": a real answer, unlike an outage.
            return Optional.empty();
        }
        if (response == null || !Boolean.TRUE.equals(response.success()) || response.responseObject() == null) {
            return Optional.empty();
        }
        Organization org = response.responseObject();
        return Optional.of(new OrganizationProfile(org.name(), org.code(), org.supportEmail(), org.supportPhone()));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UamResponse(Boolean success, Organization responseObject) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Organization(String name, String code, String supportEmail, String supportPhone) {}
}
