import { HqLookupCondition } from 'hq-shared-lib';
import { SearchCondition } from '../../shared/services/search-api-service';
import { MEMBER_PICKER_FIELDS, MEMBER_PICKER_SLOTS, MemberPickerField } from './organization-members.constants';

/** The two lists the search index takes: every condition of `all` must hold, and at least one of `any` (when it has any). */
export interface MemberSearchGroups {
  all: SearchCondition[];
  any: SearchCondition[];
}

/** Thrown when the conditions need more than one OR group, which the search index cannot express. */
export class MemberSearchTooComplexError extends Error {}

const isPickerField = (key: string | undefined): key is MemberPickerField =>
  !!key && (MEMBER_PICKER_FIELDS as readonly string[]).includes(key);

/**
 * The quick search of the "Add people" picker as index conditions: the chosen column contains the text, or with
 * "All fields" (no column) any of the picker's columns contains it.
 */
export function memberQuickSearch(query: string, field?: string): MemberSearchGroups {
  const text = query.trim();
  if (!text) return { all: [], any: [] };
  if (isPickerField(field)) return { all: [{ field: MEMBER_PICKER_SLOTS[field], operator: 'LIKE', value: text }], any: [] };
  return { all: [], any: MEMBER_PICKER_FIELDS.map((key) => ({ field: MEMBER_PICKER_SLOTS[key], operator: 'LIKE' as const, value: text })) };
}

/**
 * The advanced conditions of the picker as index conditions, read left to right: a row joined by OR forms one OR set
 * with the row before it ("A OR B AND C" is "(A OR B) AND C"). The index takes one AND list and one OR set, so at most
 * one run of OR-joined rows is supported; more throws {@link MemberSearchTooComplexError}. Rows on unknown columns are skipped.
 */
export function memberAdvancedSearch(conditions: readonly HqLookupCondition[]): MemberSearchGroups {
  const rows = conditions.filter((row) => isPickerField(row.fieldName) && String(row.value ?? '').trim() !== '');
  const runs: SearchCondition[][] = [];
  rows.forEach((row, index) => {
    const condition: SearchCondition = {
      field: MEMBER_PICKER_SLOTS[row.fieldName as MemberPickerField],
      operator: row.operator,
      value: String(row.value).trim(),
    };
    if (index > 0 && row.condition === 'OR') runs[runs.length - 1].push(condition);
    else runs.push([condition]);
  });
  const orRuns = runs.filter((run) => run.length > 1);
  if (orRuns.length > 1) throw new MemberSearchTooComplexError();
  return { all: runs.filter((run) => run.length === 1).map((run) => run[0]), any: orRuns[0] ?? [] };
}
