import { HQ_PERMISSION_PRIVILEGES } from './hq-privilege-map';

const CODE_FORMAT = /^[A-Z][A-Z0-9_]*$/;

function screenKeys(prefix: string, exclude: string[]): string[] {
  return Object.keys(HQ_PERMISSION_PRIVILEGES).filter((k) => k.startsWith(prefix) && !exclude.includes(k));
}

function codesOf(keys: string[]): string[] {
  return keys.flatMap((k) => [...HQ_PERMISSION_PRIVILEGES[k]]);
}

describe('HQ_PERMISSION_PRIVILEGES', () => {
  it('maps every screen key to at least one privilege code in privilege.json form', () => {
    for (const [key, codes] of Object.entries(HQ_PERMISSION_PRIVILEGES)) {
      expect(codes.length, key).toBeGreaterThan(0);
      for (const code of codes) expect(code, key).toMatch(CODE_FORMAT);
    }
  });

  it('keeps known individual mappings', () => {
    expect(HQ_PERMISSION_PRIVILEGES['view_rides']).toEqual(['ADMIN_RIDE_LIST']);
    expect(HQ_PERMISSION_PRIVILEGES['view_users']).toEqual(['USER_LIST', 'UAM_USERS_MANAGE']);
    expect(HQ_PERMISSION_PRIVILEGES['view_train_tdr']).toEqual(['TRAIN_ADMIN_TDR_READ', 'TRAIN_ADMIN_TDR_FILINGS_READ']);
    expect(HQ_PERMISSION_PRIVILEGES['load_agent_wallet']).toEqual(['WALLET_AGENT_LOAD_CREATE']);
    expect(HQ_PERMISSION_PRIVILEGES['view_serviceability']).toEqual(['CONFIG_SERVICEABILITY_READ']);
    expect(HQ_PERMISSION_PRIVILEGES['view_vehicle_types']).toEqual(['CONFIG_VEHICLE_TYPE_LIST_READ']);
  });

  it('opens each franchise money screen with the privileges of its main read or list call', () => {
    expect([...HQ_PERMISSION_PRIVILEGES['view_franchise_fee_schedule']]).toEqual(['FRANCHISE_FEE_SCHEDULE_LIST']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_commission_plans']]).toEqual(['COMMISSION_PLAN_LIST', 'COMMISSION_PLAN_READ']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_referral_plans']]).toEqual(['REFERRAL_PLAN_LIST']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_payout_rules']]).toEqual(['PAYOUT_RULE_LIST']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_franchise_rule_resolver']]).toEqual(['FRANCHISE_RULE_RESOLVE_READ']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_payout_approvals']]).toEqual(['PAYOUT_REQUEST_LIST']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_franchise_ledger']]).toEqual(['LEDGER_ENTRY_LIST', 'COMMISSION_STATEMENT_READ']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_driver_subscriptions']]).toEqual(['DRIVER_SUBSCRIPTION_PLAN_LIST', 'DRIVER_SUBSCRIPTION_LIST']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_driver_payouts']]).toEqual(['DRIVER_PAYOUT_LIST']);
    expect([...HQ_PERMISSION_PRIVILEGES['record_offline_handover']]).toEqual(['WALLET_OFFLINE_HANDOVER_CREATE', 'WALLET_OFFLINE_SALES_HELD_LIST']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_ledger_check']]).toEqual(['FRANCHISE_SALE_CHECK_LIST']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_franchise_members']]).toEqual(['FRANCHISE_MEMBER_LIST']);
  });

  it('opens the franchise demand screen with its summary call only', () => {
    expect([...HQ_PERMISSION_PRIVILEGES['view_franchise_demand']]).toEqual(['FRANCHISE_DEMAND_SUMMARY_LIST']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_setup_checklist']]).toEqual(['FRANCHISE_SETUP_CHECKLIST_READ']);
  });

  it('opens the train area with any train screen privilege', () => {
    const expected = codesOf(screenKeys('view_train_', ['view_train_ops']));
    expect(expected.length).toBeGreaterThan(0);
    expect([...HQ_PERMISSION_PRIVILEGES['view_train_ops']]).toEqual(expected);
  });

  it('opens the matrimony portal with any matrimony screen privilege', () => {
    const expected = codesOf(screenKeys('view_matrimony_', []));
    expect([...HQ_PERMISSION_PRIVILEGES['view_matrimony']]).toEqual(expected);
  });

  it('opens the reports portal with any reports screen privilege', () => {
    const expected = codesOf(screenKeys('view_reports_', []));
    expect([...HQ_PERMISSION_PRIVILEGES['view_reports']]).toEqual(expected);
  });

  it('opens the admin wallet area with each admin wallet privilege exactly once', () => {
    const adminWalletKeys = screenKeys('view_wallet_', ['view_wallet_ops']);
    const expected = [...new Set(codesOf(adminWalletKeys))];
    const ops = HQ_PERMISSION_PRIVILEGES['view_wallet_ops'];
    expect([...ops]).toEqual(expected);
    expect(new Set(ops).size).toBe(ops.length);
    // WALLET_ADMIN_ACCOUNT_LIST unlocks two screens but is listed once.
    expect(ops.filter((c) => c === 'WALLET_ADMIN_ACCOUNT_LIST')).toHaveLength(1);
  });

  it('opens each QA automation dashboard on the privilege of its main read or list call', () => {
    expect([...HQ_PERMISSION_PRIVILEGES['view_qa_overview']]).toEqual(['CONFIG_QA_OVERVIEW_READ']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_qa_suites']]).toEqual(['CONFIG_QA_TEST_CASE_LIST']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_qa_failures']]).toEqual(['CONFIG_QA_FAILURE_READ']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_qa_executions']]).toEqual(['CONFIG_QA_TEST_CASE_LIST']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_qa_test_set_executions']]).toEqual(['CONFIG_QA_EXECUTION_LIST']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_qa_executive_summary']]).toEqual(['CONFIG_QA_EXECUTIVE_SUMMARY_READ']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_qa_test_cases']]).toEqual(['CONFIG_QA_TEST_CASE_LIST']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_qa_test_sets']]).toEqual(['CONFIG_QA_TEST_SET_LIST']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_qa_endpoint_inventory']]).toEqual(['CONFIG_QA_ENDPOINT_LIST']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_qa_test_case_studio']]).toEqual(['CONFIG_QA_STUDIO_CASE_LIST']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_qa_api_documents']]).toEqual(['CONFIG_QA_DOCUMENT_LIST']);
    // The runs screen that was there first keeps its own key.
    expect([...HQ_PERMISSION_PRIVILEGES['view_qa_runs']]).toEqual(['CONFIG_QA_RUN_LIST']);
  });

  it('opens each accounting screen with the contract privilege of its main read or list call', () => {
    expect([...HQ_PERMISSION_PRIVILEGES['view_wallet_accounting_transactions']]).toEqual(['WALLET_ACCOUNTING_TRANSACTION_LIST']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_wallet_accounting_summary']]).toEqual(['WALLET_ACCOUNTING_SUMMARY_READ']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_wallet_accounting_commissions']]).toEqual(['WALLET_ACCOUNTING_COMMISSION_LIST']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_wallet_accounting_franchise']]).toEqual(['WALLET_ACCOUNTING_FRANCHISE_READ']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_wallet_accounting_party_statement']]).toEqual(['WALLET_ACCOUNTING_PARTY_STATEMENT_READ']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_wallet_system_accounts']]).toEqual(['WALLET_ACCOUNTING_SYSTEM_ACCOUNT_LIST', 'WALLET_ADMIN_ACCOUNT_LIST']);
    // The accounting screens open the admin wallet area too, and the franchise owner's own view opens My earnings.
    expect(HQ_PERMISSION_PRIVILEGES['view_wallet_ops']).toContain('WALLET_ACCOUNTING_TRANSACTION_LIST');
    expect(HQ_PERMISSION_PRIVILEGES['view_my_earnings']).toContain('WALLET_FRANCHISE_ACCOUNTING_MINE_READ');
  });

  it('opens each accounting portal screen with the privilege of its main read or list call, outside the admin wallet area', () => {
    expect([...HQ_PERMISSION_PRIVILEGES['view_accounting_chart']]).toEqual(['WALLET_ACCOUNTING_CHART_LIST']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_accounting_trial_balance']]).toEqual(['WALLET_ACCOUNTING_TRIAL_BALANCE_READ']);
    expect(HQ_PERMISSION_PRIVILEGES['view_wallet_ops']).not.toContain('WALLET_ACCOUNTING_CHART_LIST');
    expect(HQ_PERMISSION_PRIVILEGES['view_wallet_ops']).not.toContain('WALLET_ACCOUNTING_TRIAL_BALANCE_READ');
  });

  it('opens each inventory portal screen with an IMS catalogue read, and the inventory mount with any of them', () => {
    expect([...HQ_PERMISSION_PRIVILEGES['view_inventory_dashboard']]).toEqual(['IMS_ITEM_READ']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_inventory_items']]).toEqual(['IMS_ITEM_READ']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_inventory_locations']]).toEqual(['IMS_LOCATION_READ', 'IMS_SITE_READ']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_inventory_receiving']]).toEqual(['IMS_ITEM_READ']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_inventory_transfers']]).toEqual(['IMS_SITE_READ']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_inventory_ledger']]).toEqual(['IMS_ITEM_READ']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_inventory']].sort()).toEqual(['IMS_ITEM_READ', 'IMS_LOCATION_READ', 'IMS_SITE_READ']);
  });

  it('opens each Food screen with the catalogue READ privilege of its resource, and the Food mount with any of them', () => {
    expect([...HQ_PERMISSION_PRIVILEGES['view_food_orders']]).toEqual(['FOOD_ORDER_READ']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_food_delivery_partners']]).toEqual(['FOOD_DELIVERY_PARTNER_READ']);
    // The food audit log moved to the platform audit trail (Rider_AuditService): no food screen of its own.
    expect(HQ_PERMISSION_PRIVILEGES['view_food_audit_logs' as keyof typeof HQ_PERMISSION_PRIVILEGES]).toBeUndefined();
    // Insight screens: their own READ privilege, or the operator privilege they had before (the food backend admits operators).
    expect([...HQ_PERMISSION_PRIVILEGES['view_food_dashboard']]).toEqual(['FOOD_DASHBOARD_READ', 'ORGANIZATION_LIST']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_food_reports']]).toEqual(['FOOD_REPORT_READ', 'ORGANIZATION_LIST']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_food_analytics']]).toEqual(['FOOD_ANALYTICS_READ', 'ORGANIZATION_LIST']);
    expect(HQ_PERMISSION_PRIVILEGES['view_food_admin']).toBeUndefined();
    const foodScreens = Object.keys(HQ_PERMISSION_PRIVILEGES).filter((key) => key.startsWith('view_food_'));
    expect(foodScreens).toHaveLength(23);
    for (const key of foodScreens) {
      const [own, ...rest] = HQ_PERMISSION_PRIVILEGES[key];
      expect(own, key).toMatch(/^FOOD_[A-Z_]+_(READ|LIST)$/);
      expect(rest.every((code) => code === 'ORGANIZATION_LIST'), key).toBe(true);
      expect(HQ_PERMISSION_PRIVILEGES['view_food'], key).toContain(own);
    }
    expect(HQ_PERMISSION_PRIVILEGES['view_food']).toContain('ORGANIZATION_LIST');
    expect(HQ_PERMISSION_PRIVILEGES['view_food']).toHaveLength(24);
    // The operations screens open with their main list call.
    expect([...HQ_PERMISSION_PRIVILEGES['view_food_live_board']]).toEqual(['FOOD_LIVE_BOARD_READ']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_food_restaurant_approvals']]).toEqual(['FOOD_RESTAURANT_APPLICATION_LIST']);
    // The partner portal is opened by a partner privilege only, never by the staff Food mount.
    expect([...HQ_PERMISSION_PRIVILEGES['view_restaurant_partner']]).toEqual(['FOOD_PARTNER_RESTAURANT_LIST']);
    expect(HQ_PERMISSION_PRIVILEGES['view_food']).not.toContain('FOOD_PARTNER_RESTAURANT_LIST');
  });

  it('does not count the wallet home as an administrator key', () => {
    expect(HQ_PERMISSION_PRIVILEGES['wallet_home']).toContain('WALLET_AGENT_SELF_READ');
    expect(HQ_PERMISSION_PRIVILEGES['view_wallet_ops']).not.toContain('WALLET_AGENT_SELF_READ');
  });
});
