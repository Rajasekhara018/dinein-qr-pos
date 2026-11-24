package com.heuristiq.uam.enums;

public class UamEnums {

    public enum FileRetentionPolicyEnum {
        RETAIN_SHORT, RETAIN_NORMAL, RETAIN_LONG, RETAIN_EVER, MERCHANT_DOCUMENTS
    }

    public enum UserStatus {
        ACTIVE, INACTIVE, PENDING, LOCKED, SUSPENDED, DISABLED
    }

    public enum AuthModeEnum {
        PASSWORD_PLUS_OTP, PASSWORD_ONLY, PASSWORD_PLUS_TOTP
        // PASSWORDLESS_OTP,
    }

    public enum ThemeEnum {
        LIGHT, DARK, DEFAULT
    }

    public enum LanguageEnum {
        TE, EN, HI, TA, ML, OD, KA, AR,
    }

    public enum Gender {
        MALE, FEMALE
    }

    public enum AuthProvider {
        LOCAL, KEYCLOAK, FIREBASE, GOOGLE, AZURE_AD, LDAP
    }

    public enum ApplicationType {
        WEB, MOBILE, DESKTOP, API, MICROSERVICE, BATCH, INTERNAL, EXTERNAL
    }

    public enum AuthenticationMode {
        LOCAL, LDAP, OAUTH2, OIDC, SAML, KEYCLOAK, FIREBASE, AZURE_AD, CUSTOM
    }

    public enum PasswordChangeReason {
        USER_CHANGED, ADMIN_RESET, PASSWORD_EXPIRED, FIRST_LOGIN, FORGOT_PASSWORD, MIGRATION, SYSTEM_RESET
    }

    public enum LogoutReason {
        USER_LOGOUT, SESSION_EXPIRED, TOKEN_EXPIRED, ADMIN_LOGOUT, PASSWORD_CHANGED, ACCOUNT_LOCKED, ACCOUNT_DISABLED,
        SECURITY_POLICY,
        CONCURRENT_LOGIN, SYSTEM_RESTART
    }

    public enum DeviceType {
        WEB, MOBILE, TABLET, DESKTOP, SMART_TV, UNKNOWN
    }

    public enum Platform {
        ANDROID, IOS, WINDOWS, LINUX, MACOS, WEB
    }

    public enum MfaType {
        TOTP, EMAIL_OTP, SMS_OTP, PUSH_NOTIFICATION, PASSKEY, WEBAUTHN, SECURITY_KEY
    }

    public enum Permission {
        CREATE_DELETE_ROLES, APPROVE_DRIVERS, APPROVE_VENDORS, VIEW_ALL_RIDES, MANAGE_FARES, PROCESS_PAYOUTS,
        VIEW_FINANCIALS, RESOLVE_DISPUTES, SEND_NOTIFICATIONS, EXPORT_REPORTS, SYSTEM_CONFIG
    }

    /** Functional module of a permission ({@code module} in privilege.json). WALLET: Rider_WalletService credits ledger. */
    public enum ModuleCategory {
        ADMINISTRATION, SECURITY, BUSINESS, REPORTING, CONFIGURATION, INTEGRATION, MASTER_DATA, WORKFLOW, DASHBOARD,
        SETTINGS, WALLET
    }

    public enum PermissionAction {
        // ===== Basic CRUD =====
        CREATE,
        READ,
        UPDATE,
        DELETE,
        // ===== View =====
        VIEW,
        LIST,
        SEARCH,
        FILTER,
        // ===== Approval Workflow =====
        APPROVE,
        REJECT,
        VERIFY,
        AUTHORIZE,
        REVIEW,
        // ===== Status =====
        ENABLE,
        DISABLE,
        ACTIVATE,
        DEACTIVATE,
        LOCK,
        UNLOCK,
        // ===== Data Operations =====
        IMPORT,
        EXPORT,
        DOWNLOAD,
        UPLOAD,
        PRINT,
        // ===== Assignment =====
        ASSIGN,
        UNASSIGN,
        MAP,
        UNMAP,
        // ===== Administration =====
        CONFIGURE,
        MANAGE,
        EXECUTE,
        RESET,
        CLONE,
        // ===== Security =====
        LOGIN,
        LOGOUT,
        CHANGE_PASSWORD,
        RESET_PASSWORD,
        // ===== Financial =====
        INITIATE,
        CANCEL,
        REFUND,
        REVERSE,
        SETTLE,
        // ===== Audit =====
        AUDIT,
        AUDIT_VIEW,
        // ===== Custom Business Action =====
        CUSTOM
    }

    public enum UserType {
        SUPER_ADMIN,
        ADMIN,
        CUSTOMER,
        DRIVER,
        VENDOR,
        OPS,
        BUSINESS,
        SECURITY
    }

    public enum AssignmentType {
        DIRECT, GROUP, DEPARTMENT, DESIGNATION, TENANT_DEFAULT, SYSTEM, IMPORTED, API
    }

    public enum ResourceType {
        MASTER, TRANSACTION, CONFIGURATION, REPORT, DASHBOARD, WORKFLOW, API, DOCUMENT, FILE, SERVICE
    }

    public enum TenantType {
        INTERNAL, CUSTOMER, PARTNER, VENDOR, SUBSIDIARY
    }

    public enum TenantStatus {
        ACTIVE, INACTIVE, SUSPENDED, ARCHIVED
    }

    public enum AuditAction {
        CREATE, UPDATE, DELETE, LOGIN, LOGOUT, PASSWORD_CHANGE, PASSWORD_RESET, ENABLE, DISABLE,
        LOCK, UNLOCK, APPROVE, REJECT, ASSIGN_ROLE, REMOVE_ROLE, ASSIGN_GROUP, REMOVE_GROUP,
        ENABLE_MFA, DISABLE_MFA, EXPORT, IMPORT, ACCESS, EXECUTE,
        // Biometric (device-key) sign-in: a device was enrolled, signed in, was refused, or was revoked.
        BIOMETRIC_ENABLED, BIOMETRIC_SIGNIN, BIOMETRIC_SIGNIN_FAILED, BIOMETRIC_REVOKED
    }

    public enum AuditStatus {
        SUCCESS, FAILURE, WARNING
    }

    public enum ApiClientType {
        INTERNAL, EXTERNAL, MOBILE, WEB, MICROSERVICE,
        BATCH, INTEGRATION
    }

    public enum ClientAuthenticationMethod {
        CLIENT_SECRET, PRIVATE_KEY_JWT, MTLS, JWT_ASSERTION
    }

    public enum GrantType {
        CLIENT_CREDENTIALS, AUTHORIZATION_CODE, REFRESH_TOKEN, PASSWORD, DEVICE_CODE
    }

    public enum PrincipalType {
        USER, API_CLIENT
    }

    public enum NotificationChannel {
        EMAIL, SMS, PUSH, WHATSAPP, IN_APP
    }

    public enum TemplateEngine {
        PLAIN_TEXT, HTML, THYMELEAF, FREEMARKER, MUSTACHE
    }

    public enum NotificationCategory {
        SECURITY, SYSTEM, ACCOUNT, BILLING, PAYMENT, MARKETING,
        PROMOTION, REPORT, ALERT, APPROVAL
    }

    public enum ConfigurationScope {
        GLOBAL, TENANT, APPLICATION, MODULE
    }

    public enum ConfigurationCategory {
        SECURITY, AUTHENTICATION, JWT, DATABASE, SMTP, SMS, STORAGE, FILE, CACHE, LOGGING, PAYMENT, NOTIFICATION,
        API, UI
    }

    public enum ConfigurationDataType {
        STRING,INTEGER,LONG,DOUBLE,BOOLEAN,
        JSON,ARRAY
    }

}


// Security
// Password Length
// Password Expiry
// OTP Expiry
// Login Attempts
// JWT Expiry

// SMTP
// SMTP Host
// SMTP Port
// Username
// SSL Enabled

// Payment
// Settlement Time
// Currency
// MDR
// Retry Count
// File Upload
// Max Upload Size
// Allowed Extensions
// Virus Scan Enabled

// Cache
// Redis TTL
// Cache Size
// Refresh Interval

// UI
// Company Logo
// Theme
// Pagination Size
// Default Language