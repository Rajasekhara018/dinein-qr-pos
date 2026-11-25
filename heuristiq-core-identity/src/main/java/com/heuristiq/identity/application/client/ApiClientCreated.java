package com.heuristiq.identity.application.client;

import com.heuristiq.identity.application.client.ApiClient;

public record ApiClientCreated(ApiClient client, String clientSecret) {
}
