package com.heuristiq.documents.scan.metadefender;

import com.heuristiq.documents.config.DmsProperties;
import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * Dedicated WebClient for outbound Metadefender scan calls, mirroring PaymentWebClientConfig's
 * bounded-connection-pool + explicit timeout pattern rather than relying on WebClient defaults
 * (which have no timeout and would let a stalled scan API hang an upload indefinitely).
 */
@Configuration
public class MetadefenderWebClientConfig {

    private final DmsProperties properties;

    public MetadefenderWebClientConfig(DmsProperties properties) {
        this.properties = properties;
    }

    @Bean(MetadefenderConstants.WEB_CLIENT_BEAN)
    public WebClient metadefenderWebClient(WebClient.Builder builder) {
        DmsProperties.Metadefender config = properties.getScan().getMetadefender();

        ConnectionProvider connectionProvider = ConnectionProvider.builder(MetadefenderConstants.CONNECTION_POOL_NAME)
                .maxConnections(MetadefenderConstants.MAX_CONNECTIONS)
                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeoutMs())
                .responseTimeout(Duration.ofMillis(config.getResponseTimeoutMs()));

        return builder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
