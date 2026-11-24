package com.railcab.dms.documenttype.domain;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Fixed values of the document-type feature (limits, languages, patterns, change-request wire values). */
public final class DocumentTypeConstants {

    private DocumentTypeConstants() {
    }

    /** {@code subjectType} of the change request Rider_WorkflowService holds for a document type. */
    public static final String SUBJECT_TYPE = "DMS_DOCUMENT_TYPE";
    /** Separator in the change-request subject id: {@code tenantId|code}. */
    public static final String SUBJECT_ID_SEPARATOR = "|";
    /** Prefix of the change-request title shown to the checker. */
    public static final String TITLE_PREFIX = "Document type ";

    /** Path (appended to {@code railcab.dms.workflow.callback-base-url}) Workflow calls after an approval. */
    public static final String INTERNAL_APPLY_PATH = "/api/v1/dms/internal/document-types/apply";

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";

    /** Languages a name may be given in; English is required. */
    public static final List<String> LANGUAGES = List.of("en", "hi", "ta", "te", "kn", "ml", "ar");
    public static final String REQUIRED_LANGUAGE = "en";
    public static final int MAX_NAME_LENGTH = 120;

    public static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{2,59}$");
    public static final Pattern FLOW_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{0,59}$");
    public static final Pattern CATEGORY_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9 _.\\-]{0,59}$");

    public static final int MIN_FILE_SIZE_MB = 1;
    public static final int MAX_FILE_SIZE_MB = 50;
    public static final int MIN_EXPIRY_YEARS = 1;
    public static final int MAX_EXPIRY_YEARS = 50;
    /** Per-type retention in days; empty means the service default {@code railcab.dms.lifecycle.default-retention-days}. */
    public static final int MIN_RETENTION_DAYS = 1;
    public static final int MAX_RETENTION_DAYS = 36500;
    /** A stored type without the field (written before it existed) requires a malware scan. */
    public static final boolean DEFAULT_SCAN_REQUIRED = true;
    /** A stored type without the field (written before it existed) needs no verification (method NONE). */
    public static final boolean DEFAULT_VERIFICATION_REQUIRED = false;
    public static final long BYTES_PER_MB = 1024L * 1024L;

    /** Code of the type every organization gets automatically, accepting any supported format. */
    public static final String DEFAULT_TYPE_CODE = "DEFAULT";
    /** Category of that type; uploads of any category may use it, so it is never checked against the caller's category. */
    public static final String DEFAULT_TYPE_CATEGORY = "general";
    /** Maker and checker recorded on a type DMS created itself (no change request, no approval). */
    public static final String SYSTEM_ACTOR = "system";
    /** Display name of the default type per language (English is required). */
    public static final Map<String, String> DEFAULT_TYPE_NAMES = Map.of(
            "en", "Default document",
            "hi", "डिफ़ॉल्ट दस्तावेज़",
            "ta", "இயல்புநிலை ஆவணம்",
            "te", "డిఫాల్ట్ పత్రం",
            "kn", "ಡೀಫಾಲ್ಟ್ ದಾಖಲೆ",
            "ml", "സ്ഥിരസ്ഥിതി രേഖ",
            "ar", "مستند افتراضي");
    /** How many organizations the default-type seeder remembers as done, so it skips the lookup for them. */
    public static final int SEEDED_TENANTS_REMEMBERED = 10_000;

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    /** How many applied change-request ids a record remembers, so a repeated callback stays idempotent. */
    public static final int MAX_REMEMBERED_CHANGE_REQUESTS = 20;

    /** Change-request status returned to the maker. */
    public static final String STATUS_PENDING = "PENDING";
    public static final String OUTCOME_APPLIED = "APPLIED";

    /** "Check a file" reason code when no file part arrived or it was empty. */
    public static final String REASON_EMPTY_FILE = "FILE_EMPTY";
    /** "Check a file" reason code for a blocked or dangerous file (extension, declared type or detected content). */
    public static final String REASON_BLOCKED = "FILE_BLOCKED";

    // ---- change-request JSON keys (contract with Rider_WorkflowService; the snapshot keys also feed beforeHash)
    public static final String K_SUBJECT_TYPE = "subjectType";
    public static final String K_SUBJECT_ID = "subjectId";
    public static final String K_TITLE = "title";
    public static final String K_SUMMARY = "summary";
    public static final String K_BEFORE = "before";
    public static final String K_AFTER = "after";
    public static final String K_CALLBACK_URL = "callbackUrl";
    public static final String K_COMMENT = "comment";
    /** Envelope field of Workflow's answer, and the request object inside it. */
    public static final String K_OBJECT = "object";
    public static final String K_REQUEST = "request";
    public static final String K_STATUS = "status";
    /** Fields of the apply callback. */
    public static final String K_CHANGE_REQUEST_ID = "changeRequestId";
    public static final String K_BEFORE_HASH = "beforeHash";
    public static final String K_APPROVED_BY = "approvedBy";
    /** Snapshot fields. */
    public static final String K_ID = "id";
    public static final String K_TENANT_ID = "tenantId";
    public static final String K_CODE = "code";
    public static final String K_VERSION = "version";
    public static final String K_MAKER = "maker";
    public static final String K_ACTION = "action";
    public static final String K_EXPECTED_VERSION = "expectedVersion";
    public static final String K_CATEGORY = "category";
    public static final String K_FLOW = "flow";
    public static final String K_NAMES = "names";
    public static final String K_ALLOWED_FORMATS = "allowedFormats";
    public static final String K_MAX_FILE_SIZE_MB = "maxFileSizeMb";
    public static final String K_REQUIRED = "required";
    public static final String K_EXPIRY_YEARS = "expiryYears";
    public static final String K_RETENTION_DAYS = "retentionDays";
    public static final String K_SCAN_REQUIRED = "scanRequired";
    public static final String K_VERIFICATION_REQUIRED = "verificationRequired";
    public static final String K_VERIFICATION_METHOD = "verificationMethod";
    public static final String K_ACTIVE = "active";

    // ---- validation messages of the verification rule
    public static final String MSG_UNKNOWN_VERIFICATION_METHOD = "Unknown verificationMethod '%s'";
    public static final String MSG_VERIFICATION_METHOD_WITHOUT_REQUIRED =
            "verificationMethod must be NONE when verificationRequired is false";
    public static final String MSG_VERIFICATION_METHOD_MISSING =
            "verificationMethod must not be NONE when verificationRequired is true";

    // ---- the prebuilt catalog every organization gets after DEFAULT (see PrebuiltDocumentTypes)
    public static final String CATEGORY_CUSTOMER_DOCUMENTS = "customer-documents";
    public static final String CATEGORY_DRIVER_DOCUMENTS = "driver-documents";
    public static final String CATEGORY_FRANCHISE_ONBOARDING = "franchise-onboarding";
    public static final String FLOW_CUSTOMER_ONBOARDING = "CUSTOMER_ONBOARDING";
    public static final String FLOW_DRIVER_ONBOARDING = "DRIVER_ONBOARDING";
    public static final String FLOW_FRANCHISE_ONBOARDING = "FRANCHISE_ONBOARDING";
    /** Photos. */
    public static final Set<DocumentFormat> PREBUILT_IMAGE_FORMATS =
            Set.of(DocumentFormat.JPG, DocumentFormat.PNG, DocumentFormat.WEBP);
    /** Scanned documents. */
    public static final Set<DocumentFormat> PREBUILT_DOCUMENT_FORMATS =
            Set.of(DocumentFormat.PDF, DocumentFormat.JPG, DocumentFormat.PNG);
    /** Signed agreements: the PDF, or a photo / scan of the signed paper copy. */
    public static final Set<DocumentFormat> PREBUILT_AGREEMENT_FORMATS =
            Set.of(DocumentFormat.PDF, DocumentFormat.JPG, DocumentFormat.PNG);
    /** A franchise's KYC photo: the KYC form takes the same PDF, JPG and PNG files as every other KYC document. */
    public static final Set<DocumentFormat> PREBUILT_FRANCHISE_PHOTO_FORMATS =
            Set.of(DocumentFormat.PDF, DocumentFormat.JPG, DocumentFormat.PNG, DocumentFormat.WEBP);
    /** Catalog sizes in MB, each capped by the organization's configured maximum on creation. */
    public static final int PREBUILT_FILE_SIZE_MB = 5;
    public static final int PREBUILT_LARGE_FILE_SIZE_MB = 10;
    public static final int PREBUILT_AGREEMENT_FILE_SIZE_MB = 20;
    /** KYC, identity and financial documents are kept 8 years. */
    public static final int RETENTION_KYC_DAYS = 2920;
    /** Agreements are kept 10 years. */
    public static final int RETENTION_AGREEMENT_DAYS = 3650;
    public static final boolean PREBUILT_SCAN_REQUIRED = true;
    public static final int DRIVING_LICENCE_EXPIRY_YEARS = 20;
    public static final int VEHICLE_RC_EXPIRY_YEARS = 15;
    public static final int ANNUAL_EXPIRY_YEARS = 1;

    public static final String CUSTOMER_PHOTO = "CUSTOMER_PHOTO";
    public static final String CUSTOMER_ID_PROOF = "CUSTOMER_ID_PROOF";
    public static final String CUSTOMER_ADDRESS_PROOF = "CUSTOMER_ADDRESS_PROOF";
    public static final String DRIVER_AADHAAR = "DRIVER_AADHAAR";
    public static final String DRIVER_PAN = "DRIVER_PAN";
    public static final String DRIVING_LICENCE = "DRIVING_LICENCE";
    public static final String VEHICLE_RC = "VEHICLE_RC";
    public static final String VEHICLE_INSURANCE = "VEHICLE_INSURANCE";
    public static final String VEHICLE_PUC = "VEHICLE_PUC";
    /** Vehicle permit (contract carriage / transport permit); optional by default because not every vehicle class needs one. */
    public static final String VEHICLE_PERMIT = "VEHICLE_PERMIT";
    public static final String DRIVER_PHOTO = "DRIVER_PHOTO";
    public static final String DRIVER_BANK_PROOF = "DRIVER_BANK_PROOF";
    public static final String DRIVER_POLICE_VERIFICATION = "DRIVER_POLICE_VERIFICATION";
    public static final String FRANCHISE_ID_PROOF = "FRANCHISE_ID_PROOF";
    public static final String FRANCHISE_ADDRESS_PROOF = "FRANCHISE_ADDRESS_PROOF";
    public static final String FRANCHISE_BUSINESS_REGISTRATION = "FRANCHISE_BUSINESS_REGISTRATION";
    public static final String FRANCHISE_TAX_CERTIFICATE = "FRANCHISE_TAX_CERTIFICATE";
    public static final String FRANCHISE_BANK_PROOF = "FRANCHISE_BANK_PROOF";
    public static final String FRANCHISE_AGREEMENT = "FRANCHISE_AGREEMENT";
    public static final String FRANCHISE_PHOTO = "FRANCHISE_PHOTO";

    // Display names per language (AI-written translations: need a native-speaker review before production).
    public static final Map<String, String> CUSTOMER_PHOTO_NAMES = Map.of(
            "en", "Customer photo",
            "hi", "ग्राहक की फ़ोटो",
            "ta", "வாடிக்கையாளர் புகைப்படம்",
            "te", "కస్టమర్ ఫోటో",
            "kn", "ಗ್ರಾಹಕರ ಫೋಟೋ",
            "ml", "ഉപഭോക്താവിന്റെ ഫോട്ടോ",
            "ar", "صورة العميل");
    public static final Map<String, String> CUSTOMER_ID_PROOF_NAMES = Map.of(
            "en", "Customer identity proof",
            "hi", "ग्राहक का पहचान प्रमाण",
            "ta", "வாடிக்கையாளர் அடையாளச் சான்று",
            "te", "కస్టమర్ గుర్తింపు రుజువు",
            "kn", "ಗ್ರಾಹಕರ ಗುರುತಿನ ಪುರಾವೆ",
            "ml", "ഉപഭോക്താവിന്റെ തിരിച്ചറിയൽ രേഖ",
            "ar", "إثبات هوية العميل");
    public static final Map<String, String> CUSTOMER_ADDRESS_PROOF_NAMES = Map.of(
            "en", "Customer address proof",
            "hi", "ग्राहक के पते का प्रमाण",
            "ta", "வாடிக்கையாளர் முகவரிச் சான்று",
            "te", "కస్టమర్ చిరునామా రుజువు",
            "kn", "ಗ್ರಾಹಕರ ವಿಳಾಸದ ಪುರಾವೆ",
            "ml", "ഉപഭോക്താവിന്റെ വിലാസ രേഖ",
            "ar", "إثبات عنوان العميل");
    public static final Map<String, String> DRIVER_AADHAAR_NAMES = Map.of(
            "en", "Driver Aadhaar card",
            "hi", "ड्राइवर का आधार कार्ड",
            "ta", "ஓட்டுநர் ஆதார் அட்டை",
            "te", "డ్రైవర్ ఆధార్ కార్డు",
            "kn", "ಚಾಲಕರ ಆಧಾರ್ ಕಾರ್ಡ್",
            "ml", "ഡ്രൈവറുടെ ആധാർ കാർഡ്",
            "ar", "بطاقة آدهار الخاصة بالسائق");
    public static final Map<String, String> DRIVER_PAN_NAMES = Map.of(
            "en", "Driver PAN card",
            "hi", "ड्राइवर का पैन कार्ड",
            "ta", "ஓட்டுநர் பான் அட்டை",
            "te", "డ్రైవర్ పాన్ కార్డు",
            "kn", "ಚಾಲಕರ ಪ್ಯಾನ್ ಕಾರ್ಡ್",
            "ml", "ഡ്രൈവറുടെ പാൻ കാർഡ്",
            "ar", "بطاقة PAN الخاصة بالسائق");
    public static final Map<String, String> DRIVING_LICENCE_NAMES = Map.of(
            "en", "Driving licence",
            "hi", "ड्राइविंग लाइसेंस",
            "ta", "ஓட்டுநர் உரிமம்",
            "te", "డ్రైవింగ్ లైసెన్స్",
            "kn", "ಚಾಲನಾ ಪರವಾನಗಿ",
            "ml", "ഡ്രൈവിംഗ് ലൈസൻസ്",
            "ar", "رخصة القيادة");
    public static final Map<String, String> VEHICLE_RC_NAMES = Map.of(
            "en", "Vehicle registration certificate (RC)",
            "hi", "वाहन पंजीकरण प्रमाणपत्र (RC)",
            "ta", "வாகனப் பதிவுச் சான்றிதழ் (RC)",
            "te", "వాహన రిజిస్ట్రేషన్ సర్టిఫికేట్ (RC)",
            "kn", "ವಾಹನ ನೋಂದಣಿ ಪ್ರಮಾಣಪತ್ರ (RC)",
            "ml", "വാഹന രജിസ്ട്രേഷൻ സർട്ടിഫിക്കറ്റ് (RC)",
            "ar", "شهادة تسجيل المركبة (RC)");
    public static final Map<String, String> VEHICLE_INSURANCE_NAMES = Map.of(
            "en", "Vehicle insurance",
            "hi", "वाहन बीमा",
            "ta", "வாகனக் காப்பீடு",
            "te", "వాహన బీమా",
            "kn", "ವಾಹನ ವಿಮೆ",
            "ml", "വാഹന ഇൻഷുറൻസ്",
            "ar", "تأمين المركبة");
    public static final Map<String, String> VEHICLE_PUC_NAMES = Map.of(
            "en", "Pollution certificate (PUC)",
            "hi", "प्रदूषण प्रमाणपत्र (पीयूसी)",
            "ta", "மாசுக் கட்டுப்பாட்டுச் சான்றிதழ்",
            "te", "కాలుష్య నియంత్రణ ధృవపత్రం",
            "kn", "ಮಾಲಿನ್ಯ ನಿಯಂತ್ರಣ ಪ್ರಮಾಣಪತ್ರ",
            "ml", "മലിനീകരണ നിയന്ത്രണ സർട്ടിഫിക്കറ്റ്",
            "ar", "شهادة فحص الانبعاثات");
    public static final Map<String, String> VEHICLE_PERMIT_NAMES = Map.of(
            "en", "Vehicle permit",
            "hi", "वाहन परमिट",
            "ta", "வாகன அனுமதிச் சீட்டு",
            "te", "వాహన పర్మిట్",
            "kn", "ವಾಹನ ಪರ್ಮಿಟ್",
            "ml", "വാഹന പെർമിറ്റ്",
            "ar", "تصريح المركبة");
    public static final Map<String, String> DRIVER_PHOTO_NAMES = Map.of(
            "en", "Driver photo",
            "hi", "ड्राइवर की फ़ोटो",
            "ta", "ஓட்டுநர் புகைப்படம்",
            "te", "డ్రైవర్ ఫోటో",
            "kn", "ಚಾಲಕರ ಫೋಟೋ",
            "ml", "ഡ്രൈവറുടെ ഫോട്ടോ",
            "ar", "صورة السائق");
    public static final Map<String, String> DRIVER_BANK_PROOF_NAMES = Map.of(
            "en", "Driver bank account proof",
            "hi", "ड्राइवर के बैंक खाते का प्रमाण",
            "ta", "ஓட்டுநர் வங்கிக் கணக்குச் சான்று",
            "te", "డ్రైవర్ బ్యాంక్ ఖాతా రుజువు",
            "kn", "ಚಾಲಕರ ಬ್ಯಾಂಕ್ ಖಾತೆಯ ಪುರಾವೆ",
            "ml", "ഡ്രൈവറുടെ ബാങ്ക് അക്കൗണ്ട് രേഖ",
            "ar", "إثبات الحساب البنكي للسائق");
    public static final Map<String, String> DRIVER_POLICE_VERIFICATION_NAMES = Map.of(
            "en", "Driver police verification certificate",
            "hi", "ड्राइवर का पुलिस सत्यापन प्रमाणपत्र",
            "ta", "ஓட்டுநர் காவல்துறை சரிபார்ப்புச் சான்றிதழ்",
            "te", "డ్రైవర్ పోలీసు ధృవీకరణ పత్రం",
            "kn", "ಚಾಲಕರ ಪೊಲೀಸ್ ಪರಿಶೀಲನಾ ಪ್ರಮಾಣಪತ್ರ",
            "ml", "ഡ്രൈവറുടെ പോലീസ് വെരിഫിക്കേഷൻ സർട്ടിഫിക്കറ്റ്",
            "ar", "شهادة تحقق الشرطة الخاصة بالسائق");
    public static final Map<String, String> FRANCHISE_ID_PROOF_NAMES = Map.of(
            "en", "Franchise identity proof",
            "hi", "फ्रैंचाइज़ी का पहचान प्रमाण",
            "ta", "ஃபிரான்சைஸ் அடையாளச் சான்று",
            "te", "ఫ్రాంచైజీ గుర్తింపు రుజువు",
            "kn", "ಫ್ರಾಂಚೈಸಿ ಗುರುತಿನ ಪುರಾವೆ",
            "ml", "ഫ്രാഞ്ചൈസി തിരിച്ചറിയൽ രേഖ",
            "ar", "إثبات هوية صاحب الامتياز");
    public static final Map<String, String> FRANCHISE_ADDRESS_PROOF_NAMES = Map.of(
            "en", "Franchise address proof",
            "hi", "फ्रैंचाइज़ी के पते का प्रमाण",
            "ta", "ஃபிரான்சைஸ் முகவரிச் சான்று",
            "te", "ఫ్రాంచైజీ చిరునామా రుజువు",
            "kn", "ಫ್ರಾಂಚೈಸಿ ವಿಳಾಸದ ಪುರಾವೆ",
            "ml", "ഫ്രാഞ്ചൈസി വിലാസ രേഖ",
            "ar", "إثبات عنوان صاحب الامتياز");
    public static final Map<String, String> FRANCHISE_BUSINESS_REGISTRATION_NAMES = Map.of(
            "en", "Business registration certificate",
            "hi", "व्यवसाय पंजीकरण प्रमाणपत्र",
            "ta", "வணிகப் பதிவுச் சான்றிதழ்",
            "te", "వ్యాపార నమోదు ధృవపత్రం",
            "kn", "ವ್ಯವಹಾರ ನೋಂದಣಿ ಪ್ರಮಾಣಪತ್ರ",
            "ml", "ബിസിനസ് രജിസ്ട്രേഷൻ സർട്ടിഫിക്കറ്റ്",
            "ar", "شهادة تسجيل النشاط التجاري");
    public static final Map<String, String> FRANCHISE_TAX_CERTIFICATE_NAMES = Map.of(
            "en", "Tax registration certificate",
            "hi", "कर पंजीकरण प्रमाणपत्र",
            "ta", "வரிப் பதிவுச் சான்றிதழ்",
            "te", "పన్ను నమోదు ధృవపత్రం",
            "kn", "ತೆರಿಗೆ ನೋಂದಣಿ ಪ್ರಮಾಣಪತ್ರ",
            "ml", "നികുതി രജിസ്ട്രേഷൻ സർട്ടിഫിക്കറ്റ്",
            "ar", "شهادة التسجيل الضريبي");
    public static final Map<String, String> FRANCHISE_BANK_PROOF_NAMES = Map.of(
            "en", "Franchise bank account proof",
            "hi", "फ्रैंचाइज़ी के बैंक खाते का प्रमाण",
            "ta", "ஃபிரான்சைஸ் வங்கிக் கணக்குச் சான்று",
            "te", "ఫ్రాంచైజీ బ్యాంక్ ఖాతా రుజువు",
            "kn", "ಫ್ರಾಂಚೈಸಿ ಬ್ಯಾಂಕ್ ಖಾತೆಯ ಪುರಾವೆ",
            "ml", "ഫ്രാഞ്ചൈസി ബാങ്ക് അക്കൗണ്ട് രേഖ",
            "ar", "إثبات الحساب البنكي لصاحب الامتياز");
    public static final Map<String, String> FRANCHISE_AGREEMENT_NAMES = Map.of(
            "en", "Franchise agreement",
            "hi", "फ्रैंचाइज़ी अनुबंध",
            "ta", "ஃபிரான்சைஸ் ஒப்பந்தம்",
            "te", "ఫ్రాంచైజీ ఒప్పందం",
            "kn", "ಫ್ರಾಂಚೈಸಿ ಒಪ್ಪಂದ",
            "ml", "ഫ്രാഞ്ചൈസി കരാർ",
            "ar", "اتفاقية الامتياز");
    public static final Map<String, String> FRANCHISE_PHOTO_NAMES = Map.of(
            "en", "Franchise photo",
            "hi", "फ्रैंचाइज़ी की फ़ोटो",
            "ta", "ஃபிரான்சைஸ் புகைப்படம்",
            "te", "ఫ్రాంచైజీ ఫోటో",
            "kn", "ಫ್ರಾಂಚೈಸಿ ಫೋಟೋ",
            "ml", "ഫ്രാഞ്ചൈസി ഫോട്ടോ",
            "ar", "صورة صاحب الامتياز");
}
