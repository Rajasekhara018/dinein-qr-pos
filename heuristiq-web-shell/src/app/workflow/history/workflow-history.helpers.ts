import { DECISION_KEY_PREFIX } from '../workflow.constants';
import { DECISION_VARIABLES, SUBJECT_TYPE_KEY_PREFIX, SUMMARY_VARIABLES, USER_VARIABLES } from './workflow-history.constants';

/** One line of the dialog's summary: the translation key of its label and the value to show (already readable). */
export interface SummaryRow {
  labelKey: string;
  value: string;
}

/** The user ids held by a workflow's variables (who decided, who started it), to read their names. */
export function userIdsOf(vars: Record<string, unknown>): string[] {
  return USER_VARIABLES.map((key) => vars[key]).filter((v): v is string => typeof v === 'string' && v.length > 0);
}

/**
 * The readable summary of a request: its reference, type, decision and who decided, by name. `label` translates a key and
 * returns the key itself when it has no translation; a type or decision with no translation is shown as its code.
 */
export function summaryOf(vars: Record<string, unknown>, names: Record<string, string>, label: (key: string) => string): SummaryRow[] {
  const translated = (key: string, fallback: string) => {
    const text = label(key);
    return text === key ? fallback : text;
  };
  return SUMMARY_VARIABLES.flatMap(({ key, labelKey }) => {
    const raw = vars[key];
    if (raw === null || raw === undefined || raw === '') return [];
    const text = String(raw);
    let value = text;
    if (USER_VARIABLES.includes(key)) value = names[text] ?? text;
    else if (DECISION_VARIABLES.includes(key)) value = translated(DECISION_KEY_PREFIX + text, text);
    else if (key === 'typeCode') value = translated(SUBJECT_TYPE_KEY_PREFIX + text, text);
    return [{ labelKey, value }];
  });
}

/** A variable's value as the Workflow page shows it: a person by name with the id after it, an object as JSON. */
export function variableText(key: string, value: unknown, names: Record<string, string>): string {
  if (value !== null && typeof value === 'object') return JSON.stringify(value);
  const text = String(value);
  return USER_VARIABLES.includes(key) && names[text] && names[text] !== text ? `${names[text]} (${text})` : text;
}
