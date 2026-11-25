package com.heuristiq.documents.storage.aws;

import com.heuristiq.documents.config.DmsProperties;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Lazily builds and caches the S3-compatible client/presigner pair for one set of credentials
 * and endpoint. Owned by composition (one instance per provider) rather than inherited, so any
 * S3-compatible provider (AWS, MinIO, ...) can reuse the client-building logic without depending
 * on a concrete sibling provider class.
 */
public class S3ClientFactory {

    private final DmsProperties.Storage storageSettings;
    private volatile S3Client client;
    private volatile S3Presigner presigner;

    public S3ClientFactory(DmsProperties.Storage storageSettings) {
        this.storageSettings = storageSettings;
    }

    public S3Client client(DmsProperties.AwsS3 settings, URI endpointOverride) {
        S3Client existing = client;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (client == null) {
                client = buildClient(settings, endpointOverride);
            }
            return client;
        }
    }

    public S3Presigner presigner(DmsProperties.AwsS3 settings, URI endpointOverride) {
        S3Presigner existing = presigner;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (presigner == null) {
                presigner = buildPresigner(settings, endpointOverride);
            }
            return presigner;
        }
    }

    private S3Client buildClient(DmsProperties.AwsS3 settings, URI endpointOverride) {
        ClientOverrideConfiguration overrideConfiguration = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(storageSettings.getOperationTimeoutSeconds()))
                .apiCallAttemptTimeout(Duration.ofSeconds(storageSettings.getAttemptTimeoutSeconds()))
                .build();
        S3ClientBuilder builder = S3Client.builder().overrideConfiguration(overrideConfiguration);
        configureCommonSettings(
                settings,
                endpointOverride,
                builder::region,
                builder::credentialsProvider,
                uri -> builder.endpointOverride(uri).forcePathStyle(true)
        );
        return builder.build();
    }

    private S3Presigner buildPresigner(DmsProperties.AwsS3 settings, URI endpointOverride) {
        S3Presigner.Builder builder = S3Presigner.builder();
        configureCommonSettings(
                settings,
                endpointOverride,
                builder::region,
                builder::credentialsProvider,
                uri -> builder.endpointOverride(uri)
                        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        );
        return builder.build();
    }

    /**
     * Shared region/credential/endpoint-override construction logic used by both
     * {@link #buildClient} and {@link #buildPresigner}. Each caller supplies its own
     * builder-specific setters via method references/lambdas so the two builder types
     * (which don't share a common fluent interface in the AWS SDK) can both reuse the
     * exact same branching without duplicating it.
     */
    private void configureCommonSettings(
            DmsProperties.AwsS3 settings,
            URI endpointOverride,
            Consumer<Region> regionSetter,
            Consumer<AwsCredentialsProvider> credentialsSetter,
            Consumer<URI> endpointOverrideSetter
    ) {
        regionSetter.accept(Region.of(settings.getRegion()));
        if (endpointOverride != null) {
            endpointOverrideSetter.accept(endpointOverride);
        }
        if (StringUtils.hasText(settings.getAccessKey()) && StringUtils.hasText(settings.getSecretKey())) {
            credentialsSetter.accept(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(settings.getAccessKey(), settings.getSecretKey())
            ));
        }
    }

    public void close() {
        S3Client existingClient = client;
        if (existingClient != null) {
            existingClient.close();
        }
        S3Presigner existingPresigner = presigner;
        if (existingPresigner != null) {
            existingPresigner.close();
        }
    }
}
