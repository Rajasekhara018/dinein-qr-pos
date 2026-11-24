package com.heuristiq.uam.security;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.heuristiq.resilient.DownstreamUnavailableException;
import com.heuristiq.resilient.Idempotency;
import com.heuristiq.resilient.ResilientCaller;

/**
 * The franchises a person owns or is an agent of, asked of Rider_FranchiseService
 * ({@code GET /api/v1/franchise/internal/claims}) through a {@link ResilientCaller} (timeout, bulkhead, circuit breaker, retry
 * of this safe read). It never answers an empty list for a failure: any error, an unreadable answer or an open circuit is
 * thrown, so a sign-in fails rather than issuing a token without franchises that would read as "unrestricted".
 * With {@code railcab.uam.franchise.service.enabled=false} nobody has franchises (for setups without the service).
 */
public class RemoteFranchiseClaims implements FranchiseClaims {

    /** The part of the franchise service's response envelope this lookup reads. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Envelope(Boolean success, String message, List<String> responseObject) {
    }

    /** Name of the downstream in an unavailable error. */
    private static final String DOWNSTREAM = "franchise-service";

    private final WebClient client;
    private final ResilientCaller caller;
    private final String serviceToken;
    private final boolean enabled;

    public RemoteFranchiseClaims(WebClient client, ResilientCaller caller, String serviceToken, boolean enabled) {
        this.client = client;
        this.caller = caller;
        this.serviceToken = serviceToken;
        this.enabled = enabled;
    }

    @Override
    public boolean lookupEnabled() {
        return enabled;
    }

    /** Ids of the user's ACTIVE franchises only: the franchise service leaves out closed, suspended and pending ones. */
    @Override
    public List<String> franchiseIdsOf(String tenantId, String userId) {
        if (!enabled) {
            return List.of();
        }
        Envelope answer;
        try {
            answer = lookup(tenantId, userId);
        } catch (WebClientResponseException refused) {
            // The service answered with an error (for example it does not accept this service's token): for a sign-in that is the
            // same as no answer, and must be reported as unavailable, not as an unexpected failure.
            throw new DownstreamUnavailableException(DOWNSTREAM, DownstreamUnavailableException.Reason.FAILED, refused);
        }
        if (answer == null || !Boolean.TRUE.equals(answer.success()) || answer.responseObject() == null) {
            throw new DownstreamUnavailableException(DOWNSTREAM, DownstreamUnavailableException.Reason.FAILED,
                    new IllegalStateException("The franchise service gave no usable answer to the franchise claims lookup"));
        }
        return answer.responseObject();
    }

    /** The response envelope of the role lookup. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RolesEnvelope(Boolean success, RoleIds responseObject) {
    }

    /**
     * The owner and agent franchises of the user. Informational only (it restricts nothing), so a failure or an unreadable answer gives an
     * empty split instead of failing the sign-in; the combined franchiseIds claim, which does restrict, is still looked up and thrown on failure.
     */
    @Override
    public RoleIds roleIdsOf(String tenantId, String userId) {
        if (!enabled) {
            return new RoleIds(List.of(), List.of());
        }
        try {
            RolesEnvelope answer = caller.callBlocking(() -> client.get()
                    .uri(builder -> builder.path(SecurityConstants.FRANCHISE_ROLE_CLAIMS_PATH)
                            .queryParam(SecurityConstants.FRANCHISE_TENANT_PARAM, tenantId)
                            .queryParam(SecurityConstants.FRANCHISE_USER_PARAM, userId)
                            .build())
                    .headers(headers -> {
                        if (serviceToken != null && !serviceToken.isBlank()) {
                            headers.set(SecurityConstants.INTERNAL_SERVICE_TOKEN_HEADER, serviceToken);
                        }
                    })
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<RolesEnvelope>() { }), Idempotency.SAFE);
            if (answer == null || !Boolean.TRUE.equals(answer.success()) || answer.responseObject() == null) {
                return new RoleIds(List.of(), List.of());
            }
            return answer.responseObject();
        } catch (RuntimeException e) {
            // not thrown: the split only describes the person, the restricting claim is looked up on its own
            org.slf4j.LoggerFactory.getLogger(RemoteFranchiseClaims.class).warn("uam.franchise.role-claims.unavailable reason={}", e.getClass().getSimpleName());
            return new RoleIds(List.of(), List.of());
        }
    }

    private Envelope lookup(String tenantId, String userId) {
        return caller.callBlocking(() -> client.get()
                .uri(builder -> builder.path(SecurityConstants.FRANCHISE_CLAIMS_PATH)
                        .queryParam(SecurityConstants.FRANCHISE_TENANT_PARAM, tenantId)
                        .queryParam(SecurityConstants.FRANCHISE_USER_PARAM, userId)
                        .build())
                .headers(headers -> {
                    if (serviceToken != null && !serviceToken.isBlank()) {
                        headers.set(SecurityConstants.INTERNAL_SERVICE_TOKEN_HEADER, serviceToken);
                    }
                })
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Envelope>() { }), Idempotency.SAFE);
    }
}
