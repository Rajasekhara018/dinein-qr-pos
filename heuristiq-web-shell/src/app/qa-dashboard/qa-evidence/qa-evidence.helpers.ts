import { QA_EVIDENCE_ID_PATTERN, QA_EVIDENCE_KIND_KEYS, QA_EVIDENCE_MAX, QA_THUMBNAIL_KINDS } from './qa-evidence.constants';
import { QaEvidence } from '../../system-qa/system-qa.models';

/** True when a DMS id is a plain id (letters, digits and dashes only), so nothing odd is ever put in a request. */
export function isValidEvidenceId(id: unknown): id is string {
  return typeof id === 'string' && QA_EVIDENCE_ID_PATTERN.test(id);
}

/** The screenshots of a scenario that can be opened: a plain id and a known kind, at most three, the failure one first. */
export function usableEvidence(list: readonly QaEvidence[] | null | undefined): QaEvidence[] {
  const usable = (list ?? []).filter((e) => !!e && isValidEvidenceId(e.documentId) && e.kind in QA_EVIDENCE_KIND_KEYS);
  return [...usable.filter((e) => e.kind === 'FAILURE'), ...usable.filter((e) => e.kind !== 'FAILURE')].slice(0, QA_EVIDENCE_MAX);
}

/** True when a screenshot of this kind is shown as a preview in the row (the failure one and the final state); an unknown kind stays a link. */
export function showsAsThumbnail(kind: string): boolean {
  return QA_THUMBNAIL_KINDS.includes(kind);
}

/** The translation key of a link's text: "Screenshot" or "Failure screenshot". */
export function evidenceLabelKey(kind: string): string {
  return QA_EVIDENCE_KIND_KEYS[kind] ?? QA_EVIDENCE_KIND_KEYS['SCREEN'];
}
