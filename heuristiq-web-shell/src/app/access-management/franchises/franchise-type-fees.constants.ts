/** Translation namespace of the "fees by place type" grid on the franchise plans screen. */
export const TYPE_FEES_I18N = 'accessManagement.franchisePlans.byType';

/** How many type-default plans of an application are read to fill the grid (there is at most one per type, country and parent). */
export const TYPE_FEES_PLANS_PAGE_SIZE = 200;

/** The minimum a row's fee and agents are taken from the plan form's own rules (see scopedPlanFormError). */
export const TYPE_FEES_STATE_KEYS: Readonly<Record<string, string>> = {
  new: `${TYPE_FEES_I18N}.state.new`,
  changed: `${TYPE_FEES_I18N}.state.changed`,
  unchanged: `${TYPE_FEES_I18N}.state.saved`,
  incomplete: `${TYPE_FEES_I18N}.state.incomplete`,
};

/** The agent wallet multiplier used until the backend reports its own (the franchise is given twice its fee). */
export const DEFAULT_AGENT_WALLET_MULTIPLIER = 2;

/** Paise in a rupee: the wallet shares are worked out in whole paise, as the backend does. */
export const PAISE_PER_UNIT = 100;
