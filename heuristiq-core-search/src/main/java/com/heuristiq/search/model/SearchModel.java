package com.heuristiq.search.model;

/**
 * Every domain entity type this service indexes. Each producer service (BookingsAPI, AdminAPI,
 * UAM) tags its index/delete events with one of these, and {@code SearchDocumentMapper} maps that
 * entity's fields into the generic {@code text1..}/{@code amount1..}/{@code date1..} slots on
 * {@link com.heuristiq.search.document.SearchDocument}.
 */
public enum SearchModel {
    RIDE,
    DRIVER,
    VEHICLE,
    SUPPORT_TICKET,
    REFUND_REQUEST,
    USER,
    ROLE,
    TENANT,
    GROUP,
    APPLICATION,
    API_CLIENT,
    SECURITY_POLICY,
    DOCUMENT,
    NOTIFICATION_TEMPLATE,
    VENDOR,
    /**
     * A matrimony member profile (matrimonial-api). Slots: text1 fullName, text2 gender, text3 reviewStatus, text4 city,
     * text5 state, text6 country, text7 community, text8 religion, text9 education, text10 profession, text11 diet,
     * text12 familyType, text13 maritalStatus, text14 primaryPhotoId; date1 dateOfBirth; number1 heightCm;
     * amount1 annualIncome; ownerKey is the owning user id.
     */
    MATRIMONY_PROFILE,
    /**
     * A generated report (Rider_ReportService, ReportsMetadata). Slots: text1 productName, text2 reportName, text3 fileName,
     * text4 reportFormat, text5 fileStatus, text6 processingStatus, text7 deliveryStatus, text8 reportFrequency, text9 merchantId,
     * text10 triggeredBy, text11 triggeredByUser, text12 filePath, text13 executionType, text14 deliveryChannel, text15 fileStorageChannel;
     * number1 totalRecordsProcessed, number2 fileSizeBytes, number3 totalExecutionDurationMs, number4 attemptCount;
     * datetime1 executionStartTime, datetime2 executionEndTime, datetime3 queuedAt; ownerKey is the organization.
     */
    REPORT,
    /**
     * A product's report configuration (ReportsConfiguration). Slots: text1 productName, text2 createdBy, text3 updatedBy,
     * text4 active, text5 deprecated, text6 deleted, text7 output file names, text8 report formats, text9 frequencies,
     * text10 scheduled times (the last four are comma separated, one value per report specification); number1 version, number2 number of report specifications;
     * datetime1 createdAt, datetime2 updatedAt; ownerKey is the organization.
     */
    REPORT_CONFIGURATION,
    /**
     * A product's system configuration (SystemConfiguration). Slots: text1 productName, text2 databaseProvider, text3 description,
     * text4 active, text5 createdBy, text6 updatedBy, text7 reportScope, text8 storage providers (comma separated); number1 version; datetime1 createdAt, datetime2 updatedAt;
     * ownerKey is the organization. Credentials of the configuration (keys, tokens, connection details) are never indexed.
     */
    REPORT_SYSTEM_CONFIG,
    /**
     * A product's custom-field configuration (CustomFieldConfig). Slots: text1 productName, text2 createdBy, text3 updatedBy,
     * text4 active, text5 deprecated, text6 validated, text7 changeReason, text8 source collections (comma separated); number1 version, number2 number of fields;
     * datetime1 createdAt, datetime2 updatedAt; ownerKey is the organization.
     */
    REPORT_CUSTOM_FIELD,
    /**
     * A report template (TemplateConfig). Slots: text1 templateName, text2 description, text3 reportFormat, text4 productName,
     * text5 paperSize, text6 orientation, text7 fontFamily, text8 active, text9 templateCode; number1 version; ownerKey is the organization.
     */
    REPORT_TEMPLATE,
    /**
     * An entry of the report service's audit log (AuditLogEntry). Slots: text1 userId, text2 action, text3 resource, text4 ip,
     * text5 collectionId, text6 objectClass, text7 success; datetime1 timestamp, datetime2 loggedDateTime; ownerKey is the
     * organization. Request and response bodies are never indexed.
     */
    REPORT_AUDIT_LOG,
    /**
     * A merchant of the report service's merchant directory (MerchantDirectoryEntry). Slots: text1 productName, text2 merchantId,
     * text3 mid, text4 merchantName, text5 subMerchantId, text6 merchantVpa, text7 mccCode, text8 merchantStatus, text9 status,
     * text10 organizationId, text11 active; ownerKey is the organization. Contact details (email, mobile number) are never indexed.
     */
    REPORT_MERCHANT,
    // Source-owned franchise entities, published through the same stream as UAM records.
    FRANCHISE_FRANCHISE,
    FRANCHISE_PLAN,
    FRANCHISE_AGENT,
    FRANCHISE_ADMIN_MEMBER,
    FRANCHISE_MEMBER,
    FRANCHISE_ADMIN_DRIVER,
    FRANCHISE_DRIVER,
    FRANCHISE_FEE_SCHEDULE,
    FRANCHISE_COMMISSION_PLAN,
    FRANCHISE_REFERRAL_PLAN,
    FRANCHISE_PAYOUT_RULE,
    FRANCHISE_EVENT_TYPE,
    FRANCHISE_LOCATION,
    FRANCHISE_LOCATION_CHILD,
    FRANCHISE_PLACE_OPTION,
    FRANCHISE_PLACE_OPTION_CHILD,
    FRANCHISE_PACKAGE_TEMPLATE,
    FRANCHISE_DRIVER_SUBSCRIPTION_PLAN,
    FRANCHISE_DRIVER_SUBSCRIPTION,
    FRANCHISE_DRIVER_SUBSCRIPTION_CHARGE,
    FRANCHISE_LEDGER_ENTRY,
    FRANCHISE_PAYOUT,
    FRANCHISE_SALE_RECONCILIATION,
    FRANCHISE_MINE,
    // web-search:start
    WEB_ADMIN_DRIVER,
    WEB_ADMIN_PENDING_DRIVER,
    WEB_ADMIN_VENDOR,
    WEB_ADMIN_PENDING_VENDOR,
    WEB_ADMIN_VEHICLE,
    WEB_ADMIN_RIDE,
    WEB_ADMIN_TICKET,
    WEB_ADMIN_PAYOUT,
    WEB_ADMIN_INVOICE,
    WEB_ADMIN_DISPUTE,
    WEB_ADMIN_SURGE_HISTORY,
    WEB_WALLET_ACCOUNT,
    WEB_WALLET_ACCOUNT_ENTRY,
    WEB_WALLET_JOURNAL,
    WEB_WALLET_LINK,
    WEB_WALLET_RECONCILIATION,
    WEB_WALLET_DISPUTE,
    WEB_WALLET_PERIOD_CLOSE,
    WEB_WALLET_CASH_HANDOVER,
    WEB_WALLET_PAYOUT,
    WEB_WALLET_SELF_ENTRY,
    WEB_WALLET_AGENT_ENTRY,
    WEB_WALLET_AGENT_EARNING,
    WEB_WALLET_SELF_PAYOUT,
    WEB_IMS_ITEM,
    WEB_IMS_SITE,
    WEB_IMS_LOCATION,
    WEB_IMS_LEGAL_ENTITY,
    WEB_FOOD_RESTAURANT,
    WEB_FOOD_CATEGORY,
    WEB_FOOD_FOOD,
    WEB_FOOD_CUSTOMER,
    WEB_FOOD_ADMIN_USER,
    WEB_FOOD_ORDER,
    WEB_FOOD_PAYMENT,
    WEB_FOOD_COUPON,
    WEB_FOOD_OFFER,
    WEB_FOOD_REFUND,
    WEB_FOOD_REVIEW,
    WEB_FOOD_DELIVERY_PARTNER,
    WEB_FOOD_SUPPORT_TICKET,
    WEB_FOOD_NOTIFICATION,
    WEB_FOOD_SETTING,
    WEB_MATRIMONY_OPS_PLAN,
    WEB_MATRIMONY_ACTIVE_PLAN,
    WEB_MATRIMONY_OPS_OPTION,
    WEB_MATRIMONY_PUBLIC_STORY,
    WEB_MATRIMONY_SELF_STORY,
    WEB_MATRIMONY_SELF_PAYMENT,
    WEB_MATRIMONY_SELF_SUBSCRIPTION,
    WEB_MATRIMONY_SELF_COMPLAINT,
    WEB_CONFIG_SETTING,
    WEB_CONFIG_SETTING_HISTORY,
    WEB_CONFIG_BANNER,
    WEB_CONFIG_FAQ,
    WEB_CONFIG_APP_VERSION,
    WEB_CONFIG_CITY,
    WEB_CONFIG_VEHICLE_TYPE,
    WEB_CONFIG_VEHICLE_CITY,
    WEB_TRAIN_BOOKING,
    WEB_TRAIN_ACCOUNT,
    WEB_TRAIN_TRAVEL_USER,
    WEB_TRAIN_TDR_FILING,
    WEB_TRAIN_VIKALP,
    WEB_TRAIN_COMMISSION,
    WEB_QA_RUN,
    WEB_QA_DOCUMENT,
    WEB_QA_STUDIO_CASE,
    WEB_QA_TEST_SET,
    WEB_NMS_TEMPLATE,
    WEB_NMS_EVENT_TEMPLATE,
    WEB_NMS_EVENT,
    WEB_NMS_BROADCAST,
    WEB_DMS_DOCUMENT_TYPE,
    WEB_DMS_LIFECYCLE_DOCUMENT
    // web-search:end
}
