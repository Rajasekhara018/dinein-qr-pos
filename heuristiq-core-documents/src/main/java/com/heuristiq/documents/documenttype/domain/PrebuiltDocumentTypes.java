package com.heuristiq.documents.documenttype.domain;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.heuristiq.documents.documenttype.domain.DocumentTypeConstants.CATEGORY_CUSTOMER_DOCUMENTS;
import static com.heuristiq.documents.documenttype.domain.DocumentTypeConstants.CATEGORY_DRIVER_DOCUMENTS;
import static com.heuristiq.documents.documenttype.domain.DocumentTypeConstants.CATEGORY_FRANCHISE_ONBOARDING;
import static com.heuristiq.documents.documenttype.domain.DocumentTypeConstants.FLOW_CUSTOMER_ONBOARDING;
import static com.heuristiq.documents.documenttype.domain.DocumentTypeConstants.FLOW_DRIVER_ONBOARDING;
import static com.heuristiq.documents.documenttype.domain.DocumentTypeConstants.FLOW_FRANCHISE_ONBOARDING;
import static com.heuristiq.documents.documenttype.domain.DocumentTypeConstants.PREBUILT_LARGE_FILE_SIZE_MB;
import static com.heuristiq.documents.documenttype.domain.DocumentTypeConstants.PREBUILT_AGREEMENT_FILE_SIZE_MB;
import static com.heuristiq.documents.documenttype.domain.DocumentTypeConstants.PREBUILT_FILE_SIZE_MB;
import static com.heuristiq.documents.documenttype.domain.DocumentTypeConstants.PREBUILT_IMAGE_FORMATS;
import static com.heuristiq.documents.documenttype.domain.DocumentTypeConstants.PREBUILT_DOCUMENT_FORMATS;
import static com.heuristiq.documents.documenttype.domain.DocumentTypeConstants.PREBUILT_AGREEMENT_FORMATS;
import static com.heuristiq.documents.documenttype.domain.DocumentTypeConstants.PREBUILT_FRANCHISE_PHOTO_FORMATS;
import static com.heuristiq.documents.documenttype.domain.DocumentTypeConstants.RETENTION_AGREEMENT_DAYS;
import static com.heuristiq.documents.documenttype.domain.DocumentTypeConstants.RETENTION_KYC_DAYS;

/**
 * The prebuilt document types every organization gets automatically, right after its {@code DEFAULT} type (customer,
 * driver and franchise onboarding documents). Pure: no I/O.
 *
 * <p>Like the default type, each is created by the system (no change request) and only when the organization has no
 * type of that code; afterwards it is an ordinary record that admins edit through the change-request flow.
 */
public final class PrebuiltDocumentTypes {


    private static final List<Entry> ENTRIES = List.of(
            entry(DocumentTypeConstants.CUSTOMER_PHOTO, CATEGORY_CUSTOMER_DOCUMENTS, FLOW_CUSTOMER_ONBOARDING,
                    DocumentTypeConstants.CUSTOMER_PHOTO_NAMES, PREBUILT_IMAGE_FORMATS, PREBUILT_FILE_SIZE_MB, false, null, null,
                    VerificationMethod.NONE),
            entry(DocumentTypeConstants.CUSTOMER_ID_PROOF, CATEGORY_CUSTOMER_DOCUMENTS, FLOW_CUSTOMER_ONBOARDING,
                    DocumentTypeConstants.CUSTOMER_ID_PROOF_NAMES, PREBUILT_DOCUMENT_FORMATS, PREBUILT_FILE_SIZE_MB, false, null,
                    RETENTION_KYC_DAYS, VerificationMethod.MANUAL_REVIEW),
            entry(DocumentTypeConstants.CUSTOMER_ADDRESS_PROOF, CATEGORY_CUSTOMER_DOCUMENTS, FLOW_CUSTOMER_ONBOARDING,
                    DocumentTypeConstants.CUSTOMER_ADDRESS_PROOF_NAMES, PREBUILT_DOCUMENT_FORMATS, PREBUILT_FILE_SIZE_MB, false, null,
                    RETENTION_KYC_DAYS, VerificationMethod.MANUAL_REVIEW),
            entry(DocumentTypeConstants.DRIVER_AADHAAR, CATEGORY_DRIVER_DOCUMENTS, FLOW_DRIVER_ONBOARDING,
                    DocumentTypeConstants.DRIVER_AADHAAR_NAMES, PREBUILT_DOCUMENT_FORMATS, PREBUILT_FILE_SIZE_MB, true, null,
                    RETENTION_KYC_DAYS, VerificationMethod.AADHAAR_DIGILOCKER),
            entry(DocumentTypeConstants.DRIVER_PAN, CATEGORY_DRIVER_DOCUMENTS, FLOW_DRIVER_ONBOARDING,
                    DocumentTypeConstants.DRIVER_PAN_NAMES, PREBUILT_DOCUMENT_FORMATS, PREBUILT_FILE_SIZE_MB, true, null,
                    RETENTION_KYC_DAYS, VerificationMethod.PAN_VERIFICATION),
            entry(DocumentTypeConstants.DRIVING_LICENCE, CATEGORY_DRIVER_DOCUMENTS, FLOW_DRIVER_ONBOARDING,
                    DocumentTypeConstants.DRIVING_LICENCE_NAMES, PREBUILT_DOCUMENT_FORMATS, PREBUILT_FILE_SIZE_MB, true,
                    DocumentTypeConstants.DRIVING_LICENCE_EXPIRY_YEARS, RETENTION_KYC_DAYS,
                    VerificationMethod.DRIVING_LICENCE_SARATHI),
            entry(DocumentTypeConstants.VEHICLE_RC, CATEGORY_DRIVER_DOCUMENTS, FLOW_DRIVER_ONBOARDING,
                    DocumentTypeConstants.VEHICLE_RC_NAMES, PREBUILT_DOCUMENT_FORMATS, PREBUILT_FILE_SIZE_MB, true,
                    DocumentTypeConstants.VEHICLE_RC_EXPIRY_YEARS, RETENTION_KYC_DAYS, VerificationMethod.VEHICLE_RC_VAHAN),
            entry(DocumentTypeConstants.VEHICLE_INSURANCE, CATEGORY_DRIVER_DOCUMENTS, FLOW_DRIVER_ONBOARDING,
                    DocumentTypeConstants.VEHICLE_INSURANCE_NAMES, PREBUILT_DOCUMENT_FORMATS, PREBUILT_FILE_SIZE_MB, true,
                    DocumentTypeConstants.ANNUAL_EXPIRY_YEARS, RETENTION_KYC_DAYS, VerificationMethod.MANUAL_REVIEW),
            entry(DocumentTypeConstants.VEHICLE_PUC, CATEGORY_DRIVER_DOCUMENTS, FLOW_DRIVER_ONBOARDING,
                    DocumentTypeConstants.VEHICLE_PUC_NAMES, PREBUILT_DOCUMENT_FORMATS, PREBUILT_FILE_SIZE_MB, true,
                    null, RETENTION_KYC_DAYS, VerificationMethod.MANUAL_REVIEW),
            // Optional: bike taxis and some vehicle classes have no permit; an organization can make it required. Its real
            // valid-until date is tracked per upload (driver-permit category), so no fixed expiry here.
            entry(DocumentTypeConstants.VEHICLE_PERMIT, CATEGORY_DRIVER_DOCUMENTS, FLOW_DRIVER_ONBOARDING,
                    DocumentTypeConstants.VEHICLE_PERMIT_NAMES, PREBUILT_DOCUMENT_FORMATS, PREBUILT_FILE_SIZE_MB, false,
                    null, RETENTION_KYC_DAYS, VerificationMethod.MANUAL_REVIEW),
            entry(DocumentTypeConstants.DRIVER_PHOTO, CATEGORY_DRIVER_DOCUMENTS, FLOW_DRIVER_ONBOARDING,
                    DocumentTypeConstants.DRIVER_PHOTO_NAMES, PREBUILT_IMAGE_FORMATS, PREBUILT_FILE_SIZE_MB, true, null, null,
                    VerificationMethod.NONE),
            entry(DocumentTypeConstants.DRIVER_BANK_PROOF, CATEGORY_DRIVER_DOCUMENTS, FLOW_DRIVER_ONBOARDING,
                    DocumentTypeConstants.DRIVER_BANK_PROOF_NAMES, PREBUILT_DOCUMENT_FORMATS, PREBUILT_FILE_SIZE_MB, true, null,
                    RETENTION_KYC_DAYS, VerificationMethod.BANK_PENNY_DROP),
            entry(DocumentTypeConstants.DRIVER_POLICE_VERIFICATION, CATEGORY_DRIVER_DOCUMENTS, FLOW_DRIVER_ONBOARDING,
                    DocumentTypeConstants.DRIVER_POLICE_VERIFICATION_NAMES, PREBUILT_DOCUMENT_FORMATS, PREBUILT_FILE_SIZE_MB, false,
                    DocumentTypeConstants.ANNUAL_EXPIRY_YEARS, RETENTION_KYC_DAYS, VerificationMethod.MANUAL_REVIEW),
            entry(DocumentTypeConstants.FRANCHISE_ID_PROOF, CATEGORY_FRANCHISE_ONBOARDING, FLOW_FRANCHISE_ONBOARDING,
                    DocumentTypeConstants.FRANCHISE_ID_PROOF_NAMES, PREBUILT_DOCUMENT_FORMATS, PREBUILT_FILE_SIZE_MB, true, null,
                    RETENTION_KYC_DAYS, VerificationMethod.MANUAL_REVIEW),
            entry(DocumentTypeConstants.FRANCHISE_ADDRESS_PROOF, CATEGORY_FRANCHISE_ONBOARDING, FLOW_FRANCHISE_ONBOARDING,
                    DocumentTypeConstants.FRANCHISE_ADDRESS_PROOF_NAMES, PREBUILT_DOCUMENT_FORMATS, PREBUILT_FILE_SIZE_MB, true, null,
                    RETENTION_KYC_DAYS, VerificationMethod.MANUAL_REVIEW),
            entry(DocumentTypeConstants.FRANCHISE_BUSINESS_REGISTRATION, CATEGORY_FRANCHISE_ONBOARDING,
                    FLOW_FRANCHISE_ONBOARDING, DocumentTypeConstants.FRANCHISE_BUSINESS_REGISTRATION_NAMES, PREBUILT_DOCUMENT_FORMATS,
                    PREBUILT_LARGE_FILE_SIZE_MB, false, null, RETENTION_KYC_DAYS, VerificationMethod.MANUAL_REVIEW),
            entry(DocumentTypeConstants.FRANCHISE_TAX_CERTIFICATE, CATEGORY_FRANCHISE_ONBOARDING,
                    FLOW_FRANCHISE_ONBOARDING, DocumentTypeConstants.FRANCHISE_TAX_CERTIFICATE_NAMES, PREBUILT_DOCUMENT_FORMATS,
                    PREBUILT_LARGE_FILE_SIZE_MB, false, null, RETENTION_KYC_DAYS, VerificationMethod.MANUAL_REVIEW),
            entry(DocumentTypeConstants.FRANCHISE_BANK_PROOF, CATEGORY_FRANCHISE_ONBOARDING, FLOW_FRANCHISE_ONBOARDING,
                    DocumentTypeConstants.FRANCHISE_BANK_PROOF_NAMES, PREBUILT_DOCUMENT_FORMATS, PREBUILT_FILE_SIZE_MB, true, null,
                    RETENTION_KYC_DAYS, VerificationMethod.BANK_PENNY_DROP),
            entry(DocumentTypeConstants.FRANCHISE_AGREEMENT, CATEGORY_FRANCHISE_ONBOARDING, FLOW_FRANCHISE_ONBOARDING,
                    DocumentTypeConstants.FRANCHISE_AGREEMENT_NAMES, PREBUILT_AGREEMENT_FORMATS, PREBUILT_AGREEMENT_FILE_SIZE_MB, true, null,
                    RETENTION_AGREEMENT_DAYS, VerificationMethod.NONE),
            entry(DocumentTypeConstants.FRANCHISE_PHOTO, CATEGORY_FRANCHISE_ONBOARDING, FLOW_FRANCHISE_ONBOARDING,
                    DocumentTypeConstants.FRANCHISE_PHOTO_NAMES, PREBUILT_FRANCHISE_PHOTO_FORMATS, PREBUILT_FILE_SIZE_MB, false, null, null,
                    VerificationMethod.NONE));

    private PrebuiltDocumentTypes() {
    }

    /**
     * One row of the catalog. {@code verificationRequired} is derived: true exactly when the method is not NONE.
     *
     * @param maxFileSizeMb the catalog's size; the organization's configured maximum caps it on creation
     */
    public record Entry(String code, String category, String flow, Map<String, String> names, Set<DocumentFormat> formats,
                        int maxFileSizeMb, boolean required, Integer expiryYears, Integer retentionDays,
                        VerificationMethod verificationMethod) {

        public boolean verificationRequired() {
            return verificationMethod != VerificationMethod.NONE;
        }
    }

    /** Every prebuilt type, in a fixed order. */
    public static List<Entry> entries() {
        return ENTRIES;
    }

    /**
     * The stored form of {@code entry} for one organization: malware scan required, active, created by the system.
     *
     * @param orgMaxFileSizeMb the organization's configured maximum (the setting the DEFAULT type uses); the type's
     *                         size is the smaller of the two, and never below {@link DocumentTypeConstants#MIN_FILE_SIZE_MB}
     */
    public static DocumentType create(Entry entry, String tenantId, String id, Instant now, int orgMaxFileSizeMb) {
        int size = Math.max(DocumentTypeConstants.MIN_FILE_SIZE_MB, Math.min(entry.maxFileSizeMb(), orgMaxFileSizeMb));
        return new DocumentType(id, tenantId, entry.code(), entry.category(), entry.flow(), entry.names(),
                EnumSet.copyOf(entry.formats()), size, entry.required(), entry.expiryYears(), entry.retentionDays(),
                DocumentTypeConstants.PREBUILT_SCAN_REQUIRED, entry.verificationRequired(), entry.verificationMethod(), true,
                null, now, now, DocumentTypeConstants.SYSTEM_ACTOR, DocumentTypeConstants.SYSTEM_ACTOR, null, List.of(),
                VerificationProviderCatalog.defaultProvider(entry.verificationMethod()));
    }

    /**
     * The stored type widened to the catalog's formats, for a type an older catalog created with fewer formats (the
     * PDF-only franchise agreement rejected the JPEG and PNG scans the onboarding screen offers). Only a type the system
     * created and no admin has changed since is widened, and only by adding formats: an admin's choice is never touched.
     *
     * @return the widened type to save (same id and lock version), or empty when nothing should change
     */
    public static Optional<DocumentType> widenedToCatalog(Entry entry, DocumentType stored, Instant now) {
        boolean untouched = DocumentTypeConstants.SYSTEM_ACTOR.equals(stored.createdBy())
                && DocumentTypeConstants.SYSTEM_ACTOR.equals(stored.updatedBy())
                && stored.lastChangeRequestId() == null;
        Set<DocumentFormat> current = stored.allowedFormats();
        if (!untouched || !entry.code().equals(stored.code()) || current.containsAll(entry.formats())
                || !entry.formats().containsAll(current)) {
            return Optional.empty();
        }
        return Optional.of(new DocumentType(stored.id(), stored.tenantId(), stored.code(), stored.category(), stored.flow(),
                stored.names(), EnumSet.copyOf(entry.formats()), stored.maxFileSizeMb(), stored.required(),
                stored.expiryYears(), stored.retentionDays(), stored.scanRequired(), stored.verificationRequired(),
                stored.verificationMethod(), stored.active(), stored.version(), stored.createdAt(), now,
                stored.createdBy(), DocumentTypeConstants.SYSTEM_ACTOR, null, stored.recentChangeRequestIds(), stored.verificationProvider()));
    }

    /** Add provider assignments and mandatory PAN to untouched system seeds; preserve every admin-configured row. */
    public static Optional<DocumentType> withProviderDefaults(Entry entry, DocumentType stored, Instant now) {
        boolean untouched = DocumentTypeConstants.SYSTEM_ACTOR.equals(stored.createdBy())
                && DocumentTypeConstants.SYSTEM_ACTOR.equals(stored.updatedBy()) && stored.lastChangeRequestId() == null;
        if (!untouched || !entry.code().equals(stored.code()) || stored.verificationMethod() != entry.verificationMethod()) return Optional.empty();
        String defaultProvider = VerificationProviderCatalog.defaultProvider(entry.verificationMethod());
        boolean providersMissing = stored.verificationRequired() && stored.verificationProvider() == null && defaultProvider != null;
        boolean requiredMissing = DocumentTypeConstants.DRIVER_PAN.equals(entry.code()) && !stored.required();
        if (!providersMissing && !requiredMissing) return Optional.empty();
        return Optional.of(new DocumentType(stored.id(), stored.tenantId(), stored.code(), stored.category(), stored.flow(), stored.names(),
                stored.allowedFormats(), stored.maxFileSizeMb(), requiredMissing || stored.required(), stored.expiryYears(), stored.retentionDays(),
                stored.scanRequired(), stored.verificationRequired(), stored.verificationMethod(), stored.active(), stored.version(),
                stored.createdAt(), now, stored.createdBy(), stored.updatedBy(), stored.lastChangeRequestId(), stored.recentChangeRequestIds(),
                providersMissing ? defaultProvider : stored.verificationProvider()));
    }

    private static Entry entry(String code, String category, String flow, Map<String, String> names,
                               Set<DocumentFormat> formats, int maxFileSizeMb, boolean required, Integer expiryYears,
                               Integer retentionDays, VerificationMethod method) {
        return new Entry(code, category, flow, names, formats, maxFileSizeMb, required, expiryYears, retentionDays, method);
    }
}
