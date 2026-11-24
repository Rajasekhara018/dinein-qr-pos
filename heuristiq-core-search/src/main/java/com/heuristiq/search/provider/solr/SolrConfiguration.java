package com.heuristiq.search.provider.solr;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/** Wiring of the Solr provider: one HTTP client for the collection, with the configured timeouts. */
@Configuration
@ConditionalOnProperty(prefix = "railcab.search", name = "provider", havingValue = "solr")
@EnableConfigurationProperties(SolrProperties.class)
class SolrConfiguration {

    @Bean
    RestClient solrRestClient(RestClient.Builder builder, SolrProperties properties) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(properties.readTimeout());
        return builder.requestFactory(requests).baseUrl(properties.collectionUrl()).build();
    }
}
