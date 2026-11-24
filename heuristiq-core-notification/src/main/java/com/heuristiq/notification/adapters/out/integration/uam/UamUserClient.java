package com.railcab.nms.adapters.out.integration.uam;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.railcab.nms.application.dto.UamUserSummary;
import com.railcab.nms.application.port.out.AudienceResolutionException;
import com.railcab.nms.application.port.out.BroadcastAudienceResolver;
import com.railcab.nms.infrastructure.config.NmsProperties;
import com.railcab.nms.domain.model.BroadcastSegment;

import com.heuristiq.resilient.Idempotency;
import com.heuristiq.resilient.ResilientCaller;

import lombok.RequiredArgsConstructor;

/**
 * Resolves broadcast audiences against UAM -- the only identity source NMS is allowed to depend
 * on (see the migration plan). UAM's {@code GET /users} has no server-side filter by role/status,
 * so this pages through everything once and filters client-side.
 *
 * Deliberately absent: a VENDORS segment (vendor identities were never modeled in UAM) and any
 * city/last-active-day filtering (UAM carries no such fields on UamUser). Both are scope cuts
 * confirmed with the user, not oversights.
 *
 * Failure policy: every page is fetched through a {@link ResilientCaller} (GET, so SAFE to retry). If a
 * page still cannot be fetched -- or the sweep would need more pages than the configured limit -- an
 * {@link AudienceResolutionException} is thrown. A partial audience is never returned, because a
 * broadcast sent to half its segment looks like a success but silently skips users.
 */
@Component
@RequiredArgsConstructor
public class UamUserClient implements BroadcastAudienceResolver {

    private static final Logger logger = LoggerFactory.getLogger(UamUserClient.class);

    private final NmsProperties props;
    private final WebClient webClient;
    private final UamServiceTokenProvider tokenProvider;
    private final ResilientCaller uamResilientCaller;

    @Override
    public List<UamUserSummary> resolveSegment(BroadcastSegment segment) {
        List<UamUserSummary> all = fetchAllActiveUsers();
        return switch (segment) {
            case ALL -> all;
            case USERS -> all.stream().filter(u -> hasRole(u, UamClientConstants.ROLE_CUSTOMER)).toList();
            case DRIVERS -> all.stream().filter(u -> hasRole(u, UamClientConstants.ROLE_DRIVER)).toList();
            case ADMINS -> all.stream().filter(this::isAdminPanelUser).toList();
        };
    }

    private boolean hasRole(UamUserSummary user, String role) {
        return user.roles() != null && user.roles().stream().anyMatch(r -> r.equalsIgnoreCase(role));
    }

    private boolean isAdminPanelUser(UamUserSummary user) {
        if (user.roles() == null) {
            return false;
        }
        return user.roles().stream().anyMatch(r -> UamClientConstants.ADMIN_PANEL_ROLES.contains(r.toUpperCase()));
    }

    private List<UamUserSummary> fetchAllActiveUsers() {
        List<UamUserSummary> result = new ArrayList<>();
        String token = tokenProvider.mintServiceToken();
        int page = 0;
        int maxPages = props.getBroadcast().getMaxPagesPerSweep();
        int size = props.getBroadcast().getPageSize();

        while (page < maxPages) {
            UamPage pageResult = fetchPage(token, page, size);
            pageResult.content().stream()
                    .filter(u -> !Boolean.FALSE.equals(u.active()))
                    .forEach(result::add);
            if (Boolean.TRUE.equals(pageResult.last())) {
                return result;
            }
            page++;
        }
        throw new AudienceResolutionException("UAM user listing exceeded " + maxPages + " pages ("
                + (maxPages * size) + " users) without reaching the last page; refusing to send to a truncated audience");
    }

    private UamPage fetchPage(String token, int page, int size) {
        UamCommonResponse response;
        try {
            response = uamResilientCaller.callBlocking(() -> webClient.get()
                    .uri(props.getIntegration().getUamBaseUrl() + UamClientConstants.USERS_PAGE_PATH, page, size)
                    .header(HttpHeaders.AUTHORIZATION, UamClientConstants.BEARER_PREFIX + token)
                    .retrieve()
                    .bodyToMono(UamCommonResponse.class), Idempotency.SAFE);
        } catch (RuntimeException e) {
            logger.error("UamUserClient: failed to fetch users page={}: {}", page, e.getMessage(), e);
            throw new AudienceResolutionException("UAM users page " + page + " could not be fetched: " + e.getMessage(), e);
        }
        if (response == null || !Boolean.TRUE.equals(response.success())
                || response.responseObject() == null || response.responseObject().content() == null) {
            logger.error("UamUserClient: UAM returned an unusable response for users page={}", page);
            throw new AudienceResolutionException("UAM returned an unusable response for users page " + page);
        }
        return response.responseObject();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UamCommonResponse(Boolean success, String message, UamPage responseObject) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UamPage(List<UamUserSummary> content, Boolean last) {}
}
