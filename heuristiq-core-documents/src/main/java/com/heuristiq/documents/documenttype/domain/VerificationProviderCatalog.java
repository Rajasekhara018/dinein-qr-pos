package com.heuristiq.documents.documenttype.domain;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Assignable vendors, not live connections. Real adapters must still advertise the exact method and be configured. */
public enum VerificationProviderCatalog {
    SIGNZY(VerificationMethod.AADHAAR_DIGILOCKER, VerificationMethod.PAN_VERIFICATION,
            VerificationMethod.DRIVING_LICENCE_SARATHI, VerificationMethod.VEHICLE_RC_VAHAN),
    IDFY(VerificationMethod.PAN_VERIFICATION, VerificationMethod.DRIVING_LICENCE_SARATHI, VerificationMethod.VEHICLE_RC_VAHAN),
    KARZA(VerificationMethod.AADHAAR_DIGILOCKER, VerificationMethod.PAN_VERIFICATION,
            VerificationMethod.DRIVING_LICENCE_SARATHI, VerificationMethod.VEHICLE_RC_VAHAN, VerificationMethod.BANK_PENNY_DROP),
    EKO(VerificationMethod.DRIVING_LICENCE_SARATHI, VerificationMethod.VEHICLE_RC_VAHAN),
    EKYCNOW(VerificationMethod.AADHAAR_DIGILOCKER, VerificationMethod.PAN_VERIFICATION),
    CASHFREE(VerificationMethod.AADHAAR_DIGILOCKER, VerificationMethod.PAN_VERIFICATION,
            VerificationMethod.DRIVING_LICENCE_SARATHI, VerificationMethod.VEHICLE_RC_VAHAN, VerificationMethod.BANK_PENNY_DROP),
    RAZORPAY(VerificationMethod.BANK_PENNY_DROP);

    private final Set<VerificationMethod> methods;

    VerificationProviderCatalog(VerificationMethod... methods) { this.methods = Set.of(methods); }

    public boolean supports(VerificationMethod method) { return methods.contains(method); }

    public static VerificationProviderCatalog parse(String code) {
        if (code == null || code.isBlank()) throw DocumentTypeException.invalid("Verification provider is empty");
        try { return valueOf(code.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw DocumentTypeException.invalid("Unknown verification provider: " + code.trim()); }
    }

    /** One default assignment per type. The editor can offer all compatible vendors. */
    public static String defaultProvider(VerificationMethod method) {
        return switch (method) {
            case AADHAAR_DIGILOCKER -> "SIGNZY";
            case PAN_VERIFICATION, VEHICLE_RC_VAHAN -> "KARZA";
            case DRIVING_LICENCE_SARATHI -> "IDFY";
            case BANK_PENNY_DROP -> "CASHFREE";
            default -> null;
        };
    }
}
