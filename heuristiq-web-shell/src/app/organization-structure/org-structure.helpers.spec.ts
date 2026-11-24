import { describe, expect, it } from 'vitest';
import {
  CHANGE_FORMS, ID_GENERATING_CHANGES, IMPORT_ERROR_CODES, IMPORT_SAMPLE_ROWS, IMPORT_TEMPLATE_HEADER, PAYLOAD_LABELS_KEY,
} from './org-structure.constants';
import {
  buildImportCsv, classifyUploadProblem, deriveStepState, rolesToOffer, rulesOfUnit, draftPickerItems, firstIncompleteStep, importErrorParams, hasErrors, matchesQuery, mergePickerItems, nextImportMode, parseImportError, summarizeChange,
  unwrapOrgResponse, wrapOrgRequest,
} from './org-structure.helpers';
import { ChangeType, OrgChange, OrgRoleRule } from './org-structure.models';

const change = (type: ChangeType, payload: OrgChange['payload']): OrgChange => ({ id: 'c1', changeSetId: 'd1', seq: 1, type, payload });

describe('org-structure helpers', () => {
  it('wraps a request in the UAM envelope', () => {
    expect(wrapOrgRequest({ note: 'x' })).toMatchObject({ requestObject: { note: 'x' }, doesInputHaveEncryptedData: false });
  });

  it('unwraps a successful response and throws the screen error shape on failure', () => {
    expect(unwrapOrgResponse({ success: true, responseObject: [1] }, 'fallback')).toEqual([1]);
    expect(() => unwrapOrgResponse({ success: false, message: 'boom' }, 'fallback')).toThrow();
    try {
      unwrapOrgResponse({ success: false }, 'fallback');
    } catch (e) {
      expect((e as { error: { detail: string } }).error.detail).toBe('fallback');
    }
  });

  it('summarises a change with display names instead of ids and hides bare generated ids', () => {
    const text = summarizeChange(change('ASSIGN', {
      id: 'generated-id', employeeId: 'e-1', unitId: 'u-1', positionId: 'p-1',
      [PAYLOAD_LABELS_KEY]: { employeeId: 'Asha (E1)', unitId: 'Testing (TEST)' },
    }));
    expect(text).toContain('employeeId: Asha (E1)');
    expect(text).toContain('unitId: Testing (TEST)');
    expect(text).toContain('positionId: p-1');
    expect(text).not.toContain('generated-id');
    expect(text).not.toContain(PAYLOAD_LABELS_KEY);
  });

  it('offers records created earlier in the draft, with their ladder', () => {
    const items = draftPickerItems([
      change('UNIT_CREATE', { id: 'u-1', code: 'TECH', name: 'Technical', ladderId: 'l-1' }),
      change('LADDER_CREATE', { id: 'l-1', name: 'Tech' }),
    ], 'UNIT_CREATE', 'ladderId');
    expect(items).toEqual([{ id: 'u-1', label: 'Technical', ladderId: 'l-1' }]);
  });

  it('lists a record once when it is both live and in the draft', () => {
    const merged = mergePickerItems([{ id: 'a', label: 'draft' }], [{ id: 'a', label: 'live' }, { id: 'b', label: 'live b' }]);
    expect(merged.map((i) => i.id)).toEqual(['a', 'b']);
    expect(merged[0].label).toBe('draft');
  });

  it('detects blocking issues and matches queries case-insensitively', () => {
    expect(hasErrors([{ severity: 'WARNING', code: 'X', message: '', subjectId: null }])).toBe(false);
    expect(hasErrors([{ severity: 'ERROR', code: 'X', message: '', subjectId: null }])).toBe(true);
    expect(matchesQuery('Testing Infra', 'infra')).toBe(true);
    expect(matchesQuery('Testing Infra', '')).toBe(true);
    expect(matchesQuery('Testing Infra', 'hr')).toBe(false);
  });
});

describe('org-structure change forms', () => {
  /** What Rider_UAM's ChangeType enum requires per type (generated ids excluded): a drift here breaks every add. */
  const BACKEND_REQUIRED: Record<ChangeType, string[]> = {
    UNIT_CREATE: ['code', 'name'],
    UNIT_RENAME: ['id', 'name'],
    UNIT_MOVE: ['id'],
    UNIT_ARCHIVE: ['id'],
    UNIT_SET_LADDER: ['unitId', 'ladderId'],
    UNIT_SET_HEAD: ['unitId', 'employeeId'],
    UNIT_CLEAR_HEAD: ['unitId'],
    LADDER_CREATE: ['name'],
    LADDER_COPY: ['sourceLadderId', 'name'],
    POSITION_UPSERT: ['ladderId', 'name', 'rank'],
    POSITION_REMOVE: ['id'],
    ASSIGN: ['employeeId', 'unitId', 'positionId'],
    UNASSIGN: ['employeeId', 'unitId'],
    SET_REPORTS_TO: ['employeeId'],
  };

  it('has exactly one form per backend change type', () => {
    expect(CHANGE_FORMS.map((f) => f.type).sort()).toEqual(Object.keys(BACKEND_REQUIRED).sort());
  });

  it('requires every field the backend requires', () => {
    for (const form of CHANGE_FORMS) {
      const required = form.fields.filter((f) => f.required).map((f) => f.key);
      for (const key of BACKEND_REQUIRED[form.type]) {
        expect(required, `${form.type} must require ${key}`).toContain(key);
      }
    }
  });

  it('does not ask the user for an id the server generates', () => {
    for (const form of CHANGE_FORMS) {
      if (ID_GENERATING_CHANGES.has(form.type)) {
        expect(form.fields.map((f) => f.key), form.type).not.toContain('id');
      }
    }
  });

  it('gives every position field a way to find its ladder', () => {
    for (const form of CHANGE_FORMS) {
      for (const field of form.fields.filter((f) => f.kind === 'position')) {
        expect(field.unitKey ?? field.ladderKey, `${form.type}.${field.key}`).toBeTruthy();
      }
    }
  });
});

describe('org-structure import helpers', () => {
  const CRLF = '\r\n';

  it('gives a row error its detail, and splits the two values of a rejected parent into code and row', () => {
    expect(importErrorParams('PARENT_UNKNOWN', 'HQ')).toEqual({ detail: 'HQ' });
    expect(importErrorParams('TOO_DEEP', '12')).toEqual({ detail: '12' });
    expect(importErrorParams('PARENT_REJECTED', 'EXEC:1')).toEqual({ detail: 'EXEC:1', code: 'EXEC', row: '1' });
    expect(importErrorParams('PARENT_REJECTED', 'EXEC')).toEqual({ detail: 'EXEC', code: 'EXEC', row: '' });
    expect(importErrorParams('HEAD_DUPLICATE', 'TECH:4')).toEqual({ detail: 'TECH:4', code: 'TECH', row: '4' });
    expect(importErrorParams('DUPLICATE_ROW', '2')).toEqual({ detail: '2' });
  });

  it('splits a row error into its stable code and optional detail', () => {
    expect(parseImportError('DEPARTMENT_UNKNOWN:HR')).toEqual({ code: 'DEPARTMENT_UNKNOWN', detail: 'HR' });
    expect(parseImportError('EMAIL_INVALID')).toEqual({ code: 'EMAIL_INVALID', detail: '' });
    expect(parseImportError('POSITION_NOT_IN_LADDER:Senior: Lead')).toEqual({ code: 'POSITION_NOT_IN_LADDER', detail: 'Senior: Lead' });
  });

  it('builds a template that is just the header, and quotes a value with a comma or a quote', () => {
    expect(buildImportCsv(IMPORT_TEMPLATE_HEADER, [])).toBe(IMPORT_TEMPLATE_HEADER.join(',') + CRLF);
    expect(buildImportCsv(['a', 'b'], [['x,y', 'say "hi"']])).toBe(`a,b${CRLF}"x,y","say ""hi"""${CRLF}`);
  });

  it('knows every error code the server can report', () => {
    expect(IMPORT_ERROR_CODES).toContain('MANAGER_CYCLE');
    expect(new Set(IMPORT_ERROR_CODES).size).toBe(IMPORT_ERROR_CODES.length);
  });

  describe('the import sample', () => {
    it('has a row per person, every row as wide as the header, and managers that exist in the file', () => {
      expect(IMPORT_SAMPLE_ROWS.length).toBe(12);
      IMPORT_SAMPLE_ROWS.forEach((row) => expect(row.length).toBe(IMPORT_TEMPLATE_HEADER.length));
      const codes = new Set(IMPORT_SAMPLE_ROWS.map((r) => r[0]));
      expect(codes.size).toBe(12);
      IMPORT_SAMPLE_ROWS.filter((r) => r[6]).forEach((r) => expect(codes.has(r[6]), r[0]).toBe(true));
    });

    it('builds a CSV whose first line is the header and whose lines match the rows', () => {
      const lines = buildImportCsv(IMPORT_TEMPLATE_HEADER, IMPORT_SAMPLE_ROWS).trim().split(/\r?\n/);

      expect(lines[0]).toBe('employee_code,full_name,department_code,position_name,email,phone,manager_employee_code,joined_on,is_head');
      expect(lines.length).toBe(13);
    });

    it('quotes a value that holds a comma', () => {
      expect(buildImportCsv(['a', 'b'], [['x, y', 'z']])).toBe('a,b\r\n"x, y",z\r\n');
    });
  });
});

describe('import tabs and refused files', () => {
  it('sorts the refusals of the file chooser into the kinds that have a translated message', () => {
    expect(classifyUploadProblem('File exceeds the 9.9 MB limit.')).toBe('tooLarge');
    expect(classifyUploadProblem('Please upload CSV, up to 10 MB files.')).toBe('notCsv');
    expect(classifyUploadProblem('File is too small (minimum 50 bytes).')).toBe('tooSmall');
    expect(classifyUploadProblem('Upload failed.')).toBe('failed');
    expect(classifyUploadProblem('')).toBe('failed');
    expect(classifyUploadProblem('FILE EXCEEDS THE LIMIT')).toBe('tooLarge');
  });

  it('moves along the tabs with the arrow keys, wrapping round, and to either end with Home and End', () => {
    const both = ['EMPLOYEES', 'DEPARTMENTS'] as const;
    expect(nextImportMode(both, 'EMPLOYEES', 'ArrowRight')).toBe('DEPARTMENTS');
    expect(nextImportMode(both, 'DEPARTMENTS', 'ArrowRight')).toBe('EMPLOYEES');
    expect(nextImportMode(both, 'EMPLOYEES', 'ArrowLeft')).toBe('DEPARTMENTS');
    expect(nextImportMode(both, 'DEPARTMENTS', 'ArrowDown')).toBe('EMPLOYEES');
    expect(nextImportMode(both, 'EMPLOYEES', 'ArrowUp')).toBe('DEPARTMENTS');
    expect(nextImportMode(both, 'DEPARTMENTS', 'Home')).toBe('EMPLOYEES');
    expect(nextImportMode(both, 'EMPLOYEES', 'End')).toBe('DEPARTMENTS');
  });

  it('stays where it is for any other key, for a lone tab and for a tab that is not on offer', () => {
    expect(nextImportMode(['EMPLOYEES', 'DEPARTMENTS'], 'EMPLOYEES', 'Enter')).toBe('EMPLOYEES');
    expect(nextImportMode(['EMPLOYEES'], 'EMPLOYEES', 'ArrowRight')).toBe('EMPLOYEES');
    expect(nextImportMode(['EMPLOYEES'], 'DEPARTMENTS', 'ArrowRight')).toBe('EMPLOYEES');
  });
});

describe('role rules of a department', () => {
  const rule = (id: string, over: Partial<OrgRoleRule> = {}): OrgRoleRule =>
    ({ id, unitId: 'u1', unitName: 'Tech', positionId: null, positionName: null, roleCode: 'HR_VIEW', active: true, ...over });

  it('keeps only the rules that target exactly this department', () => {
    const rules = [rule('a'), rule('b', { unitId: 'u2' }), rule('c', { unitId: null, positionId: 'p1' }), rule('d', { positionId: 'p1', positionName: 'Lead' })];
    expect(rulesOfUnit(rules, 'u1').map((r) => r.id)).toEqual(['a', 'd']);
    expect(rulesOfUnit(rules, 'none')).toEqual([]);
  });

  it('offers the roles not yet given to the whole department; a rule for one position does not use a role up', () => {
    const roles = [{ code: 'HR_VIEW' }, { code: 'SALES' }, { code: 'LEAD_ONLY' }];
    const unitRules = [rule('a'), rule('b', { roleCode: 'LEAD_ONLY', positionId: 'p1', positionName: 'Lead' })];
    expect(rolesToOffer(roles, unitRules).map((r) => r.code)).toEqual(['SALES', 'LEAD_ONLY']);
    expect(rolesToOffer(roles, [])).toHaveLength(3);
    expect(rolesToOffer([], unitRules)).toEqual([]);
  });
});

describe('import steps', () => {
  const b = (status: string, createdAt = '2026-01-01T00:00:00Z') => ({ status, createdAt }) as never;

  it('derives the state of a step from its recent imports and its data', () => {
    expect(deriveStepState(null, null)).toBe('NOT_STARTED');
    expect(deriveStepState([], false)).toBe('NOT_STARTED');
    expect(deriveStepState([b('CANCELLED'), b('FAILED')], false)).toBe('NOT_STARTED');
    expect(deriveStepState([b('VALIDATED')], false)).toBe('PREVIEW');
    expect(deriveStepState([b('VALIDATED')], null)).toBe('PREVIEW');
    expect(deriveStepState([b('APPLYING')], true)).toBe('IN_PROGRESS');
    expect(deriveStepState([b('APPLYING'), b('APPLIED', '2025-01-01T00:00:00Z')], null)).toBe('IN_PROGRESS');
    expect(deriveStepState([], true)).toBe('DONE');
    expect(deriveStepState(null, true)).toBe('DONE');
    expect(deriveStepState([b('APPLIED')], null)).toBe('DONE');
  });

  it('lets data, or a newer applied file, win over an old preview that was never confirmed', () => {
    expect(deriveStepState([b('VALIDATED')], true)).toBe('DONE');
    expect(deriveStepState([b('VALIDATED', '2025-01-01T00:00:00Z'), b('APPLIED', '2026-01-01T00:00:00Z')], false)).toBe('DONE');
    expect(deriveStepState([b('APPLIED', '2026-01-01T00:00:00Z'), b('VALIDATED', '2025-06-01T00:00:00Z')], null)).toBe('DONE');
    // a preview newer than the last applied file, and no data: waiting for a decision
    expect(deriveStepState([b('APPLIED', '2025-01-01T00:00:00Z'), b('VALIDATED', '2026-01-01T00:00:00Z')], false)).toBe('PREVIEW');
  });

  it('never lets a failed or cancelled file change the state', () => {
    expect(deriveStepState([b('FAILED'), b('CANCELLED')], true)).toBe('DONE');
    expect(deriveStepState([b('CANCELLED', '2026-02-01T00:00:00Z'), b('APPLIED', '2026-01-01T00:00:00Z')], false)).toBe('DONE');
    expect(deriveStepState([b('FAILED', '2026-02-01T00:00:00Z'), b('VALIDATED', '2026-01-01T00:00:00Z')], false)).toBe('PREVIEW');
    expect(deriveStepState([b('FAILED'), b('CANCELLED')], null)).toBe('NOT_STARTED');
    expect(deriveStepState([{ status: 'VALIDATED' } as never], false)).toBe('PREVIEW');
  });

  it('opens on the first step that is not done, or the last when all are', () => {
    const states = (done: string[]) => (mode: string) => (done.includes(mode) ? 'DONE' : 'NOT_STARTED') as 'DONE' | 'NOT_STARTED';
    expect(firstIncompleteStep(['LADDERS', 'DEPARTMENTS', 'EMPLOYEES'], states([]))).toBe('LADDERS');
    expect(firstIncompleteStep(['LADDERS', 'DEPARTMENTS', 'EMPLOYEES'], states(['LADDERS']))).toBe('DEPARTMENTS');
    expect(firstIncompleteStep(['DEPARTMENTS', 'EMPLOYEES'], states(['LADDERS']))).toBe('DEPARTMENTS');
    expect(firstIncompleteStep(['LADDERS', 'DEPARTMENTS', 'EMPLOYEES'], states(['LADDERS', 'DEPARTMENTS', 'EMPLOYEES']))).toBe('EMPLOYEES');
    expect(firstIncompleteStep(['EMPLOYEES'], states([]))).toBe('EMPLOYEES');
  });
});
