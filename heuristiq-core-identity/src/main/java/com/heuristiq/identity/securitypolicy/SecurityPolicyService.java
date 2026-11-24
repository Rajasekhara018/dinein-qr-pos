package com.heuristiq.uam.securitypolicy;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.feature.management.ManagementPageSupport;
import com.heuristiq.uam.helper.AuditInfo;
import com.heuristiq.uam.security.TenantGuard;
import com.heuristiq.uam.securitypolicy.repository.SecurityPolicyRepository;
import com.heuristiq.uam.common.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class SecurityPolicyService implements SecurityPolicyServiceUseCase {
    private final SecurityPolicyRepository securityPolicyRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private SecurityPolicyAccess access;
    @org.springframework.beans.factory.annotation.Autowired
    private EffectiveSecurityPolicyResolver resolver;
    @org.springframework.beans.factory.annotation.Autowired
    private com.heuristiq.uam.audit.AuditLogServiceUseCase audit;
    @org.springframework.beans.factory.annotation.Autowired
    private com.fasterxml.jackson.databind.ObjectMapper mapper;

    public SecurityPolicyService(SecurityPolicyRepository securityPolicyRepository) {
        this.securityPolicyRepository = securityPolicyRepository;
    }

    public CreateOutput create(CreateInput input) {
        UpsertSecurityPolicyRequest request = input.request();
        SecurityPolicyValidator.validate(request);
        String tenantId = normalizeOptional(request.tenantId());
        String applicationId = normalizeOptional(request.applicationId());
        if (access != null) access.require("CREATE", tenantId);
        securityPolicyRepository.findByTenantIdAndApplicationId(tenantId, applicationId).ifPresent(existing -> {
            throw UamException.conflict("Security policy already exists for this tenant/application");
        });
        LocalDateTime now = LocalDateTime.now();
        SecurityPolicy policy = SecurityPolicy.builder()
                .tenantId(tenantId)
                .applicationId(applicationId)
                .name(normalizeRequired(request.name()))
                .description(normalizeOptional(request.description()))
                .passwordPolicy(request.passwordPolicy())
                .loginPolicy(request.loginPolicy())
                .sessionPolicy(request.sessionPolicy())
                .mfaPolicy(request.mfaPolicy())
                .apiSecurityPolicy(request.apiSecurityPolicy())
                .accountPolicy(request.accountPolicy())
                .active(request.active() == null || request.active())
                .effectiveFrom(request.effectiveFrom())
                .effectiveUntil(request.effectiveUntil())
                .audit(AuditInfo.builder().createdAt(now).updatedAt(now).build())
                .build();
        if (resolver != null) resolver.validateChange(policy);
        SecurityPolicy saved = securityPolicyRepository.save(policy);
        recordChange(saved, null, "CREATE");
        return new CreateOutput(saved);
    }

    public UpdateOutput update(UpdateInput input) {
        String id = input.id();
        UpsertSecurityPolicyRequest request = input.request();
        SecurityPolicyValidator.validate(request);
        SecurityPolicy policy = get(new GetInput(id)).policy();
        java.util.Map<String, Object> before = snapshot(policy);
        if (access != null) access.require("UPDATE", policy.getTenantId());
        if (request.version() != null && !request.version().equals(policy.getVersion()))
            throw UamException.conflict("Security policy changed; reload it before saving");
        if (access != null && policy.getVersion() != null && request.version() == null)
            throw UamException.conflict("Reload the security policy before saving its current version");
        String tenantId = normalizeOptional(request.tenantId());
        String applicationId = normalizeOptional(request.applicationId());
        if (access != null) access.require("UPDATE", tenantId);
        securityPolicyRepository.findByTenantIdAndApplicationId(tenantId, applicationId)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw UamException.conflict("Security policy already exists for this tenant/application");
                });
        policy.setTenantId(tenantId);
        policy.setApplicationId(applicationId);
        policy.setName(normalizeRequired(request.name()));
        policy.setDescription(normalizeOptional(request.description()));
        policy.setPasswordPolicy(request.passwordPolicy());
        policy.setLoginPolicy(request.loginPolicy());
        policy.setSessionPolicy(request.sessionPolicy());
        policy.setMfaPolicy(request.mfaPolicy());
        policy.setApiSecurityPolicy(request.apiSecurityPolicy());
        policy.setAccountPolicy(request.accountPolicy());
        policy.setActive(request.active() == null || request.active());
        policy.setEffectiveFrom(request.effectiveFrom());
        policy.setEffectiveUntil(request.effectiveUntil());
        if (resolver != null) resolver.validateChange(policy);
        if (policy.getAudit() != null) {
            policy.getAudit().setUpdatedAt(LocalDateTime.now());
        }
        SecurityPolicy saved = securityPolicyRepository.save(policy);
        recordChange(saved, before, "UPDATE");
        return new UpdateOutput(saved);
    }

    public GetOutput get(GetInput input) {
        SecurityPolicy policy = securityPolicyRepository.findById(input.id())
                .orElseThrow(() -> UamException.notFound("Security policy not found"));
        TenantGuard.assertAccessible(policy.getTenantId());
        if (access != null) access.require("READ", policy.getTenantId());
        return new GetOutput(policy);
    }

    public ListOutput list(ListInput input) {
        String tenant = access == null ? null : access.listTenant(null);
        return new ListOutput(securityPolicyRepository.findAll().stream()
                .filter(policy -> tenant == null || tenant.equals(policy.getTenantId())).toList());
    }

    public ListPageOutput list(ListPageInput input) {
        var pageable = ManagementPageSupport.page(input.page(), input.size(), "name");
        String tenant = access == null ? input.tenantId() : access.listTenant(input.tenantId());
        if (StringUtils.hasText(input.name()) || StringUtils.hasText(tenant)) {
            return new ListPageOutput(PageResult.from(securityPolicyRepository.search(input.name(), tenant, pageable)));
        }
        return new ListPageOutput(PageResult.from(securityPolicyRepository.findAll(pageable)));
    }

    public DeleteOutput delete(DeleteInput input) {
        SecurityPolicy policy = get(new GetInput(input.id())).policy();
        if (access != null) access.require("DELETE", policy.getTenantId());
        securityPolicyRepository.delete(policy);
        recordChange(policy, snapshot(policy), "DELETE");
        return new DeleteOutput();
    }

    private java.util.Map<String, Object> snapshot(SecurityPolicy policy) {
        return mapper == null ? null : mapper.convertValue(policy, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
    }

    private void recordChange(SecurityPolicy policy, java.util.Map<String, Object> before, String operation) {
        if (audit == null) return;
        var actor = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        audit.record(new com.heuristiq.uam.audit.AuditLogServiceUseCase.RecordInput(com.heuristiq.uam.audit.AuditLog.builder()
                .tenantId(policy.getTenantId()).applicationId(policy.getApplicationId())
                .userId(actor == null ? null : actor.getName()).entityType("SECURITY_POLICY").entityId(policy.getId())
                .action(com.heuristiq.uam.enums.UamEnums.AuditAction.valueOf(operation))
                .status(com.heuristiq.uam.enums.UamEnums.AuditStatus.SUCCESS)
                .oldValue(before).newValue("DELETE".equals(operation) ? null : snapshot(policy))
                .remarks("Security policy " + operation + "; version=" + policy.getVersion())
                .createdAt(LocalDateTime.now()).build()));
    }

    private String normalizeRequired(String value) {
        if (!StringUtils.hasText(value)) {
            throw new UamException("Security policy name is required");
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
