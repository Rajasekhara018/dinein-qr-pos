package com.railcab.nms.adapters.out.integration.uam;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.railcab.nms.application.dto.UamUserSummary;
import com.railcab.nms.application.port.out.UserPhoneDirectory;
import com.railcab.nms.infrastructure.config.NmsProperties;
import com.heuristiq.resilient.Idempotency;
import com.heuristiq.resilient.ResilientCaller;

import org.springframework.web.reactive.function.client.WebClient;

import lombok.RequiredArgsConstructor;

/**
 * Reads users' phone numbers from UAM ({@code GET /users/batch?ids=}, at most {@link UamClientConstants#USERS_BATCH_MAX} per call)
 * with NMS's service token, through the resilient caller (a read, so SAFE). An outage gives an empty answer and is logged: the
 * SMS is then skipped, never guessed.
 */
@Component
@RequiredArgsConstructor
public class UamUserPhoneClient implements UserPhoneDirectory {

    private static final Logger log = LoggerFactory.getLogger(UamUserPhoneClient.class);

    private final NmsProperties props;
    private final WebClient webClient;
    private final UamServiceTokenProvider tokenProvider;
    private final ResilientCaller uamResilientCaller;

    @Override
    public Map<String, String> phonesOf(Collection<String> userIds) {
        List<String> ids = userIds == null ? List.of()
                : userIds.stream().filter(Objects::nonNull).map(String::trim).filter(id -> !id.isEmpty()).distinct().toList();
        Map<String, String> phones = new LinkedHashMap<>();
        for (int from = 0; from < ids.size(); from += UamClientConstants.USERS_BATCH_MAX) {
            List<String> chunk = ids.subList(from, Math.min(from + UamClientConstants.USERS_BATCH_MAX, ids.size()));
            BatchResponse response;
            try {
                response = uamResilientCaller.callBlocking(() -> webClient.get()
                        .uri(props.getIntegration().getUamBaseUrl() + UamClientConstants.USERS_BATCH_PATH, String.join(",", chunk))
                        .header(HttpHeaders.AUTHORIZATION, UamClientConstants.BEARER_PREFIX + tokenProvider.mintServiceToken())
                        .retrieve()
                        .bodyToMono(BatchResponse.class), Idempotency.SAFE);
            } catch (RuntimeException e) {
                log.warn("UAM phone lookup failed for {} user(s); their SMS is skipped: {}", chunk.size(), e.getMessage());
                continue;
            }
            if (response == null || response.responseObject() == null) {
                continue;
            }
            for (User user : response.responseObject()) {
                String phone = user.phoneNumber() == null ? "" : user.phoneNumber().e164();
                if (user.id() != null && !phone.isEmpty()) {
                    phones.put(user.id(), phone);
                }
            }
        }
        return phones;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BatchResponse(Boolean success, List<User> responseObject) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record User(String id, UamUserSummary.PhoneNumber phoneNumber) {}
}
