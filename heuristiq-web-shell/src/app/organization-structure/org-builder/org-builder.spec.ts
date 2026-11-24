import { ChangeDetectorRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { OrganizationContextService } from '../../shared/services/organization-context-service';
import { OrgBuilder } from './org-builder';

const DRAFT = { id: 'd1' };
const BLOCKED = {
  applied: false,
  issues: [
    { severity: 'WARNING', code: 'UNIT_NO_HEAD', message: "Unit 'Associates' has no head", subjectId: null },
    { severity: 'ERROR', code: 'POSITION_NOT_IN_LADDER', message: 'Position is not in the ladder', subjectId: null },
  ],
  rootUnits: [],
};

describe('OrgBuilder publish', () => {
  function create(result: unknown) {
    const notify = { error: vi.fn(), success: vi.fn() };
    const api = { currentDraft: vi.fn(() => of(null)), changes: vi.fn(() => of([])), publish: vi.fn(() => of(result)) };
    TestBed.configureTestingModule({ providers: [{ provide: OrganizationContextService, useValue: {} }] });
    const builder = TestBed.runInInjectionContext(
      () => new OrgBuilder(
        api as never,
        { detectChanges: vi.fn() } as unknown as ChangeDetectorRef,
        notify as never,
        { confirm: vi.fn(() => of(true)) } as never,
        { instant: vi.fn((key: string, params?: Record<string, unknown>) => key + (params ? JSON.stringify(params) : '')) } as never,
      ),
    );
    builder.draft = DRAFT as never;
    return { builder, notify };
  }

  it('shows the blocking problem in a snackbar when the publish is not applied', () => {
    const { builder, notify } = create(BLOCKED);
    builder.publish();
    expect(notify.success).not.toHaveBeenCalled();
    expect(notify.error).toHaveBeenCalledTimes(1);
    const text = notify.error.mock.calls[0][0] as string;
    expect(text).toContain('publishBlocked');
    expect(text).toContain('"count":1');
    expect(text).toContain('Position is not in the ladder');
    expect(builder.draft).not.toBeNull();
  });

  it('says it was published, and shows no error, when it is applied', () => {
    const { builder, notify } = create({ applied: true, issues: [], rootUnits: [] });
    builder.publish();
    expect(notify.error).not.toHaveBeenCalled();
    expect(notify.success).toHaveBeenCalledTimes(1);
    expect(builder.draft).toBeNull();
  });
});
