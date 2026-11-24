package com.heuristiq.uam.application.client;

import com.heuristiq.uam.application.client.ApiClient;

public record ApiClientCreated(ApiClient client, String clientSecret) {
}
