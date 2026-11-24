import { IMPORT_APPLIED_STATUS, IMPORT_APPLYING_STATUS, IMPORT_PREVIEW_STATUS, IMPORT_TWO_VALUED_ERROR_CODES, PAYLOAD_LABELS_KEY, UPLOAD_PROBLEM_KEYS } from './org-structure.constants';
import { ChangeLabels, ChangePayload, OrgChange, OrgImportBatch, OrgImportMode, OrgImportStepState, OrgIssue, OrgRoleRule, PickerItem } from './org-structure.models';

/** UAM's standard request envelope. */
export function wrapOrgRequest<T>(payload: T): Record<string, unknown> {
  return { requestObject: payload, doesInputHaveEncryptedData: false, success: false, pageNo: 0, pageSize: 0, useOrOperator: false };
}

export interface OrgEnvelope<T> {
  success: boolean;
  message?: string;
  responseObject?: T;
}

/** Returns the payload of a successful envelope; throws the `{ error: { detail } }` shape the screens already read. */
export function unwrapOrgResponse<T>(response: OrgEnvelope<T>, fallbackMessage: string): T {
  if (!response.success) {
    const detail = response.message || fallbackMessage;
    throw { error: { detail, message: detail } };
  }
  return response.responseObject as T;
}

/** The display names kept inside a payload, empty when there are none. */
export function payloadLabels(payload: ChangePayload): ChangeLabels {
  const labels = payload[PAYLOAD_LABELS_KEY];
  return labels && typeof labels === 'object' ? (labels as ChangeLabels) : {};
}

/** A short "name=value" list for a change row; ids are replaced by the display names stored beside them. */
export function summarizeChange(change: OrgChange): string {
  const labels = payloadLabels(change.payload);
  return Object.entries(change.payload)
    .filter(([key]) => key !== PAYLOAD_LABELS_KEY)
    // A bare generated id says nothing to a reader; show it only when a display name was stored for it.
    .filter(([key]) => key !== 'id' || labels['id'] !== undefined)
    .filter(([, value]) => value !== null && value !== undefined && value !== '')
    .map(([key, value]) => `${key}: ${labels[key] ?? String(value)}`)
    .join(', ');
}

/** Picker options for records created earlier in the same draft, which the server cannot list yet. */
export function draftPickerItems(changes: OrgChange[], type: OrgChange['type'], extraLadderKey?: string): PickerItem[] {
  return changes
    .filter((change) => change.type === type)
    .map((change) => {
      const label = String(change.payload['name'] ?? change.payload['code'] ?? change.payload['id']);
      const ladder = extraLadderKey ? change.payload[extraLadderKey] : null;
      return { id: String(change.payload['id']), label, ladderId: ladder ? String(ladder) : null };
    });
}

/** Keeps the first item per id, so a draft-created record that is also live is listed once. */
export function mergePickerItems(...lists: PickerItem[][]): PickerItem[] {
  const seen = new Set<string>();
  const merged: PickerItem[] = [];
  for (const item of lists.flat()) {
    if (!seen.has(item.id)) {
      seen.add(item.id);
      merged.push(item);
    }
  }
  return merged;
}

export function hasErrors(issues: OrgIssue[]): boolean {
  return issues.some((issue) => issue.severity === 'ERROR');
}

/** Case-insensitive prefix/substring match used to filter locally held picker lists. */
export function matchesQuery(label: string, query: string): boolean {
  const q = query.trim().toLowerCase();
  return q === '' || label.toLowerCase().includes(q);
}

/** Splits a server row error such as "DEPARTMENT_UNKNOWN:HR" into its stable code and optional detail. */
export function parseImportError(raw: string): { code: string; detail: string } {
  const at = raw.indexOf(':');
  return at < 0 ? { code: raw, detail: '' } : { code: raw.slice(0, at), detail: raw.slice(at + 1) };
}

/**
 * The values a translated row error can use: {{detail}} is everything after the code, and a two-valued detail (PARENT_REJECTED:<parentCode>:<rowNo>,
 * HEAD_DUPLICATE:<department>:<rowNo>) is also split into {{code}} and {{row}}.
 */
export function importErrorParams(code: string, detail: string): Record<string, string> {
  if (!IMPORT_TWO_VALUED_ERROR_CODES.has(code)) return { detail };
  const at = detail.lastIndexOf(':');
  return at < 0 ? { detail, code: detail, row: '' } : { detail, code: detail.slice(0, at), row: detail.slice(at + 1) };
}

/**
 * What the file chooser said about a refused file, as one of a few kinds (the chooser's own texts are English and fixed): a file over the size
 * limit, a file that is not a CSV, a file too small to be one, or anything else (the document store did not take it, for example because it is down).
 */
export function classifyUploadProblem(message: string): keyof typeof UPLOAD_PROBLEM_KEYS {
  const text = message.toLowerCase();
  if (text.includes('exceeds')) return 'tooLarge';
  if (text.includes('please upload')) return 'notCsv';
  if (text.includes('too small')) return 'tooSmall';
  return 'failed';
}

/** The mode to open after an arrow, Home or End key on a tab; the same mode for any other key or when it is the only one. */
export function nextImportMode(modes: readonly OrgImportMode[], current: OrgImportMode, key: string): OrgImportMode {
  const at = Math.max(0, modes.indexOf(current));
  switch (key) {
    case 'ArrowRight':
    case 'ArrowDown': return modes[(at + 1) % modes.length];
    case 'ArrowLeft':
    case 'ArrowUp': return modes[(at - 1 + modes.length) % modes.length];
    case 'Home': return modes[0];
    case 'End': return modes[modes.length - 1];
    default: return current;
  }
}

/**
 * How far a setup step is, by this precedence: (1) a file of its kind is being applied: in progress; (2) its data is there, or the most recent
 * file of its kind that was not failed or cancelled was applied: done (a stale preview never undoes this); (3) a validated preview waits to be
 * confirmed or cancelled: preview not confirmed; (4) not started. FAILED and CANCELLED files never change the state. `recent` (newest first by
 * date) and `hasData` are null when this person may not read them.
 */
export function deriveStepState(recent: readonly OrgImportBatch[] | null, hasData: boolean | null): OrgImportStepState {
  const batches = [...(recent ?? [])].sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''));
  if (batches.some((batch) => batch.status === IMPORT_APPLYING_STATUS)) return 'IN_PROGRESS';
  const latest = batches.find((batch) => batch.status === IMPORT_APPLIED_STATUS || batch.status === IMPORT_PREVIEW_STATUS);
  if (hasData || latest?.status === IMPORT_APPLIED_STATUS) return 'DONE';
  if (batches.some((batch) => batch.status === IMPORT_PREVIEW_STATUS)) return 'PREVIEW';
  return 'NOT_STARTED';
}

/** The first of the visible steps that is not done (the last one when all are done): where the screen opens. */
export function firstIncompleteStep(visible: readonly OrgImportMode[], stateOf: (mode: OrgImportMode) => OrgImportStepState): OrgImportMode {
  return visible.find((mode) => stateOf(mode) !== 'DONE') ?? visible[visible.length - 1];
}

/** A CSV (comma separated, CRLF lines) of a header and any number of rows; a value with a comma, quote or line break is quoted. */
export function buildImportCsv(header: readonly string[], rows: readonly (readonly string[])[]): string {
  const needsQuotes = (value: string) => value.includes(',') || value.includes('"') || value.includes('\n');
  const cell = (value: string) => (needsQuotes(value) ? `"${value.replace(/"/g, '""')}"` : value);
  const lineBreak = '\r\n';
  return [header, ...rows].map((row) => row.map(cell).join(',')).join(lineBreak) + lineBreak;
}

/**
 * The job a person keeps when moved to a department whose ladder is {@code positions}: the same job (by id), or the one with the
 * same name (ignoring case and spacing). Null when that ladder has no such job, so the person must pick one instead of the
 * publish failing later with "holds a job that is not in the ladder".
 */
export function matchPositionForMove(
  positions: readonly { id: string; name: string }[],
  currentPositionId: string | null | undefined,
  currentPositionName: string | null | undefined,
): { id: string; name: string } | null {
  const byId = currentPositionId ? positions.find((p) => p.id === currentPositionId) : undefined;
  if (byId) return byId;
  const wanted = (currentPositionName ?? '').trim().toLowerCase();
  return wanted ? positions.find((p) => p.name.trim().toLowerCase() === wanted) ?? null : null;
}

/** The role rules that target exactly this department (rules of other departments, and rules of no department, are left out). */
export function rulesOfUnit(rules: readonly OrgRoleRule[], unitId: string): OrgRoleRule[] {
  return rules.filter((rule) => rule.unitId === unitId);
}

/**
 * The roles that can still be added for a department: those that are active and not already given to everyone in it. A rule that also names a
 * position does not use up the role, since it only reaches that position.
 */
export function rolesToOffer<T extends { code: string }>(roles: readonly T[], unitRules: readonly OrgRoleRule[]): T[] {
  const taken = new Set(unitRules.filter((rule) => !rule.positionId).map((rule) => rule.roleCode));
  return roles.filter((role) => !taken.has(role.code));
}
