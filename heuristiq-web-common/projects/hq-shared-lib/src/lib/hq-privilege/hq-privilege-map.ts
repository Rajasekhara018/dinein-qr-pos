/**
 * Maps a screen permission key (the `permission` on a menu item or route) to the privileges that unlock it.
 * A user may open the screen when they hold ANY of the listed privilege codes (the names in the gateway's
 * privilege.json, as returned by UAM's GET /identity/me/privileges). Broad "*_MANAGE" codes are listed too,
 * because the gateway treats them as covering the whole path family.
 *
 * UI hint only: the gateway checks every API call. Shared by Rider_Web and Rider_AdminWeb through hq-shared-lib.
 */

const TRAIN_SCREEN_PRIVILEGES = {
  view_train_dashboard: ['TRAIN_ADMIN_DASHBOARD_SUMMARY_READ'],
  view_train_bookings: ['TRAIN_ADMIN_BOOKING_LIST_READ'],
  view_train_refunds: ['TRAIN_ADMIN_REFUND_LIST_READ'],
  view_train_stuck_payments: ['TRAIN_ADMIN_REFUND_STUCK_READ'],
  view_train_tdr: ['TRAIN_ADMIN_TDR_READ', 'TRAIN_ADMIN_TDR_FILINGS_READ'],
  view_train_alerts: ['TRAIN_ADMIN_ALERTS_READ'],
  view_train_vikalp: ['TRAIN_ADMIN_VIKALP_READ'],
  view_train_pnr: ['TRAIN_ADMIN_PNR_READ'],
  view_train_reconciliation: ['TRAIN_ADMIN_RECONCILIATION_STATUS_READ'],
  view_train_reports: [
    'TRAIN_ADMIN_REPORT_PERFORMANCE_READ',
    'TRAIN_ADMIN_REPORT_TDR_READ',
    'TRAIN_ADMIN_REPORT_CLASS_DISTRIBUTION_READ',
    'TRAIN_ADMIN_REPORT_JOB_LIST',
  ],
  view_train_travel_users: ['TRAIN_ADMIN_TRAVEL_USER_LIST_READ'],
  view_train_irctc_accounts: ['TRAIN_ADMIN_IRCTC_ACCOUNT_LIST_READ'],
} as const;

/**
 * Matrimony operations portal (Rider_Web remote 'railcab-matrimony'): one key per screen, each unlocked by the
 * privilege of its main read/list call (names from privilege.json, service "matrimony").
 */
const MATRIMONY_SCREEN_PRIVILEGES = {
  view_matrimony_dashboard: ['MATRIMONY_ANALYTICS_SUMMARY_READ'],
  view_matrimony_insights: [
    'MATRIMONY_ANALYTICS_TIMESERIES_READ',
    'MATRIMONY_ANALYTICS_FUNNEL_READ',
    'MATRIMONY_ANALYTICS_OPS_PERFORMANCE_READ',
    'MATRIMONY_ANALYTICS_REVENUE_READ',
    'MATRIMONY_ANALYTICS_DEMOGRAPHICS_READ',
  ],
  view_matrimony_profile_review: ['MATRIMONY_PROFILE_REVIEW_QUEUE_LIST', 'MATRIMONY_PROFILE_SEARCH'],
  view_matrimony_activations: ['MATRIMONY_CUSTOMER_PENDING_ACTIVATION_LIST'],
  view_matrimony_agents: ['MATRIMONY_AGENT_LIST'],
  // The agent's own area: their dashboard, and onboarding a customer on someone's behalf.
  view_matrimony_agent: ['MATRIMONY_AGENT_PROFILE_READ'],
  view_matrimony_agent_add_customer: ['MATRIMONY_AGENT_CUSTOMER_CREATE'],
  // The franchise (broker-led) flow: a customer's biodata and consent, and the proposals between two customers.
  view_matrimony_agent_biodata: ['MATRIMONY_FRANCHISE_BIODATA_READ'],
  view_matrimony_agent_proposals: ['MATRIMONY_FRANCHISE_PROPOSAL_LIST'],
  // Consent of a customer an agent created, and moving customers between agents.
  view_matrimony_agent_consent: ['MATRIMONY_AGENT_CUSTOMER_CONSENT_LIST'],
  // Service quality: complaints about agents, and how customers rate an agent.
  // Photos, horoscope and partner preferences an agent adds for a customer.
  view_matrimony_agent_media: ['MATRIMONY_AGENT_CUSTOMER_PHOTO_CREATE', 'MATRIMONY_AGENT_CUSTOMER_HOROSCOPE_CREATE', 'MATRIMONY_AGENT_CUSTOMER_PREFERENCE_UPDATE'],
  view_matrimony_agent_funnel: ['MATRIMONY_FRANCHISE_FUNNEL_READ'],
  view_matrimony_agent_complaints: ['MATRIMONY_FRANCHISE_COMPLAINT_LIST'],
  view_matrimony_agent_ratings: ['MATRIMONY_FRANCHISE_AGENT_RATING_LIST'],
  view_matrimony_agent_reassign: ['MATRIMONY_FRANCHISE_CUSTOMER_AGENT_UPDATE', 'MATRIMONY_FRANCHISE_AGENT_CUSTOMER_REASSIGN_CREATE'],
  view_matrimony_photo_verification: ['MATRIMONY_PHOTO_VERIFICATION_QUEUE_LIST'],
  view_matrimony_rm_cases: ['MATRIMONY_RM_ASSIGNMENT_MINE_LIST'],
  view_matrimony_match_digest: ['MATRIMONY_MATCH_DIGEST_SEND'],
  view_matrimony_reports: ['MATRIMONY_REPORT_LIST'],
  view_matrimony_blocks: ['MATRIMONY_BLOCK_LIST'],
  view_matrimony_subscription_plans: ['MATRIMONY_SUBSCRIPTION_PLAN_LIST'],
  view_matrimony_credit_packs: ['MATRIMONY_CREDIT_PACK_LIST'],
  view_matrimony_payments: ['MATRIMONY_PAYMENT_LIST'],
  view_matrimony_success_stories: ['MATRIMONY_SUCCESS_STORY_REVIEW_QUEUE_LIST'],
  view_matrimony_platform_config: ['MATRIMONY_CONFIG_OPTION_LIST'],
  view_matrimony_operational_settings: ['MATRIMONY_SETTINGS_READ', 'MATRIMONY_SECURITY_POLICY_READ'],
} as const;

/**
 * Agent wallet screens in Rider_Web (`/agent/wallet/...`, Rider_WalletService, service "rider-wallet"): one key per
 * screen, each unlocked by the privilege of its main read/list call (or, for the load screen, the load itself).
 */
const WALLET_AGENT_SCREEN_PRIVILEGES = {
  view_agent_wallet: ['WALLET_AGENT_SELF_READ'],
  load_agent_wallet: ['WALLET_AGENT_LOAD_CREATE'],
  hand_over_cash: ['WALLET_AGENT_CASH_HANDOVER_LIST'],
  view_agent_customers: ['WALLET_AGENT_CUSTOMER_LIST'],
  view_agent_earnings: ['WALLET_AGENT_EARNINGS_LIST'],
  distribute_team_wallet: ['WALLET_SELF_TEAM_LIST'],
  view_team_movement: ['WALLET_SELF_TEAM_MOVEMENT_READ'],
} as const;

/**
 * Admin wallet screens (Rider_AdminWeb area `wallet`, `/admin/wallet/...`, service "rider-wallet"): one key per
 * screen, each unlocked by the privilege of its main read/list call.
 */
const WALLET_ADMIN_SCREEN_PRIVILEGES = {
  view_wallet_overview: ['WALLET_ADMIN_OVERVIEW_READ'],
  view_wallet_users: ['WALLET_ADMIN_USER_WALLET_READ', 'WALLET_ADMIN_ACCOUNT_LIST'],
  view_wallet_agents: ['WALLET_ADMIN_ACCOUNT_LIST'],
  view_wallet_journals: ['WALLET_ADMIN_JOURNAL_LIST'],
  view_wallet_agent_links: ['WALLET_ADMIN_AGENT_LINK_LIST'],
  view_wallet_apps: ['WALLET_ADMIN_APP_LIST'],
  view_wallet_reports: ['WALLET_ADMIN_REPORT_APP_SUMMARY', 'WALLET_ADMIN_REPORT_SETTLEMENT'],
  view_wallet_reconciliation: ['WALLET_ADMIN_RECONCILIATION_LIST'],
  view_wallet_team_movement: ['WALLET_ADMIN_TEAM_MOVEMENT_READ'],
  view_wallet_department_funding: ['WALLET_ADMIN_DEPARTMENT_HEAD_LIST'],
  view_wallet_alerts: ['WALLET_ADMIN_ALERT_LIST'],
  view_wallet_treasury: ['WALLET_ADMIN_TREASURY_LIST'],
  view_wallet_disputes: ['WALLET_ADMIN_DISPUTE_LIST'],
  view_wallet_period_close: ['WALLET_ADMIN_PERIOD_CLOSE_LIST'],
  // Accounting register (live Wallet journals, docs/ACCOUNTING_REBUILD_PLAN.md): each screen opens with its main read or list call.
  view_wallet_accounting_transactions: ['WALLET_ACCOUNTING_TRANSACTION_LIST'],
  view_wallet_accounting_summary: ['WALLET_ACCOUNTING_SUMMARY_READ'],
  view_wallet_accounting_commissions: ['WALLET_ACCOUNTING_COMMISSION_LIST'],
  view_wallet_accounting_franchise: ['WALLET_ACCOUNTING_FRANCHISE_READ'],
  view_wallet_accounting_party_statement: ['WALLET_ACCOUNTING_PARTY_STATEMENT_READ'],
  // System accounts: the live accounting list, or (until that is granted to a role) the account list filtered to SYSTEM owners.
  view_wallet_system_accounts: ['WALLET_ACCOUNTING_SYSTEM_ACCOUNT_LIST', 'WALLET_ADMIN_ACCOUNT_LIST'],
} as const;

/**
 * Accounting portal (Rider_Web remote 'railcab-accounting', realboom-accounting-web, Rider_AccountingService, service
 * "rider-accounting"): one key per screen, each unlocked by the privilege of its main read or list call. They are not
 * view_wallet_* keys, so they do not open the admin wallet area.
 */
const ACCOUNTING_SCREEN_PRIVILEGES = {
  view_accounting_chart: ['WALLET_ACCOUNTING_CHART_LIST'],
  view_accounting_trial_balance: ['WALLET_ACCOUNTING_TRIAL_BALANCE_READ'],
} as const;

/**
 * Reports portal (Rider_Web remote 'railcab-reports', realboom-reports-web, Rider_ReportService, service "reports"): one
 * key per screen, each unlocked by the privilege of its main query call. The codes are WORKING NAMES: they are not in
 * privilege.json until the catalogue entries proposed in realboom-reports-web/docs/BACKEND_INTEGRATION.md are approved,
 * so until then no role holds them and the screens stay hidden.
 */
const REPORTS_SCREEN_PRIVILEGES = {
  view_reports_dashboard: ['REPORTS_DASHBOARD_QUERY'],
  view_reports_system_config: ['REPORTS_SYSTEM_CONFIG_QUERY'],
  view_reports_custom_fields: ['REPORTS_CUSTOM_FIELD_QUERY'],
  view_reports_templates: ['REPORTS_TEMPLATE_QUERY'],
  view_reports_configs: ['REPORTS_CONFIG_QUERY'],
  view_reports_generated: ['REPORTS_GENERATED_QUERY'],
  view_reports_audit_logs: ['REPORTS_AUDIT_LOG_QUERY'],
} as const;

/**
 * Inventory portal (Rider_Web remote 'railcab-ims', Rider_IMSWeb, Rider_IMS, service "ims"): one key per screen, each unlocked by
 * the privilege of its main read or list call. The catalogue has only the four master-data reads (IMS_001..IMS_004), so the
 * receiving, transfers and ledger screens, whose own calls (/api/ims/v1/receipts, /transfers, /stock) have no catalogue entry
 * yet, open on the master-data read they also need: the items for receiving and the stock ledger, the sites for transfers.
 */
const INVENTORY_SCREEN_PRIVILEGES = {
  view_inventory_dashboard: ['IMS_ITEM_READ'],
  view_inventory_items: ['IMS_ITEM_READ'],
  view_inventory_locations: ['IMS_LOCATION_READ', 'IMS_SITE_READ'],
  view_inventory_receiving: ['IMS_ITEM_READ'],
  view_inventory_transfers: ['IMS_SITE_READ'],
  view_inventory_ledger: ['IMS_ITEM_READ'],
} as const;

/**
 * Food delivery admin (Rider_Web remote 'railcab-food', food-delivery-platform/admin-web, service "food"): one key per screen, each
 * unlocked by the catalogue READ privilege of its main call: a resource's list and its records (/api/v1/food/admin/<resource>/**),
 * the dashboard (/dashboard), analytics (/analytics) and reports (/reports/**). Changes on a screen are gated per action in the remote.
 *
 * The operations screens (live board, refund requests, restaurant approvals, menu reviews, settlements) open with the LIST/READ of
 * their main call. The food backend still accepts only platform operators (UAM's org-wide claim, from ORGANIZATION_LIST), so the
 * insight screens, which before had only that operator key, also open for ORGANIZATION_LIST while roles are granted FOOD_*_READ.
 */
const FOOD_OPERATOR_PRIVILEGE = 'ORGANIZATION_LIST';
const FOOD_SCREEN_PRIVILEGES = {
  view_food_dashboard: ['FOOD_DASHBOARD_READ', FOOD_OPERATOR_PRIVILEGE],
  view_food_reports: ['FOOD_REPORT_READ', FOOD_OPERATOR_PRIVILEGE],
  view_food_analytics: ['FOOD_ANALYTICS_READ', FOOD_OPERATOR_PRIVILEGE],
  view_food_restaurants: ['FOOD_RESTAURANT_READ'],
  view_food_categories: ['FOOD_CATEGORY_READ'],
  view_food_foods: ['FOOD_FOOD_READ'],
  view_food_customers: ['FOOD_CUSTOMER_READ'],
  view_food_admin_users: ['FOOD_ADMIN_USER_READ'],
  view_food_orders: ['FOOD_ORDER_READ'],
  view_food_payments: ['FOOD_PAYMENT_READ'],
  view_food_refunds: ['FOOD_REFUND_READ'],
  view_food_coupons: ['FOOD_COUPON_READ'],
  view_food_offers: ['FOOD_OFFER_READ'],
  view_food_reviews: ['FOOD_REVIEW_READ'],
  view_food_delivery_partners: ['FOOD_DELIVERY_PARTNER_READ'],
  view_food_support_tickets: ['FOOD_SUPPORT_TICKET_READ'],
  view_food_notifications: ['FOOD_NOTIFICATION_READ'],
  view_food_settings: ['FOOD_SETTING_READ'],
  view_food_live_board: ['FOOD_LIVE_BOARD_READ'],
  view_food_refund_requests: ['FOOD_REFUND_REQUEST_LIST'],
  view_food_restaurant_approvals: ['FOOD_RESTAURANT_APPLICATION_LIST'],
  view_food_menu_reviews: ['FOOD_MENU_REVIEW_LIST'],
  view_food_settlements: ['FOOD_SETTLEMENT_LIST'],
} as const;

export const HQ_PERMISSION_PRIVILEGES: Readonly<Record<string, readonly string[]>> = {
  ...TRAIN_SCREEN_PRIVILEGES,
  ...MATRIMONY_SCREEN_PRIVILEGES,
  ...WALLET_AGENT_SCREEN_PRIVILEGES,
  ...WALLET_ADMIN_SCREEN_PRIVILEGES,
  ...ACCOUNTING_SCREEN_PRIVILEGES,
  ...REPORTS_SCREEN_PRIVILEGES,
  ...INVENTORY_SCREEN_PRIVILEGES,
  // The whole inventory portal: any one of its screens opens the mount, each screen then checks its own key.
  view_inventory: [...new Set(Object.values(INVENTORY_SCREEN_PRIVILEGES).flat())],
  // The whole reports portal: any one of its screens opens the area, each screen then checks its own key.
  view_reports: Object.values(REPORTS_SCREEN_PRIVILEGES).flat(),
  ...FOOD_SCREEN_PRIVILEGES,
  // The whole Food area (federated remote 'railcab-food'): any one of its screens opens the mount, each screen then checks its own key.
  view_food: [...new Set(Object.values(FOOD_SCREEN_PRIVILEGES).flat())],
  // The restaurant-partner portal (/food-partner): a partner's own restaurants. Not a view_food_* key: staff privileges never open
  // it, and its join screen (applying to become a partner) is open to any signed-in user.
  view_restaurant_partner: ['FOOD_PARTNER_RESTAURANT_LIST'],
  // The whole admin wallet area: any one admin wallet screen opens the area, each screen then checks its own key.
  // The Wallet home: anyone who may use any part of the wallet (an agent, a head or an administrator). Not a view_wallet_* key,
  // so it never counts as an administrator key.
  wallet_home: ['WALLET_AGENT_SELF_READ', 'WALLET_ADMIN_OVERVIEW_READ', 'WALLET_ADMIN_ACCOUNT_LIST', 'WALLET_ADMIN_USER_WALLET_READ'],
  view_wallet_ops: [...new Set(Object.values(WALLET_ADMIN_SCREEN_PRIVILEGES).flat())],
  // The whole matrimony portal: any one of its screens opens the area, each screen then checks its own key.
  view_matrimony: Object.values(MATRIMONY_SCREEN_PRIVILEGES).flat(),
  // The whole train-operations area: any one train screen opens the area, each screen then checks its own key.
  view_train_ops: Object.values(TRAIN_SCREEN_PRIVILEGES).flat(),
  manage_train_refunds: ['TRAIN_ADMIN_REFUND_CREATE'],
  manage_train_config: ['TRAIN_ADMIN_COMMISSION_LIST_READ', 'TRAIN_ADMIN_COMMISSION_UPDATE'],
  review_changes: ['WORKFLOW_CHANGE_REQUEST_LIST_READ'],
  manage_content: ['CONFIG_BANNER_LIST_READ', 'CONFIG_FAQ_LIST_READ', 'CONFIG_APP_VERSION_LIST_READ'],
  system_config: ['CONFIG_SETTING_LIST_READ', 'ADMIN_SETTINGS_READ', 'ADMIN_SETTINGS_MANAGE'],
  // Serviceability check (Rider_AdminWeb /admin/config-settings/serviceability): which city, area and settings apply at a point.
  view_serviceability: ['CONFIG_SERVICEABILITY_READ'],
  // Vehicle-type catalogue (Rider_AdminWeb /admin/config-settings/vehicle-types): the types riders can book and their passenger capacity.
  view_vehicle_types: ['CONFIG_VEHICLE_TYPE_LIST_READ'],
  view_code_coverage: ['CONFIG_COVERAGE_RUN_LIST', 'CONFIG_COVERAGE_RUN_READ'],
  // Developer tools (Rider_Web): the reference pages (chart gallery, API docs, Java docs) open to developer staff, the same people
  // who may read the coverage runs; the service registry to whoever may call the admin API behind it (/api/v1/admin/registry/**).
  view_developer_docs: ['CONFIG_COVERAGE_RUN_LIST', 'CONFIG_COVERAGE_RUN_READ'],
  view_service_registry: ['ADMIN_READ'],
  // Workflow (Rider_Web /workflow): the task inbox (and starting or viewing an instance) for whoever works tasks; the designer and
  // reports for whoever may read the process definitions.
  view_workflow_tasks: ['WORKFLOW_TASK_LIST'],
  view_workflow_designer: ['WORKFLOW_DEFINITION_LIST'],
  view_qa_runs: ['CONFIG_QA_RUN_LIST'],
  // QA automation dashboards: each screen opens on the privilege of its main read or list call.
  view_qa_overview: ['CONFIG_QA_OVERVIEW_READ'],
  view_qa_suites: ['CONFIG_QA_TEST_CASE_LIST'],
  view_qa_failures: ['CONFIG_QA_FAILURE_READ'],
  view_qa_executions: ['CONFIG_QA_TEST_CASE_LIST'],
  view_qa_test_set_executions: ['CONFIG_QA_EXECUTION_LIST'],
  view_qa_executive_summary: ['CONFIG_QA_EXECUTIVE_SUMMARY_READ'],
  view_qa_test_cases: ['CONFIG_QA_TEST_CASE_LIST'],
  view_qa_test_sets: ['CONFIG_QA_TEST_SET_LIST'],
  view_qa_endpoint_inventory: ['CONFIG_QA_ENDPOINT_LIST'],
  view_qa_test_case_studio: ['CONFIG_QA_STUDIO_CASE_LIST'],
  view_qa_api_documents: ['CONFIG_QA_DOCUMENT_LIST'],
  // Operations workspace (notes, to-dos, team tasks, handover notes): anyone who may list the items.
  view_workspace: ['WORKSPACE_ITEM_LIST'],

  view_dashboard: ['ADMIN_DASHBOARD_SUMMARY_READ'],
  view_rides: ['ADMIN_RIDE_LIST'],
  view_sos: ['RIDE_SOS_SEARCH', 'RIDE_SOS_LIST'],
  // Ops queue of early-drop rides under dispute (Rider_AdminWeb /admin/ride-disputes); resolving needs RIDE_DISPUTE_UPDATE.
  view_ride_disputes: ['RIDE_DISPUTE_LIST'],
  view_fleet: ['ADMIN_VEHICLE_LIST'],
  view_fleet_map: ['LOCATION_DRIVERS_LIVE_READ'],
  view_customers: ['CUSTOMER_ADMIN_LIST'],
  manage_fares: ['ADMIN_FARE_CONFIG_READ', 'ADMIN_FARE_MANAGE'],
  approve_drivers: ['ADMIN_DRIVER_LIST', 'ADMIN_DRIVERS_MANAGE'],
  approve_vendors: ['ADMIN_VENDOR_LIST', 'ADMIN_VENDORS_MANAGE'],
  manage_roles: ['ADMIN_USER_LIST', 'ADMIN_USERS_MANAGE'],
  resolve_disputes: ['ADMIN_SUPPORT_TICKET_LIST', 'ADMIN_SUPPORT_MANAGE'],
  view_financials: ['ADMIN_FINANCE_OVERVIEW_READ', 'ADMIN_FINANCE_ACCESS'],
  export_reports: ['ADMIN_ANALYTICS_SUMMARY_READ'],
  send_notifications: ['ADMIN_NOTIFICATION_LIST', 'ADMIN_NOTIFICATIONS_MANAGE'],
  audit_logs: ['AUDIT_EVENT_LIST', 'AUDIT_LOG_LIST_ADMIN', 'ADMIN_AUDIT_ACCESS'],

  manage_onboarding: ['ONBOARDING_ADMIN_REVIEW_LIST', 'ONBOARDING_ADMIN_SCHEMA_LIST', 'RIDER_ONBOARDING_ADMIN_ACCESS'],
  view_nms_templates: ['NMS_TEMPLATE_LIST', 'NMS_MANAGE'],
  view_nms_events: ['NMS_EVENT_LIST', 'NMS_MANAGE'],
  view_document_types: ['DMS_DOCUMENT_TYPE_LIST', 'DMS_DOCUMENT_TYPE_READ'],
  view_document_lifecycle: ['DMS_DOCUMENT_LIFECYCLE_LIST', 'DMS_DOCUMENT_LIFECYCLE_READ'],

  view_users: ['USER_LIST', 'UAM_USERS_MANAGE'],
  view_roles: ['ROLE_LIST', 'UAM_ROLES_MANAGE'],
  view_organizations: ['ORGANIZATION_LIST'],
  // An organization's legal entity, tax ids and addresses (opened from the organizations list).
  view_organization_legal_profile: ['ORGANIZATION_LEGAL_PROFILE_READ'],
  // Franchises: the administrator's list of every franchise, and the owner's own franchises with their agents.
  view_franchises: ['FRANCHISE_LIST'],
  // The administrator's franchise plans: agent places and fee per application, country, state and city.
  view_franchise_plans: ['FRANCHISE_PLAN_LIST'],
  // The shared places reference (states, districts, cities, towns, ...) that franchise plans are set for.
  // Franchise package templates: starting terms (places, fee, commission, referral) a franchise is opened from.
  view_franchise_package_templates: ['FRANCHISE_PACKAGE_TEMPLATE_LIST'],
  view_franchise_locations: ['LOCATION_LIST'],
  // Where people asked for service no franchise gives yet: votes per place, for planning new franchises.
  view_franchise_demand: ['FRANCHISE_DEMAND_SUMMARY_LIST'],
  view_setup_checklist: ['FRANCHISE_SETUP_CHECKLIST_READ'],
  // Owners only: agents also hold FRANCHISE_MINE_LIST (the payout screen needs it to pick a franchise), so the screen keys on a
  // privilege only a franchise owner carries.
  view_my_franchises: ['FRANCHISE_AGENT_LIST'],
  // A franchise's legal entity, tax ids and addresses: the administrator's view of any franchise, and the owner's of their own.
  view_franchise_legal_profile: ['FRANCHISE_LEGAL_PROFILE_READ'],
  view_my_franchise_legal_profile: ['FRANCHISE_MINE_LEGAL_PROFILE_READ'],
  // A franchise's onboarding (checklist, KYC documents, payout bank account, agreement, activation): any franchise for
  // administrators, their own for owners.
  view_franchise_onboarding: ['FRANCHISE_ONBOARDING_READ'],
  view_my_franchise_onboarding: ['FRANCHISE_MINE_ONBOARDING_READ'],
  // The signed-in person's own commission ledger and statement (shadow mode), and their payout requests.
  view_my_earnings: ['LEDGER_ENTRY_MINE_LIST', 'COMMISSION_STATEMENT_MINE_READ', 'WALLET_FRANCHISE_ACCOUNTING_MINE_READ'],
  view_my_payouts: ['PAYOUT_REQUEST_MINE_LIST'],
  // Wallet statements (Rider_WalletService): the person's own, and an administrator's view of an account or user.
  view_my_statement: ['WALLET_SELF_STATEMENT_READ'],
  // Partner / agent float (Rider_WalletService): the person's own, and the administrator's alerts list and lookup.
  view_my_float: ['WALLET_PARTNER_FLOAT_SELF_READ'],
  view_partner_floats: ['WALLET_ADMIN_PARTNER_FLOAT_ALERT_LIST', 'WALLET_ADMIN_PARTNER_FLOAT_READ'],
  view_account_statement: ['WALLET_ADMIN_STATEMENT_READ', 'WALLET_ADMIN_USER_STATEMENT_READ'],
  // Franchise money rules and ledger (Rider_AdminWeb /admin/franchise-admin/**) and the portal's member list (Rider_Web).
  view_franchise_fee_schedule: ['FRANCHISE_FEE_SCHEDULE_LIST'],
  view_commission_plans: ['COMMISSION_PLAN_LIST', 'COMMISSION_PLAN_READ'],
  view_referral_plans: ['REFERRAL_PLAN_LIST'],
  view_payout_rules: ['PAYOUT_RULE_LIST'],
  view_franchise_rule_resolver: ['FRANCHISE_RULE_RESOLVE_READ'],
  view_payout_approvals: ['PAYOUT_REQUEST_LIST'],
  view_franchise_ledger: ['LEDGER_ENTRY_LIST', 'COMMISSION_STATEMENT_READ'],
  // Driver subscription plans and the drivers' subscriptions (Rider_AdminWeb /admin/franchise-admin/driver-subscriptions).
  view_driver_subscriptions: ['DRIVER_SUBSCRIPTION_PLAN_LIST', 'DRIVER_SUBSCRIPTION_LIST'],
  // Cash operations (Rider_AdminWeb cash-ops): driver payout requests, and offline sales cash handed over by agents.
  view_driver_payouts: ['DRIVER_PAYOUT_LIST'],
  record_offline_handover: ['WALLET_OFFLINE_HANDOVER_CREATE', 'WALLET_OFFLINE_SALES_HELD_LIST'],
  view_ledger_check: ['FRANCHISE_SALE_CHECK_LIST'],
  view_franchise_members: ['FRANCHISE_MEMBER_LIST'],
  // A franchise owner's drivers, and onboarding a driver straight into the owner's own franchise.
  view_franchise_drivers: ['FRANCHISE_DRIVER_LIST'],
  onboard_franchise_drivers: ['FRANCHISE_DRIVER_ONBOARDING_CREATE'],
  view_applications: ['APPLICATION_LIST'],
  view_api_clients: ['CLIENT_LIST'],
  view_security_policies: ['SECURITY_POLICY_LIST'],

  // Organization structure (Rider_UAM /org-structure): the chart opens for anyone who may list units, the builder for
  // anyone who may open a draft.
  view_org_chart: ['ORG_UNIT_LIST'],
  manage_org_structure: ['ORG_DRAFT_OPEN'],
  import_org_employees: ['ORG_IMPORT_LIST'],
  manage_org_logins: ['ORG_LOGIN_CANDIDATE_LIST'],
};
