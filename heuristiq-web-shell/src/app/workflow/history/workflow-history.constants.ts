/** Workflow variables that hold a UAM user id: shown by the person's name instead of the id. */
export const USER_VARIABLES: readonly string[] = ['decidedBy', 'initiator', 'startedBy', 'assignee', 'requestedBy', 'reviewer'];

/**
 * The plain-language summary the history dialog shows, in this order: the variable and the translation key of its label. A
 * variable not listed here (callback URL, internal ids) stays out of the dialog; the Workflow page still shows everything.
 */
export const SUMMARY_VARIABLES: readonly { key: string; labelKey: string }[] = [
  { key: 'reference', labelKey: 'workflow.history.fields.reference' },
  { key: 'typeCode', labelKey: 'workflow.history.fields.type' },
  { key: 'decision', labelKey: 'workflow.history.fields.decision' },
  { key: 'decidedBy', labelKey: 'workflow.history.fields.decidedBy' },
];

/** Variables holding a decision code (APPROVED, REJECTED, RETURNED): shown with the decision's label. */
export const DECISION_VARIABLES: readonly string[] = ['decision'];

/** Translation key prefix of a request type code (DRIVER, CUSTOMER, ...); a code with no key is shown as it is. */
export const SUBJECT_TYPE_KEY_PREFIX = 'onboardingSubmissions.types.';

export const WORKFLOW_HISTORY_DIALOG_WIDTH = '720px';
