import { Injector, runInInjectionContext } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { appDialogTexts } from './app-dialog-texts';
import { pendingChangesGuard } from './guards/pending-changes.guard';
import { isNavSectionHidden, isTodaySectionHidden, profileApplicationCodes } from './profile-visibility';
import {
  addressInputTexts, commonTableTexts, imageCropDialogTexts, languageSwitcherTexts, lookupTexts, phoneNumberInputTexts, tableFooterTexts,
} from './hq-shared-lib-texts';
import { SCREEN_HELP_MAX_STEPS, screenHelpTexts } from './screen-help-texts';
import { getPasswordChecks, getPasswordStrength } from './utils/password-strength';
import { matchPasswords } from './validators/password.validators';
import { environment } from '../../environments/environment';

/** Echoes the key and, when given, the params, so tests can see exactly what was asked for. */
const echo = (key: string, params?: Record<string, unknown>) => (params ? `${key}|${JSON.stringify(params)}` : key);

function inCtx<T>(fn: () => T, translations: Record<string, string> = {}): T {
  TestBed.configureTestingModule({
    providers: [{ provide: TranslateService, useValue: { instant: (k: string, p?: Record<string, unknown>) => translations[k] ?? echo(k, p) } }],
  });
  return runInInjectionContext(TestBed.inject(Injector), fn);
}

describe('password strength', () => {
  it('checks length, mixed case and number-or-symbol', () => {
    expect(getPasswordChecks('a').map((c) => c.valid)).toEqual([false, false, false]);
    expect(getPasswordChecks('Abcdefghijklm').map((c) => c.valid)).toEqual([true, true, false]);
    expect(getPasswordChecks('abcdefghijkl1')[2].valid).toBe(true);
    expect(getPasswordChecks('abcdefghijkl_')[2].valid).toBe(true);
    expect(getPasswordChecks('x')[0].label).toBe('shared.passwordForm.checks.length');
  });

  it('rates weak, medium and strong by how many checks pass', () => {
    expect(getPasswordStrength(getPasswordChecks('a'))).toBe('weak');
    expect(getPasswordStrength(getPasswordChecks('abc1'))).toBe('weak');
    expect(getPasswordStrength(getPasswordChecks('Abc1'))).toBe('medium');
    expect(getPasswordStrength(getPasswordChecks('Abcdefghijkl1'))).toBe('strong');
  });
});

describe('matchPasswords', () => {
  const group = (a: string, b: string) => new FormGroup({ pw: new FormControl(a), confirm: new FormControl(b) });
  it('is valid when the two fields are equal and flags a mismatch otherwise', () => {
    const validator = matchPasswords('pw', 'confirm');
    expect(validator(group('x', 'x'))).toBeNull();
    expect(validator(group('x', 'y'))).toEqual({ mismatch: true });
  });
});

describe('pendingChangesGuard', () => {
  const run = (c: unknown) => (pendingChangesGuard as unknown as (c: unknown) => unknown)(c);
  it('lets navigation continue when nothing is pending, without asking', () => {
    const confirm = vi.fn();
    expect(run({ hasPendingChanges: () => false, confirmDiscardChanges: confirm })).toBe(true);
    expect(confirm).not.toHaveBeenCalled();
  });
  it('defers to the confirmation when changes are pending', () => {
    const answer = of(false);
    expect(run({ hasPendingChanges: () => true, confirmDiscardChanges: () => answer })).toBe(answer);
  });
});

describe('profile visibility', () => {
  const env = environment as unknown as Record<string, unknown>;
  beforeEach(() => {
    delete env['hiddenNavSections'];
    delete env['hiddenTodaySections'];
    delete env['applicationCodes'];
  });

  it('hides nothing when the profile has no lists', () => {
    expect(isNavSectionHidden('nav.x')).toBe(false);
    expect(isTodaySectionHidden('card')).toBe(false);
    expect(profileApplicationCodes()).toEqual([]);
  });

  it('hides exactly the listed sections and returns the profile application codes', () => {
    env['hiddenNavSections'] = ['nav.hidden'];
    env['hiddenTodaySections'] = ['card1'];
    env['applicationCodes'] = ['APP1'];
    expect(isNavSectionHidden('nav.hidden')).toBe(true);
    expect(isNavSectionHidden('nav.other')).toBe(false);
    expect(isTodaySectionHidden('card1')).toBe(true);
    expect(isTodaySectionHidden('card2')).toBe(false);
    expect(profileApplicationCodes()).toEqual(['APP1']);
  });
});

describe('screenHelpTexts', () => {
  it('returns the title and null for a screen with no help text', () => {
    const h = inCtx(() => screenHelpTexts());
    expect(h.title()).toBe('screenHelp.title');
    expect(h.read('payments')).toBeNull();
  });

  it('collects what, the steps that exist and the tip', () => {
    const h = inCtx(() => screenHelpTexts(), {
      'screenHelp.payments.what': 'W', 'screenHelp.payments.step1': 'S1', 'screenHelp.payments.step3': 'S3', 'screenHelp.payments.tip': 'T',
    });
    expect(h.read('payments')).toEqual({ what: 'W', steps: ['S1', 'S3'], tip: 'T' });
    expect(SCREEN_HELP_MAX_STEPS).toBe(6);
  });

  it('reports an empty tip when none is translated', () => {
    const h = inCtx(() => screenHelpTexts(), { 'screenHelp.p.what': 'W' });
    expect(h.read('p')).toEqual({ what: 'W', steps: [], tip: '' });
  });
});

describe('text providers for hq-shared-lib', () => {
  it('app dialog texts pass the label and limits as parameters', () => {
    const t = inCtx(() => appDialogTexts());
    expect(t.required('Name')).toBe('shared.appDialog.fieldRequired|{"label":"Name"}');
    expect(t.invalidNumber('Age')).toBe('shared.appDialog.invalidNumber|{"label":"Age"}');
    expect(t.min('Age', 3)).toBe('shared.appDialog.min|{"label":"Age","min":3}');
    expect(t.max('Age', 9)).toBe('shared.appDialog.max|{"label":"Age","max":9}');
    expect(t.cancel()).toBe('shared.appDialog.defaultCancel');
    expect(t.confirm()).toBe('shared.appDialog.defaultConfirm');
    expect(t.working()).toBe('shared.appDialog.defaultLoading');
  });

  it('every shared text is a translation key under shared/header with the right parameters', () => {
    const all = inCtx(() => ({
      table: commonTableTexts(), lookup: lookupTexts(), footer: tableFooterTexts(), address: addressInputTexts(),
      phone: phoneNumberInputTexts(), lang: languageSwitcherTexts(), crop: imageCropDialogTexts(),
    }));
    const call = (o: object) => Object.entries(o).map(([name, fn]) => [name, (fn as (...a: unknown[]) => string)('LBL', 2)] as const);
    for (const group of [all.table, all.lookup, all.footer, all.address, all.phone, all.crop]) {
      for (const [, value] of call(group)) expect(value).toMatch(/^shared\./);
    }
    for (const [, value] of call(all.lang)) expect(value).toMatch(/^header\.language\./);
    expect(all.lookup.typeToSearch(3)).toBe('shared.lookup.typeToSearch|{"min":3}');
    expect(all.lookup.selected(4)).toBe('shared.lookup.selected|{"count":4}');
    expect(all.phone.required('Mobile')).toBe('shared.phoneNumberInput.required|{"label":"Mobile"}');
    expect(all.table.noRecords()).toBe('shared.commonTable.noRecords');
  });
});
