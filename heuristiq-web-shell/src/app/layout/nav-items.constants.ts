/**
 * The signed-in navigation menu: the single source of truth for the sidebar, the header's quick-navigate
 * search and the page title/breadcrumb, so a screen added here shows up in all three.
 * `label` and `section` are i18n keys (nav.items.* / nav.sections.*), resolved with the translate pipe.
 */

import { PENDING_REVIEWS_BADGE } from '../shared/services/pending-reviews.constants';
import type { OperatingModel } from '../access-management/organization-operating-model/organization-operating-model.types';
import { FRANCHISE_OPERATING_MODELS } from './nav-operating-model.constants';
import { DEVELOPER_DOCS_PERMISSION, SERVICE_REGISTRY_PERMISSION } from '../shared/developer-tools.constants';
import { WORKFLOW_TASKS_PERMISSION } from '../workflow/workflow.constants';

/** i18n keys for the sidebar section headings (also used by the header's quick-navigate). */
export type NavSection =
  | 'nav.sections.main'
  | 'nav.sections.account'
  | 'nav.sections.wallet'
  | 'nav.sections.agentWallet'
  | 'nav.sections.walletSetup'
  | 'nav.sections.walletOperations'
  | 'nav.sections.reports'
  | 'nav.sections.reportsSetup'
  | 'nav.sections.reportsRun'
  | 'nav.sections.agentMatrimony'
  | 'nav.sections.ownerMatrimony'
  | 'nav.sections.ownerBestRide'
  | 'nav.sections.matrimonySetup'
  | 'nav.sections.matrimonyOperations'
  | 'nav.sections.matrimonyInsights'
  | 'nav.sections.adminSetup'
  | 'nav.sections.adminStep2VehiclesFares'
  | 'nav.sections.adminStep3AppContent'
  | 'nav.sections.adminOperations'
  | 'nav.sections.adminPeopleFleet'
  | 'nav.sections.adminFinance'
  | 'nav.sections.trainSetup'
  | 'nav.sections.trainOperations'
  | 'nav.sections.trainReconciliation'
  | 'nav.sections.qaSetup'
  | 'nav.sections.qaOperations'
  | 'nav.sections.qaInsights'
  | 'nav.sections.accessStep1Organization'
  | 'nav.sections.accessStep2Organization'
  | 'nav.sections.accessStep2System'
  | 'nav.sections.accessStep3People'
  | 'nav.sections.franchiseSetup'
  | 'nav.sections.franchiseMoney'
  | 'nav.sections.ownerFranchise'
  | 'nav.sections.developerTools'
  | 'nav.sections.accessOperations'
  | 'nav.sections.franchiseOperations'
  | 'nav.sections.accountingSetup'
  | 'nav.sections.accountingOperations'
  | 'nav.sections.accountingInsights'
  | 'nav.sections.foodSetup'
  | 'nav.sections.foodOperations'
  | 'nav.sections.foodInsights'
  | 'nav.sections.imsSetup'
  | 'nav.sections.imsOperations';

export interface NavItem {
  path: string;
  icon: string;
  /** i18n key (nav.items.*), rendered via the translate pipe. */
  label: string;
  section: NavSection;
  permission: string | null;
  /** Stay highlighted on sub-routes too (e.g. /api-docs/java-doc/uam), not only on the exact path. */
  matchPrefix?: boolean;
  /** Live count shown next to the label; only 'pendingReviews' (maker-checker requests waiting for this user) exists. */
  badge?: typeof PENDING_REVIEWS_BADGE;
  /** Kept for routing, page titles and highlighting, but not listed in the sidebar or the quick-navigate search. */
  hidden?: boolean;
  /**
   * Shown only when the session organization runs one of these operating models (absent: every model). A hint only; the
   * screen's privilege still decides access.
   */
  operatingModels?: ReadonlyArray<OperatingModel>;
}

export const NAV_ITEMS: NavItem[] = [
  // Daily summary of the applications the user works in; the landing page after sign-in.
  { path: '/today', icon: 'bi-speedometer2', label: 'nav.items.today', section: 'nav.sections.main', permission: null },

  // The Applications hub: every application of the suite and what the user may open in each.
  { path: '/home', icon: 'bi-grid-1x2', label: 'nav.items.home', section: 'nav.sections.main', permission: null },

  // Hidden from the menu, still routable.
  { path: '/payments/make-payment', icon: 'bi-credit-card', label: 'nav.items.makePayment', section: 'nav.sections.main', permission: null, hidden: true },

  // Notes, to-dos, team tasks and handover notes.
  { path: '/workspace', icon: 'bi-journal-check', label: 'nav.items.workspace', section: 'nav.sections.main', permission: 'view_workspace' },

  // Daily work: what is waiting for the signed-in user.
  { path: '/admin/change-requests', icon: 'bi-patch-check', label: 'nav.items.changeRequests', section: 'nav.sections.main', permission: 'review_changes', badge: PENDING_REVIEWS_BADGE },

  // Wallet portal (federation remote 'railcab-wallet', realboom-wallet-web) under /wallet/**. Each screen has its own permission key (agent or admin wallet).
  { path: '/wallet/home', icon: 'bi-house-heart', label: 'nav.items.walletHome', section: 'nav.sections.wallet', permission: 'wallet_home' },
  // The signed-in user's own wallet statement (Rider_WalletService; routed under /access-management).
  { path: '/access-management/my-statement', icon: 'bi-receipt', label: 'nav.items.myWalletStatement', section: 'nav.sections.wallet', permission: 'view_my_statement' },
  { path: '/wallet/admin/system-accounts', icon: 'bi-bank', label: 'nav.items.walletSystemAccounts', section: 'nav.sections.walletSetup', permission: 'view_wallet_system_accounts' },
  { path: '/wallet/admin', icon: 'bi-speedometer2', label: 'nav.items.walletOverview', section: 'nav.sections.walletOperations', permission: 'view_wallet_overview' },
  { path: '/wallet/admin/journals', icon: 'bi-journal-text', label: 'nav.items.walletJournals', section: 'nav.sections.walletOperations', permission: 'view_wallet_journals' },
  { path: '/wallet/admin/reconciliation', icon: 'bi-arrow-left-right', label: 'nav.items.walletReconciliation', section: 'nav.sections.walletOperations', permission: 'view_wallet_reconciliation' },
  { path: '/wallet/admin/alerts', icon: 'bi-bell', label: 'nav.items.walletAlerts', section: 'nav.sections.walletOperations', permission: 'view_wallet_alerts' },
  { path: '/wallet/admin/disputes', icon: 'bi-flag', label: 'nav.items.walletDisputes', section: 'nav.sections.walletOperations', permission: 'view_wallet_disputes' },
  { path: '/wallet/admin/reports', icon: 'bi-file-earmark-bar-graph', label: 'nav.items.walletReports', section: 'nav.sections.walletOperations', permission: 'view_wallet_reports' },
  { path: '/wallet/admin/period-close', icon: 'bi-lock', label: 'nav.items.walletPeriodClose', section: 'nav.sections.walletOperations', permission: 'view_wallet_period_close' },
  // Partner float alerts and account statements (Rider_WalletService; routed under /access-management).
  { path: '/access-management/partner-floats', icon: 'bi-wallet2', label: 'nav.items.partnerFloats', section: 'nav.sections.walletOperations', permission: 'view_partner_floats' },
  { path: '/access-management/account-statement', icon: 'bi-journal-text', label: 'nav.items.accountStatement', section: 'nav.sections.walletOperations', permission: 'view_account_statement' },
  // Accounting: its own application. The accounting portal (federation remote 'railcab-accounting', realboom-accounting-web,
  // Rider_AccountingService) under /accounting/** and the wallet accounting views (live journals, docs/ACCOUNTING_REBUILD_PLAN.md)
  // under /wallet/admin/accounting/**. Setup (chart of accounts), operations (the register and party statements), then insights.
  { path: '/accounting/admin/chart', icon: 'bi-journal-bookmark', label: 'nav.items.accountingChart', section: 'nav.sections.accountingSetup', permission: 'view_accounting_chart' },
  { path: '/wallet/admin/accounting/transactions', icon: 'bi-list-columns-reverse', label: 'nav.items.walletAccountingTransactions', section: 'nav.sections.accountingOperations', permission: 'view_wallet_accounting_transactions' },
  { path: '/wallet/admin/accounting/statements', icon: 'bi-file-earmark-text', label: 'nav.items.walletAccountingPartyStatement', section: 'nav.sections.accountingOperations', permission: 'view_wallet_accounting_party_statement' },
  { path: '/wallet/admin/accounting/summary', icon: 'bi-grid-3x3-gap', label: 'nav.items.walletAccountingSummary', section: 'nav.sections.accountingInsights', permission: 'view_wallet_accounting_summary' },
  { path: '/wallet/admin/accounting/commissions', icon: 'bi-diagram-3', label: 'nav.items.walletAccountingCommissions', section: 'nav.sections.accountingInsights', permission: 'view_wallet_accounting_commissions' },
  { path: '/wallet/admin/accounting/franchises', icon: 'bi-shop', label: 'nav.items.walletAccountingFranchise', section: 'nav.sections.accountingInsights', permission: 'view_wallet_accounting_franchise' },
  { path: '/accounting/admin/trial-balance', icon: 'bi-calculator', label: 'nav.items.accountingTrialBalance', section: 'nav.sections.accountingInsights', permission: 'view_accounting_trial_balance' },
  // Wallet · Money movement (own section): share with my team, team money movement, fund department heads, treasury,
  // admin team movement. Hidden for now - uncomment these lines, and add 'nav.sections.walletMoneyMovement' to the section
  // type above and to the i18n files, to show the section. The routes themselves still work.
  // { path: '/wallet/agent/team', icon: 'bi-diagram-3', label: 'nav.items.walletTeam', section: 'nav.sections.walletMoneyMovement', permission: 'distribute_team_wallet' },
  // { path: '/wallet/agent/team/movement', icon: 'bi-bar-chart-steps', label: 'nav.items.walletTeamMovement', section: 'nav.sections.walletMoneyMovement', permission: 'view_team_movement' },
  // { path: '/wallet/admin/department-funding', icon: 'bi-cash-coin', label: 'nav.items.walletDepartmentFunding', section: 'nav.sections.walletMoneyMovement', permission: 'view_wallet_department_funding' },
  // { path: '/wallet/admin/treasury', icon: 'bi-safe', label: 'nav.items.walletTreasury', section: 'nav.sections.walletMoneyMovement', permission: 'view_wallet_treasury' },
  // { path: '/wallet/admin/team-movement', icon: 'bi-bar-chart-steps', label: 'nav.items.walletAdminTeamMovement', section: 'nav.sections.walletMoneyMovement', permission: 'view_wallet_team_movement' },

  // Reports portal (federation remote 'railcab-reports', realboom-reports-web) under /reports/**. One permission key per screen.
  { path: '/reports/home', icon: 'bi-bar-chart-line', label: 'nav.items.reportsDashboard', section: 'nav.sections.reports', permission: 'view_reports_dashboard' },
  { path: '/reports/csearch/SYSTEM_CONFIGURATION', icon: 'bi-gear', label: 'nav.items.reportsSystemConfig', section: 'nav.sections.reportsSetup', permission: 'view_reports_system_config' },
  { path: '/reports/csearch/REPORTS_CUSTOM_FIELDS', icon: 'bi-input-cursor-text', label: 'nav.items.reportsCustomFields', section: 'nav.sections.reportsSetup', permission: 'view_reports_custom_fields' },
  { path: '/reports/csearch/REPORTS_TEMPLATE', icon: 'bi-layout-text-window', label: 'nav.items.reportsTemplates', section: 'nav.sections.reportsSetup', permission: 'view_reports_templates' },
  { path: '/reports/csearch/REPORTS_CONFIGURATION', icon: 'bi-sliders', label: 'nav.items.reportsConfig', section: 'nav.sections.reportsSetup', permission: 'view_reports_configs' },
  { path: '/reports/csearch/REPORTS', icon: 'bi-file-earmark-bar-graph', label: 'nav.items.reportsGenerated', section: 'nav.sections.reportsRun', permission: 'view_reports_generated' },
  { path: '/reports/csearch/AUDITLOG', icon: 'bi-shield-exclamation', label: 'nav.items.reportsAuditLogs', section: 'nav.sections.reportsRun', permission: 'view_reports_audit_logs' },
  // Create / edit screens of the portal: routable, resolved to their list's title, not listed in the menu.
  { path: '/reports/reports-config', icon: 'bi-sliders', label: 'nav.items.reportsConfig', section: 'nav.sections.reportsSetup', permission: 'view_reports_configs', hidden: true },
  { path: '/reports/report-template', icon: 'bi-layout-text-window', label: 'nav.items.reportsTemplates', section: 'nav.sections.reportsSetup', permission: 'view_reports_templates', hidden: true },
  { path: '/reports/reports-custom-fields', icon: 'bi-input-cursor-text', label: 'nav.items.reportsCustomFields', section: 'nav.sections.reportsSetup', permission: 'view_reports_custom_fields', hidden: true },
  { path: '/reports/system-config', icon: 'bi-gear', label: 'nav.items.reportsSystemConfig', section: 'nav.sections.reportsSetup', permission: 'view_reports_system_config', hidden: true },

  // Pasupu Kumkuma Matrimony: setup, operations (the daily queues), then insights. The agent's desk and the owner's pages are in the workspaces.
  { path: '/matrimony/platform-config', icon: 'bi-sliders', label: 'nav.items.matrimonyPlatformConfig', section: 'nav.sections.matrimonySetup', permission: 'view_matrimony_platform_config' },
  { path: '/matrimony/subscription-plans', icon: 'bi-award', label: 'nav.items.matrimonySubscriptionPlans', section: 'nav.sections.matrimonySetup', permission: 'view_matrimony_subscription_plans' },
  { path: '/matrimony/operational-settings', icon: 'bi-gear-wide-connected', label: 'nav.items.matrimonyOperationalSettings', section: 'nav.sections.matrimonySetup', permission: 'view_matrimony_operational_settings' },
  { path: '/matrimony/dashboard', icon: 'bi-speedometer2', label: 'nav.items.matrimonyDashboard', section: 'nav.sections.matrimonyOperations', permission: 'view_matrimony_dashboard' },
  { path: '/matrimony/profile-review', icon: 'bi-person-check', label: 'nav.items.matrimonyProfileReview', section: 'nav.sections.matrimonyOperations', permission: 'view_matrimony_profile_review' },
  { path: '/matrimony/photo-verification', icon: 'bi-images', label: 'nav.items.matrimonyPhotoVerification', section: 'nav.sections.matrimonyOperations', permission: 'view_matrimony_photo_verification' },
  { path: '/matrimony/activations', icon: 'bi-cash-coin', label: 'nav.items.matrimonyActivations', section: 'nav.sections.matrimonyOperations', permission: 'view_matrimony_activations' },
  { path: '/matrimony/rm-cases', icon: 'bi-person-lines-fill', label: 'nav.items.matrimonyRmCases', section: 'nav.sections.matrimonyOperations', permission: 'view_matrimony_rm_cases' },
  { path: '/matrimony/blocks', icon: 'bi-slash-circle', label: 'nav.items.matrimonyBlocks', section: 'nav.sections.matrimonyOperations', permission: 'view_matrimony_blocks' },
  { path: '/matrimony/payments', icon: 'bi-receipt', label: 'nav.items.matrimonyPayments', section: 'nav.sections.matrimonyOperations', permission: 'view_matrimony_payments' },
  { path: '/matrimony/reports', icon: 'bi-shield-exclamation', label: 'nav.items.matrimonyReports', section: 'nav.sections.matrimonyInsights', permission: 'view_matrimony_reports' },
  { path: '/matrimony/insights', icon: 'bi-graph-up-arrow', label: 'nav.items.matrimonyInsights', section: 'nav.sections.matrimonyInsights', permission: 'view_matrimony_insights' },
  { path: '/matrimony/match-digest', icon: 'bi-envelope-paper', label: 'nav.items.matrimonyMatchDigest', section: 'nav.sections.matrimonyInsights', permission: 'view_matrimony_match_digest' },
  { path: '/matrimony/success-stories', icon: 'bi-heart', label: 'nav.items.matrimonySuccessStories', section: 'nav.sections.matrimonyInsights', permission: 'view_matrimony_success_stories' },

  // BestRide (Rider app admin, AdminWeb remote loaded under /admin/**): three setup sections (cities and coverage, vehicles and
  // fares, app content), then operations (rides, SOS, disputes, support, the live map and cash handling), people and fleet
  // (approving drivers and vendors, customers, fleet), then finance and insights. Gating is a UI hint; AdminWeb's permissionGuard
  // and the gateway enforce.
  { path: '/admin/config-settings', icon: 'bi-sliders', label: 'nav.items.configSettings', section: 'nav.sections.adminSetup', permission: 'system_config' },
  { path: '/admin/config-settings/cities', icon: 'bi-geo-alt', label: 'nav.items.cities', section: 'nav.sections.adminSetup', permission: 'system_config' },
  { path: '/admin/config-settings/serviceability', icon: 'bi-pin-map', label: 'nav.items.serviceability', section: 'nav.sections.adminSetup', permission: 'view_serviceability' },
  { path: '/admin/config-settings/vehicle-types', icon: 'bi-truck-front', label: 'nav.items.vehicleTypes', section: 'nav.sections.adminStep2VehiclesFares', permission: 'view_vehicle_types' },
  { path: '/admin/fare-engine', icon: 'bi-lightning', label: 'nav.items.fareEngine', section: 'nav.sections.adminStep2VehiclesFares', permission: 'manage_fares' },
  { path: '/admin/config-settings/banners', icon: 'bi-image', label: 'nav.items.banners', section: 'nav.sections.adminStep3AppContent', permission: 'manage_content' },
  { path: '/admin/config-settings/faqs', icon: 'bi-question-circle', label: 'nav.items.faqs', section: 'nav.sections.adminStep3AppContent', permission: 'manage_content' },
  { path: '/admin/config-settings/app-versions', icon: 'bi-phone', label: 'nav.items.appVersions', section: 'nav.sections.adminStep3AppContent', permission: 'manage_content' },
  { path: '/admin/dashboard', icon: 'bi-speedometer2', label: 'nav.items.dashboard', section: 'nav.sections.adminOperations', permission: 'view_dashboard' },
  { path: '/admin/sos', icon: 'bi-exclamation-octagon', label: 'nav.items.sos', section: 'nav.sections.adminOperations', permission: 'view_sos' },
  { path: '/admin/rides', icon: 'bi-car-front-fill', label: 'nav.items.rides', section: 'nav.sections.adminOperations', permission: 'view_rides' },
  { path: '/admin/ride-disputes', icon: 'bi-flag', label: 'nav.items.rideDisputes', section: 'nav.sections.adminOperations', permission: 'view_ride_disputes' },
  { path: '/admin/support', icon: 'bi-headset', label: 'nav.items.support', section: 'nav.sections.adminOperations', permission: 'resolve_disputes' },
  { path: '/admin/fleet-map', icon: 'bi-geo-alt', label: 'nav.items.fleetMap', section: 'nav.sections.adminOperations', permission: 'view_fleet_map' },
  { path: '/admin/cash-ops/driver-payouts', icon: 'bi-bank2', label: 'nav.items.cashDriverPayouts', section: 'nav.sections.adminOperations', permission: 'view_driver_payouts' },
  { path: '/admin/cash-ops/offline-handovers', icon: 'bi-cash-coin', label: 'nav.items.cashOfflineHandovers', section: 'nav.sections.adminOperations', permission: 'record_offline_handover' },
  { path: '/admin/cash-ops/ledger-check', icon: 'bi-clipboard2-check', label: 'nav.items.cashLedgerCheck', section: 'nav.sections.adminOperations', permission: 'view_ledger_check' },
  { path: '/admin/drivers', icon: 'bi-car-front', label: 'nav.items.drivers', section: 'nav.sections.adminPeopleFleet', permission: 'approve_drivers' },
  { path: '/admin/vendors', icon: 'bi-building', label: 'nav.items.vendors', section: 'nav.sections.adminPeopleFleet', permission: 'approve_vendors' },
  { path: '/admin/customers', icon: 'bi-people', label: 'nav.items.customers', section: 'nav.sections.adminPeopleFleet', permission: 'view_customers' },
  { path: '/admin/fleet', icon: 'bi-truck', label: 'nav.items.fleet', section: 'nav.sections.adminPeopleFleet', permission: 'view_fleet' },
  { path: '/admin/finance', icon: 'bi-credit-card', label: 'nav.items.finance', section: 'nav.sections.adminFinance', permission: 'view_financials' },
  { path: '/admin/analytics', icon: 'bi-bar-chart-line', label: 'nav.items.analytics', section: 'nav.sections.adminFinance', permission: 'export_reports' },
  { path: '/admin/audit', icon: 'bi-shield-exclamation', label: 'nav.items.auditLogs', section: 'nav.sections.adminFinance', permission: 'audit_logs' },

  // QA automation: its own application. Setup (the test cases and test sets, the studio, the endpoint inventory and API documents),
  // operations (the regression and smoke suites, executions, failures and the runs Jenkins published), then insights (the
  // overview and the executive summary).
  { path: '/qa/test-cases', icon: 'bi-card-checklist', label: 'nav.items.qaTestCases', section: 'nav.sections.qaSetup', permission: 'view_qa_test_cases' },
  { path: '/qa/test-sets', icon: 'bi-collection', label: 'nav.items.qaTestSets', section: 'nav.sections.qaSetup', permission: 'view_qa_test_sets' },
  { path: '/qa/test-case-studio', icon: 'bi-pencil-square', label: 'nav.items.qaTestCaseStudio', section: 'nav.sections.qaSetup', permission: 'view_qa_test_case_studio' },
  { path: '/qa/endpoint-inventory', icon: 'bi-diagram-3', label: 'nav.items.qaEndpointInventory', section: 'nav.sections.qaSetup', permission: 'view_qa_endpoint_inventory' },
  { path: '/qa/api-documents', icon: 'bi-journal-text', label: 'nav.items.qaApiDocuments', section: 'nav.sections.qaSetup', permission: 'view_qa_api_documents' },
  { path: '/qa/regression', icon: 'bi-list-check', label: 'nav.items.qaRegression', section: 'nav.sections.qaOperations', permission: 'view_qa_suites' },
  { path: '/qa/smoke', icon: 'bi-lightning-charge', label: 'nav.items.qaSmoke', section: 'nav.sections.qaOperations', permission: 'view_qa_suites' },
  { path: '/qa/executions/test-cases', icon: 'bi-journal-check', label: 'nav.items.qaExecutionsTestCases', section: 'nav.sections.qaOperations', permission: 'view_qa_executions' },
  { path: '/qa/executions/test-sets', icon: 'bi-collection-play', label: 'nav.items.qaExecutionsTestSets', section: 'nav.sections.qaOperations', permission: 'view_qa_test_set_executions' },
  { path: '/qa/failures', icon: 'bi-exclamation-triangle', label: 'nav.items.qaFailures', section: 'nav.sections.qaOperations', permission: 'view_qa_failures' },
  { path: '/system/qa', icon: 'bi-patch-check', label: 'nav.items.qaRuns', section: 'nav.sections.qaOperations', permission: 'view_qa_runs' },
  { path: '/qa/overview', icon: 'bi-speedometer2', label: 'nav.items.qaOverview', section: 'nav.sections.qaInsights', permission: 'view_qa_overview' },
  { path: '/qa/executive-summary', icon: 'bi-file-earmark-bar-graph', label: 'nav.items.qaExecutiveSummary', section: 'nav.sections.qaInsights', permission: 'view_qa_executive_summary' },

  // Train operations (AdminWeb remote, lazy module train-admin): setup, daily operations, then reconciliation and reports.
  { path: '/admin/train-admin/irctc-accounts', icon: 'bi-person-badge', label: 'nav.items.trainIrctcAccounts', section: 'nav.sections.trainSetup', permission: 'view_train_irctc_accounts' },
  { path: '/admin/train-admin/class-commissions', icon: 'bi-percent', label: 'nav.items.trainCommissions', section: 'nav.sections.trainSetup', permission: 'manage_train_config' },
  { path: '/admin/train-admin/travel-users', icon: 'bi-people', label: 'nav.items.trainTravelUsers', section: 'nav.sections.trainSetup', permission: 'view_train_travel_users' },
  { path: '/admin/train-admin/dashboard', icon: 'bi-speedometer2', label: 'nav.items.trainDashboard', section: 'nav.sections.trainOperations', permission: 'view_train_dashboard' },
  { path: '/admin/train-admin/bookings', icon: 'bi-ticket-perforated', label: 'nav.items.trainBookings', section: 'nav.sections.trainOperations', permission: 'view_train_bookings' },
  { path: '/admin/train-admin/pnr', icon: 'bi-search', label: 'nav.items.trainPnr', section: 'nav.sections.trainOperations', permission: 'view_train_pnr' },
  { path: '/admin/train-admin/refunds', icon: 'bi-receipt', label: 'nav.items.trainRefunds', section: 'nav.sections.trainOperations', permission: 'view_train_refunds' },
  { path: '/admin/train-admin/refunds/stuck', icon: 'bi-hourglass-split', label: 'nav.items.trainStuckPayments', section: 'nav.sections.trainOperations', permission: 'view_train_stuck_payments' },
  { path: '/admin/train-admin/tdr', icon: 'bi-file-earmark-text', label: 'nav.items.trainTdr', section: 'nav.sections.trainOperations', permission: 'view_train_tdr' },
  { path: '/admin/train-admin/alerts', icon: 'bi-exclamation-triangle', label: 'nav.items.trainAlerts', section: 'nav.sections.trainOperations', permission: 'view_train_alerts' },
  { path: '/admin/train-admin/vikalp', icon: 'bi-signpost-split', label: 'nav.items.trainVikalp', section: 'nav.sections.trainOperations', permission: 'view_train_vikalp' },
  { path: '/admin/train-admin/reconciliation', icon: 'bi-arrow-repeat', label: 'nav.items.trainReconciliation', section: 'nav.sections.trainReconciliation', permission: 'view_train_reconciliation' },
  { path: '/admin/train-admin/reports', icon: 'bi-bar-chart-line', label: 'nav.items.trainReports', section: 'nav.sections.trainReconciliation', permission: 'view_train_reports' },
  { path: '/admin/train-admin/report-jobs', icon: 'bi-file-earmark-arrow-down', label: 'nav.items.trainReportJobs', section: 'nav.sections.trainReconciliation', permission: 'view_train_reports' },
  // Access set-up, in the order it has to be done. Each step needs the ones before it:
  // 1 the organization and the applications it uses, 2 the forms, messages and documents those applications send and collect,
  // 3 the roles (what may be done) and then the users. Its operations (the onboarding queues) come last; the wallet screens are in Wallet.
  // The franchise screens are an application of their own (the block after this one).
  { path: '/org-structure/builder', icon: 'bi-pencil-square', label: 'nav.items.orgBuilder', section: 'nav.sections.accessStep1Organization', permission: 'manage_org_structure' },
  { path: '/org-structure/chart', icon: 'bi-diagram-3-fill', label: 'nav.items.orgChart', section: 'nav.sections.accessStep1Organization', permission: 'view_org_chart' },
  { path: '/org-structure/import', icon: 'bi-file-earmark-arrow-up', label: 'nav.items.orgImport', section: 'nav.sections.accessStep1Organization', permission: 'import_org_employees' },
  { path: '/access-management/roles', icon: 'bi-person-badge', label: 'nav.items.roles', section: 'nav.sections.accessStep3People', permission: 'view_roles' },
  { path: '/access-management/users', icon: 'bi-people', label: 'nav.items.users', section: 'nav.sections.accessStep3People', permission: 'view_users' },
  { path: '/org-structure/logins', icon: 'bi-person-vcard', label: 'nav.items.orgLogins', section: 'nav.sections.accessStep3People', permission: 'manage_org_logins', hidden: true },
  { path: '/access-management/organizations', icon: 'bi-building', label: 'nav.items.organizations', section: 'nav.sections.accessStep2Organization', permission: 'view_organizations' },
  { path: '/access-management/applications', icon: 'bi-grid-3x3-gap', label: 'nav.items.applications', section: 'nav.sections.accessStep2Organization', permission: 'view_applications' },
  { path: '/access-management/clients', icon: 'bi-plug', label: 'nav.items.apiClients', section: 'nav.sections.accessStep2Organization', permission: 'view_api_clients' },
  { path: '/access-management/security-policies', icon: 'bi-shield-lock', label: 'nav.items.securityPolicies', section: 'nav.sections.accessStep2Organization', permission: 'view_security_policies' },
  { path: '/access-management/setup-checklist', icon: 'bi-list-check', label: 'nav.items.setupChecklist', section: 'nav.sections.accessStep2Organization', permission: 'view_setup_checklist' },
  { path: '/onboarding-forms', icon: 'bi-ui-checks-grid', label: 'nav.items.onboardingForms', section: 'nav.sections.accessStep2System', permission: 'manage_onboarding' },
  { path: '/nms-templates', icon: 'bi-chat-square-text', label: 'nav.items.communicationTemplates', section: 'nav.sections.accessStep2System', permission: 'view_nms_templates' },
  { path: '/nms-templates/events', icon: 'bi-calendar-event', label: 'nav.items.eventsAttributes', section: 'nav.sections.accessStep2System', permission: 'view_nms_events' },
  { path: '/document-management/document-types', icon: 'bi-file-earmark-check', label: 'nav.items.documentTypes', section: 'nav.sections.accessStep2System', permission: 'view_document_types', matchPrefix: true },
  { path: '/document-management/lifecycle', icon: 'bi-clock-history', label: 'nav.items.documentLifecycle', section: 'nav.sections.accessStep2System', permission: 'view_document_lifecycle', matchPrefix: true },
  { path: '/onboarding-reviews', icon: 'bi-clipboard-check', label: 'nav.items.onboardingReviews', section: 'nav.sections.accessOperations', permission: 'manage_onboarding' },
  { path: '/onboarded-users', icon: 'bi-people', label: 'nav.items.onboardedUsers', section: 'nav.sections.accessOperations', permission: 'manage_onboarding' },

  // Franchise (catalogue application FRANCHISE_MANAGEMENT_APP): its own application, for HQ staff. Setup first (the places and
  // plans a franchise is sold on, then the money rules), then operations (opening franchises and adding members, payout
  // approvals, ledger, driver subscriptions). A franchise member's own pages are in the My franchise workspace.
  { path: '/access-management/franchise-locations', icon: 'bi-geo-alt', label: 'nav.items.franchiseLocations', section: 'nav.sections.franchiseSetup', permission: 'view_franchise_locations' },
  { path: '/access-management/franchise-demand', icon: 'bi-megaphone', label: 'nav.items.franchiseDemand', section: 'nav.sections.franchiseSetup', permission: 'view_franchise_demand' },
  { path: '/access-management/franchise-plans', icon: 'bi-tags', label: 'nav.items.franchisePlans', section: 'nav.sections.franchiseSetup', permission: 'view_franchise_plans' },
  { path: '/access-management/franchise-packages', icon: 'bi-box-seam', label: 'nav.items.franchisePackages', section: 'nav.sections.franchiseSetup', permission: 'view_franchise_package_templates' },
  { path: '/admin/franchise-admin/commission-plans', icon: 'bi-percent', label: 'nav.items.franchiseCommissionPlans', section: 'nav.sections.franchiseMoney', permission: 'view_commission_plans' },
  { path: '/admin/franchise-admin/referral-plans', icon: 'bi-people', label: 'nav.items.franchiseReferralPlans', section: 'nav.sections.franchiseMoney', permission: 'view_referral_plans' },
  { path: '/admin/franchise-admin/payout-rules', icon: 'bi-wallet2', label: 'nav.items.franchisePayoutRules', section: 'nav.sections.franchiseMoney', permission: 'view_payout_rules' },
  { path: '/admin/franchise-admin/rule-resolver', icon: 'bi-search', label: 'nav.items.franchiseRuleResolver', section: 'nav.sections.franchiseMoney', permission: 'view_franchise_rule_resolver' },
  { path: '/access-management/franchises', icon: 'bi-shop', label: 'nav.items.franchises', section: 'nav.sections.franchiseOperations', permission: 'view_franchises' },
  { path: '/access-management/franchise-members', icon: 'bi-people-fill', label: 'nav.items.franchiseMembers', section: 'nav.sections.franchiseOperations', permission: 'view_franchise_members' },
  { path: '/admin/franchise-admin/payout-approvals', icon: 'bi-check2-square', label: 'nav.items.franchisePayoutApprovals', section: 'nav.sections.franchiseOperations', permission: 'view_payout_approvals' },
  { path: '/admin/franchise-admin/ledger', icon: 'bi-journal-text', label: 'nav.items.franchiseLedger', section: 'nav.sections.franchiseOperations', permission: 'view_franchise_ledger' },
  { path: '/admin/franchise-admin/driver-subscriptions', icon: 'bi-calendar-check', label: 'nav.items.franchiseDriverSubscriptions', section: 'nav.sections.franchiseOperations', permission: 'view_driver_subscriptions' },

  // My franchise workspace: every franchise member's own franchise, earnings and payouts (owners and agents alike), then the
  // owner-only groups per application, which only an owner's privileges open. Each group still follows that application's
  // subscription (WORKSPACE_SECTION_APPS in nav-areas.constants.ts). Agent KYC and franchise setup open from My franchises.
  { path: '/access-management/my-franchises', icon: 'bi-person-workspace', label: 'nav.items.myFranchises', section: 'nav.sections.ownerFranchise', permission: 'view_my_franchises' },
  { path: '/access-management/my-earnings', icon: 'bi-cash-coin', label: 'nav.items.myEarnings', section: 'nav.sections.ownerFranchise', permission: 'view_my_earnings' },
  { path: '/access-management/my-payouts', icon: 'bi-wallet2', label: 'nav.items.myPayouts', section: 'nav.sections.ownerFranchise', permission: 'view_my_payouts' },
  { path: '/matrimony/agent/complaints', icon: 'bi-exclamation-octagon', label: 'nav.items.matrimonyComplaints', section: 'nav.sections.ownerMatrimony', permission: 'view_matrimony_agent_complaints' },
  { path: '/matrimony/agent/ratings', icon: 'bi-star', label: 'nav.items.matrimonyAgentRatings', section: 'nav.sections.ownerMatrimony', permission: 'view_matrimony_agent_ratings', operatingModels: FRANCHISE_OPERATING_MODELS },
  { path: '/matrimony/agent/funnel', icon: 'bi-funnel', label: 'nav.items.matrimonyFunnel', section: 'nav.sections.ownerMatrimony', permission: 'view_matrimony_agent_funnel', operatingModels: FRANCHISE_OPERATING_MODELS },
  { path: '/matrimony/agent/reassign', icon: 'bi-person-check', label: 'nav.items.matrimonyReassignCustomers', section: 'nav.sections.ownerMatrimony', permission: 'view_matrimony_agent_reassign', operatingModels: FRANCHISE_OPERATING_MODELS },
  // The owner's drivers (BestRide): opens the only franchise's drivers, or My franchises to pick one.
  { path: '/access-management/my-drivers', icon: 'bi-car-front', label: 'nav.items.myDrivers', section: 'nav.sections.ownerBestRide', permission: 'view_franchise_drivers' },

  // Agent workspace: an agent's own daily work, grouped by application (the matrimony desk in the order of the work: onboard a
  // customer, then proposals; the wallet desk with the agent's float). Company-run agents use it too, so it is not franchise-only.
  { path: '/matrimony/agent', icon: 'bi-speedometer2', label: 'nav.items.matrimonyAgentDashboard', section: 'nav.sections.agentMatrimony', permission: 'view_matrimony_agent' },
  { path: '/matrimony/agent/add-customer', icon: 'bi-person-plus', label: 'nav.items.matrimonyAddCustomer', section: 'nav.sections.agentMatrimony', permission: 'view_matrimony_agent_add_customer' },
  { path: '/matrimony/agent/consent', icon: 'bi-patch-check', label: 'nav.items.matrimonyCustomerConsent', section: 'nav.sections.agentMatrimony', permission: 'view_matrimony_agent_consent' },
  { path: '/matrimony/agent/biodata', icon: 'bi-card-list', label: 'nav.items.matrimonyCustomerBiodata', section: 'nav.sections.agentMatrimony', permission: 'view_matrimony_agent_biodata' },
  { path: '/matrimony/agent/profile-media', icon: 'bi-images', label: 'nav.items.matrimonyCustomerMedia', section: 'nav.sections.agentMatrimony', permission: 'view_matrimony_agent_media' },
  { path: '/matrimony/agent/proposals', icon: 'bi-people', label: 'nav.items.matrimonyProposals', section: 'nav.sections.agentMatrimony', permission: 'view_matrimony_agent_proposals', operatingModels: FRANCHISE_OPERATING_MODELS },
  { path: '/wallet/agent', icon: 'bi-wallet2', label: 'nav.items.walletAgent', section: 'nav.sections.agentWallet', permission: 'view_agent_wallet' },
  { path: '/wallet/agent/load', icon: 'bi-plus-circle', label: 'nav.items.walletLoad', section: 'nav.sections.agentWallet', permission: 'load_agent_wallet' },
  { path: '/wallet/agent/customers', icon: 'bi-people', label: 'nav.items.walletCustomers', section: 'nav.sections.agentWallet', permission: 'view_agent_customers' },
  { path: '/wallet/agent/cash', icon: 'bi-cash-stack', label: 'nav.items.walletCash', section: 'nav.sections.agentWallet', permission: 'hand_over_cash' },
  { path: '/wallet/agent/earnings', icon: 'bi-graph-up-arrow', label: 'nav.items.walletEarnings', section: 'nav.sections.agentWallet', permission: 'view_agent_earnings' },
  { path: '/access-management/my-float', icon: 'bi-cash-stack', label: 'nav.items.myFloat', section: 'nav.sections.agentWallet', permission: 'view_my_float' },

  // Royal Food (food delivery admin, federation remote 'railcab-food') under /food/**: setup, operations, then insights. Hosted,
  // the console shows no menu of its own, so this is the only menu. Keys match FOOD_SCREEN_PERMISSIONS (food-admin.constants.ts).
  { path: '/food/restaurants', icon: 'bi-shop', label: 'nav.items.foodRestaurants', section: 'nav.sections.foodSetup', permission: 'view_food_restaurants', matchPrefix: true },
  { path: '/food/categories', icon: 'bi-tags', label: 'nav.items.foodCategories', section: 'nav.sections.foodSetup', permission: 'view_food_categories', matchPrefix: true },
  { path: '/food/foods', icon: 'bi-egg-fried', label: 'nav.items.foodFoodItems', section: 'nav.sections.foodSetup', permission: 'view_food_foods', matchPrefix: true },
  { path: '/food/coupons', icon: 'bi-ticket-perforated', label: 'nav.items.foodCoupons', section: 'nav.sections.foodSetup', permission: 'view_food_coupons', matchPrefix: true },
  { path: '/food/offers', icon: 'bi-tag', label: 'nav.items.foodOffers', section: 'nav.sections.foodSetup', permission: 'view_food_offers', matchPrefix: true },
  { path: '/food/delivery-partners', icon: 'bi-bicycle', label: 'nav.items.foodDeliveryPartners', section: 'nav.sections.foodSetup', permission: 'view_food_delivery_partners', matchPrefix: true },
  { path: '/food/admin-users', icon: 'bi-shield-lock', label: 'nav.items.foodAdminUsers', section: 'nav.sections.foodSetup', permission: 'view_food_admin_users', matchPrefix: true },
  { path: '/food/notifications', icon: 'bi-bell', label: 'nav.items.foodNotifications', section: 'nav.sections.foodSetup', permission: 'view_food_notifications', matchPrefix: true },
  { path: '/food/settings', icon: 'bi-gear', label: 'nav.items.foodSettings', section: 'nav.sections.foodSetup', permission: 'view_food_settings', matchPrefix: true },
  { path: '/food/dashboard', icon: 'bi-speedometer2', label: 'nav.items.foodDashboard', section: 'nav.sections.foodOperations', permission: 'view_food_dashboard' },
  { path: '/food/orders', icon: 'bi-receipt', label: 'nav.items.foodOrders', section: 'nav.sections.foodOperations', permission: 'view_food_orders', matchPrefix: true },
  { path: '/food/payments', icon: 'bi-credit-card', label: 'nav.items.foodPayments', section: 'nav.sections.foodOperations', permission: 'view_food_payments', matchPrefix: true },
  { path: '/food/refunds', icon: 'bi-arrow-left-right', label: 'nav.items.foodRefunds', section: 'nav.sections.foodOperations', permission: 'view_food_refunds', matchPrefix: true },
  { path: '/food/customers', icon: 'bi-people', label: 'nav.items.foodCustomers', section: 'nav.sections.foodOperations', permission: 'view_food_customers', matchPrefix: true },
  { path: '/food/reviews', icon: 'bi-chat-square-text', label: 'nav.items.foodReviews', section: 'nav.sections.foodOperations', permission: 'view_food_reviews', matchPrefix: true },
  { path: '/food/support-tickets', icon: 'bi-headset', label: 'nav.items.foodSupportTickets', section: 'nav.sections.foodOperations', permission: 'view_food_support_tickets', matchPrefix: true },
  { path: '/food/live-board', icon: 'bi-broadcast', label: 'nav.items.foodLiveBoard', section: 'nav.sections.foodOperations', permission: 'view_food_live_board' },
  { path: '/food/refund-requests', icon: 'bi-arrow-counterclockwise', label: 'nav.items.foodRefundRequests', section: 'nav.sections.foodOperations', permission: 'view_food_refund_requests', matchPrefix: true },
  { path: '/food/restaurant-approvals', icon: 'bi-shop-window', label: 'nav.items.foodRestaurantApprovals', section: 'nav.sections.foodOperations', permission: 'view_food_restaurant_approvals', matchPrefix: true },
  { path: '/food/menu-reviews', icon: 'bi-card-checklist', label: 'nav.items.foodMenuReviews', section: 'nav.sections.foodOperations', permission: 'view_food_menu_reviews' },
  { path: '/food/settlements', icon: 'bi-cash-stack', label: 'nav.items.foodSettlements', section: 'nav.sections.foodOperations', permission: 'view_food_settlements' },
  // The restaurant-partner portal (same remote, its own mount): shown to partners, who hold FOOD_PARTNER_* privileges only.
  { path: '/food-partner', icon: 'bi-shop', label: 'nav.items.foodPartner', section: 'nav.sections.foodOperations', permission: 'view_restaurant_partner', matchPrefix: true },
  { path: '/food/reports', icon: 'bi-file-earmark-bar-graph', label: 'nav.items.foodReports', section: 'nav.sections.foodInsights', permission: 'view_food_reports' },
  { path: '/food/analytics', icon: 'bi-graph-up', label: 'nav.items.foodAnalytics', section: 'nav.sections.foodInsights', permission: 'view_food_analytics' },
  // Best Purchase inventory (federation remote 'railcab-ims', Rider_IMSWeb, Rider_IMS) under /inventory/**: setup (items, sites),
  // then operations (receiving, transfers, the stock ledger).
  { path: '/inventory/items', icon: 'bi-box-seam', label: 'nav.items.inventoryItems', section: 'nav.sections.imsSetup', permission: 'view_inventory_items' },
  { path: '/inventory/locations', icon: 'bi-geo-alt', label: 'nav.items.inventoryLocations', section: 'nav.sections.imsSetup', permission: 'view_inventory_locations' },
  { path: '/inventory/dashboard', icon: 'bi-speedometer2', label: 'nav.items.inventoryDashboard', section: 'nav.sections.imsOperations', permission: 'view_inventory_dashboard' },
  { path: '/inventory/receiving', icon: 'bi-box-arrow-in-down', label: 'nav.items.inventoryReceiving', section: 'nav.sections.imsOperations', permission: 'view_inventory_receiving', matchPrefix: true },
  { path: '/inventory/transfers', icon: 'bi-arrow-left-right', label: 'nav.items.inventoryTransfers', section: 'nav.sections.imsOperations', permission: 'view_inventory_transfers', matchPrefix: true },
  { path: '/inventory/ledger', icon: 'bi-journal-text', label: 'nav.items.inventoryLedger', section: 'nav.sections.imsOperations', permission: 'view_inventory_ledger' },
  // Developer reference (API docs, generated Java docs, live service registry, workflow).
  { path: '/chart-gallery', icon: 'bi-bar-chart-line', label: 'nav.items.chartGallery', section: 'nav.sections.developerTools', permission: DEVELOPER_DOCS_PERMISSION },
  { path: '/api-docs', icon: 'bi-code-slash', label: 'nav.items.apiDocs', section: 'nav.sections.developerTools', permission: DEVELOPER_DOCS_PERMISSION },
  { path: '/api-docs/java-doc', icon: 'bi-code-slash', label: 'nav.items.javaDocs', section: 'nav.sections.developerTools', permission: DEVELOPER_DOCS_PERMISSION, matchPrefix: true },
  { path: '/service-registry', icon: 'bi-hdd-network', label: 'nav.items.serviceRegistry', section: 'nav.sections.developerTools', permission: SERVICE_REGISTRY_PERMISSION },
  { path: '/workflow', icon: 'bi-diagram-3', label: 'nav.items.workflow', section: 'nav.sections.developerTools', permission: WORKFLOW_TASKS_PERMISSION, matchPrefix: true },
  // Test coverage of every service and web app: a developer view, so it lives with the other developer tools.
  { path: '/system/coverage', icon: 'bi-clipboard-data', label: 'nav.items.codeCoverage', section: 'nav.sections.developerTools', permission: 'view_code_coverage' },

  // Account.
  { path: '/profile', icon: 'bi-person-circle', label: 'nav.items.myProfile', section: 'nav.sections.account', permission: null },
];

/** Sections that start folded in the sidebar: every application's setup (so it opens on its operations) and the developer tools. */
export const SIDEBAR_DEFAULT_COLLAPSED_SECTIONS: ReadonlyArray<NavSection> = [
  'nav.sections.franchiseSetup',
  'nav.sections.franchiseMoney',
  'nav.sections.walletSetup',
  'nav.sections.accountingSetup',
  'nav.sections.matrimonySetup',
  'nav.sections.adminSetup',
  'nav.sections.adminStep2VehiclesFares',
  'nav.sections.adminStep3AppContent',
  'nav.sections.foodSetup',
  'nav.sections.imsSetup',
  'nav.sections.qaSetup',
  'nav.sections.developerTools',
];

/** localStorage key remembering which sidebar sections the user folded or opened. */
export const SIDEBAR_SECTION_STATE_KEY = 'sidebar.sections.collapsed';

/** Quick-navigate shortcuts that have no sidebar entry (kept so the search doesn't lose them). */
export const EXTRA_QUICK_NAV: ReadonlyArray<Pick<NavItem, 'label' | 'path' | 'icon' | 'section'>> = [
  { label: 'nav.items.myBookings', path: '/my-bookings', icon: 'bi-ticket-perforated', section: 'nav.sections.main' },
  { label: 'nav.items.notifications', path: '/notifications', icon: 'bi-bell', section: 'nav.sections.main' },
  { label: 'nav.items.changePassword', path: '/change-password', icon: 'bi-key-fill', section: 'nav.sections.account' },
];

/**
 * The menu entry that owns {@code url}: the one with the longest path that is the url or a parent of it, so
 * /admin/config-settings/cities beats /admin/config-settings and a sub-page such as
 * /admin/config-settings/train.irctcOtpMax still resolves to Settings. Query string and fragment are ignored.
 */
export function findNavItemForUrl(url: string): NavItem | undefined {
  const path = url.split(/[?#]/)[0].replace(/\/+$/, '') || '/';
  let best: NavItem | undefined;
  for (const item of NAV_ITEMS) {
    const matches = path === item.path || path.startsWith(item.path + '/');
    if (matches && (!best || item.path.length > best.path.length)) {
      best = item;
    }
  }
  return best;
}
