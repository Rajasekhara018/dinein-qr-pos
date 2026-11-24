package com.heuristiq.uam.application.client;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.application.client.ApiClient;
import com.heuristiq.uam.application.client.ApiClientCreated;
import com.heuristiq.uam.application.client.UpsertClientRequest;
import com.heuristiq.uam.feature.management.ManagementPageSupport;
import com.heuristiq.uam.helper.AuditInfo;
import com.heuristiq.uam.identity.IdentityProviderSyncService;
import com.heuristiq.uam.application.client.repository.ApiClientRepository;
import com.heuristiq.uam.security.PasswordService;
import com.heuristiq.uam.security.TenantGuard;
import com.heuristiq.uam.common.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ApiClientService implements ApiClientServiceUseCase {
    private final ApiClientRepository clientRepository;
    private final PasswordService passwordService;
    private final IdentityProviderSyncService identityProviderSyncService;
    private final ClientTenantResolver tenantResolver;
    private final SecureRandom random = new SecureRandom();

    /** Without an organization lookup the tenant of a request is used as typed, trimmed (for tests of the other rules). */
    public ApiClientService(ApiClientRepository clientRepository,
                            PasswordService passwordService,
                            IdentityProviderSyncService identityProviderSyncService) {
        this(clientRepository, passwordService, identityProviderSyncService, requested -> StringUtils.hasText(requested) ? requested.trim() : null);
    }

    @Autowired
    public ApiClientService(ApiClientRepository clientRepository,
                            PasswordService passwordService,
                            IdentityProviderSyncService identityProviderSyncService,
                            ClientTenantResolver tenantResolver) {
        this.clientRepository = clientRepository;
        this.passwordService = passwordService;
        this.identityProviderSyncService = identityProviderSyncService;
        this.tenantResolver = tenantResolver;
    }

    public CreateOutput create(CreateInput input) {
        UpsertClientRequest request = input.request();
        String clientId = normalizeRequired(request.clientId(), "Client id is required");
        ApiClientValidator.requireValidClientId(clientId);
        ApiClientValidator.requireValidRedirectUris(normalizeSet(request.redirectUris()));
        String tenantId = tenantResolver.resolve(request.tenantId());
        clientRepository.findByClientIdIgnoreCase(clientId).ifPresent(existing -> {
            throw UamException.conflict("Client already exists");
        });
        String secret = generateSecret();
        LocalDateTime now = LocalDateTime.now();
        ApiClient client = ApiClient.builder()
                .clientId(clientId)
                .tenantId(tenantId)
                .applicationId(normalizeOptional(request.applicationId()))
                .name(defaultName(request.name(), clientId))
                .description(normalizeOptional(request.description()))
                .clientSecretHash(passwordService.hash(secret))
                .clientType(request.clientType())
                .authenticationMethod(request.authenticationMethod())
                .grantTypes(request.grantTypes())
                .scopes(normalizeSet(request.scopes()))
                .redirectUris(normalizeSet(request.redirectUris()))
                .active(request.active() == null || request.active())
                .audit(AuditInfo.builder().createdAt(now).updatedAt(now).build())
                .build();
        ApiClient saved = clientRepository.save(client);
        identityProviderSyncService.syncClient(saved, secret);
        return new CreateOutput(new ApiClientCreated(saved, secret));
    }

    public UpdateOutput update(UpdateInput input) {
        String id = input.id();
        UpsertClientRequest request = input.request();
        ApiClient client = get(new GetInput(id)).client();
        String clientId = normalizeRequired(request.clientId(), "Client id is required");
        ApiClientValidator.requireValidClientId(clientId);
        ApiClientValidator.requireValidRedirectUris(normalizeSet(request.redirectUris()));
        String tenantId = tenantResolver.resolve(request.tenantId());
        clientRepository.findByClientIdIgnoreCase(clientId)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw UamException.conflict("Client already exists");
                });
        client.setClientId(clientId);
        client.setTenantId(tenantId);
        client.setApplicationId(normalizeOptional(request.applicationId()));
        client.setName(defaultName(request.name(), clientId));
        client.setDescription(normalizeOptional(request.description()));
        client.setClientType(request.clientType());
        client.setAuthenticationMethod(request.authenticationMethod());
        client.setGrantTypes(request.grantTypes());
        client.setScopes(normalizeSet(request.scopes()));
        client.setRedirectUris(normalizeSet(request.redirectUris()));
        client.setActive(request.active() == null || request.active());
        if (client.getAudit() != null) {
            client.getAudit().setUpdatedAt(LocalDateTime.now());
        }
        ApiClient saved = clientRepository.save(client);
        identityProviderSyncService.syncClient(saved);
        return new UpdateOutput(saved);
    }

    public RotateSecretOutput rotateSecret(RotateSecretInput input) {
        ApiClient client = get(new GetInput(input.id())).client();
        String secret = generateSecret();
        client.setClientSecretHash(passwordService.hash(secret));
        if (client.getAudit() != null) {
            client.getAudit().setUpdatedAt(LocalDateTime.now());
        }
        ApiClient saved = clientRepository.save(client);
        identityProviderSyncService.syncClient(saved, secret);
        return new RotateSecretOutput(secret);
    }

    public GetOutput get(GetInput input) {
        ApiClient client = clientRepository.findById(input.id()).orElseThrow(() -> UamException.notFound("Client not found"));
        TenantGuard.assertAccessible(client.getTenantId());
        return new GetOutput(client);
    }

    public ListOutput list(ListInput input) {
        return new ListOutput(clientRepository.findAll(Sort.by(Sort.Direction.ASC, "clientId")));
    }

    public ListPageOutput list(ListPageInput input) {
        var pageable = ManagementPageSupport.page(input.page(), input.size(), "clientId");
        if (StringUtils.hasText(input.clientId()) || input.clientType() != null || input.active() != null) {
            return new ListPageOutput(PageResult.from(
                    clientRepository.search(input.clientId(), input.clientType(), input.active(), pageable)));
        }
        return new ListPageOutput(PageResult.from(clientRepository.findAll(pageable)));
    }

    public DeleteOutput delete(DeleteInput input) {
        ApiClient client = get(new GetInput(input.id())).client();
        identityProviderSyncService.deleteClient(client);
        clientRepository.delete(client);
        return new DeleteOutput();
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new UamException(message);
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultName(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private Set<String> normalizeSet(Set<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        if (values != null) {
            values.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(normalized::add);
        }
        return normalized;
    }
}
