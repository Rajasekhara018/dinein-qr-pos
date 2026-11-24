package com.heuristiq.search.provider.solr;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings of the Apache Solr provider (used when {@code railcab.search.provider=solr}).
 *
 * @param baseUrl        Solr's base URL, e.g. {@code http://localhost:8983/solr}
 * @param collection     the collection (core) holding the documents
 * @param connectTimeout how long to wait to connect
 * @param readTimeout    how long to wait for an answer
 */
@ConfigurationProperties(prefix = "railcab.search.solr")
public record SolrProperties(String baseUrl, String collection, Duration connectTimeout, Duration readTimeout) {

    public SolrProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8983/solr" : baseUrl.replaceAll("/+$", "");
        collection = collection == null || collection.isBlank() ? "railcab-search" : collection;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(10) : readTimeout;
    }

    /** The collection's URL: {@code <baseUrl>/<collection>}. */
    public String collectionUrl() {
        return baseUrl + "/" + collection;
    }
}
