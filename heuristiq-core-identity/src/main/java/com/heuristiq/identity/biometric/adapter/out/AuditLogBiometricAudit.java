package com.heuristiq.uam.biometric.adapter.out;

import com.heuristiq.uam.audit.AuditLog;
import com.heuristiq.uam.audit.AuditLogServiceUseCase;
import com.heuristiq.uam.biometric.application.port.BiometricAudit;
import com.heuristiq.uam.biometric.domain.BiometricConstants;
import com.heuristiq.uam.biometric.domain.BiometricCredential;
import com.heuristiq.uam.biometric.domain.BiometricRevocationReason;
import com.heuristiq.uam.common.UamCommonConstants;
import com.heuristiq.uam.enums.UamEnums.AuditAction;
import com.heuristiq.uam.enums.UamEnums.AuditStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Writes biometric sign-in events to UAM's audit log (central audit outbox) as BIOMETRIC_ENABLED, BIOMETRIC_SIGNIN,
 * BIOMETRIC_SIGNIN_FAILED and BIOMETRIC_REVOKED. The entry names the credential, installation, application and platform,
 * never the public key, a signature or a challenge. A failed write is logged and never fails the action.
 */
public class AuditLogBiometricAudit implements BiometricAudit {

    private static final Logger log = LoggerFactory.getLogger(AuditLogBiometricAudit.class);

    private final AuditLogServiceUseCase auditLogs;

    public AuditLogBiometricAudit(AuditLogServiceUseCase auditLogs) {
        this.auditLogs = auditLogs;
    }

    @Override
    public void enabled(BiometricCredential credential) {
        Map<String, Object> details = details(credential, credential.installationId());
        details.put(BiometricConstants.AUDIT_FIELD_KEY_ALGORITHM, credential.keyAlgorithm().name());
        write(credential, AuditAction.BIOMETRIC_ENABLED, AuditStatus.SUCCESS, details);
    }

    @Override
    public void signedIn(BiometricCredential credential) {
        write(credential, AuditAction.BIOMETRIC_SIGNIN, AuditStatus.SUCCESS, details(credential, credential.installationId()));
    }

    @Override
    public void signInFailed(BiometricCredential credential, String installationId, String failure) {
        Map<String, Object> details = details(credential, installationId);
        details.put(BiometricConstants.AUDIT_FIELD_FAILURE, failure);
        write(credential, AuditAction.BIOMETRIC_SIGNIN_FAILED, AuditStatus.FAILURE, details);
    }

    @Override
    public void revoked(BiometricCredential credential, BiometricRevocationReason reason) {
        Map<String, Object> details = details(credential, credential.installationId());
        details.put(BiometricConstants.AUDIT_FIELD_REASON, reason.name());
        write(credential, AuditAction.BIOMETRIC_REVOKED, AuditStatus.SUCCESS, details);
    }

    private static Map<String, Object> details(BiometricCredential credential, String installationId) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put(BiometricConstants.AUDIT_FIELD_INSTALLATION_ID, installationId);
        if (credential != null) {
            details.put(BiometricConstants.AUDIT_FIELD_APPLICATION_CODE, credential.applicationCode());
            if (credential.platform() != null) {
                details.put(BiometricConstants.AUDIT_FIELD_PLATFORM, credential.platform().name());
            }
        }
        return details;
    }

    private void write(BiometricCredential credential, AuditAction action, AuditStatus status, Map<String, Object> details) {
        try {
            auditLogs.record(new AuditLogServiceUseCase.RecordInput(AuditLog.builder()
                    .tenantId(credential == null ? null : credential.tenantId())
                    .userId(credential == null ? null : credential.userId())
                    .entityType(BiometricConstants.AUDIT_ENTITY_TYPE)
                    .entityId(credential == null ? null : credential.credentialId())
                    .action(action)
                    .status(status)
                    .newValue(details)
                    .correlationId(MDC.get(UamCommonConstants.CORRELATION_ID_MDC_KEY))
                    .build()));
        } catch (RuntimeException e) {
            log.warn("biometric.audit.failed action={} reason={}", action, e.getClass().getSimpleName());
        }
    }
}
