import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { OrganizationChoice } from '../../shared/models/uam.models';
import { OrganizationContextService } from '../../shared/services/organization-context-service';
import { OrgSelector } from './org-selector';

const org = (tenantId: string): OrganizationChoice => ({ tenantId, code: tenantId.toUpperCase(), name: tenantId, status: 'ACTIVE' }) as OrganizationChoice;
const A = org('a');
const B = org('b');

describe('OrgSelector', () => {
  function create(canSwitch: boolean, load = () => of([A, B])) {
    const context = { canSwitch, load: vi.fn(load), switchTo: vi.fn() };
    TestBed.configureTestingModule({ providers: [{ provide: OrganizationContextService, useValue: context }] });
    const selector = TestBed.runInInjectionContext(() => new OrgSelector());
    return { selector, context };
  }

  it('loads the organizations on init', () => {
    const { selector, context } = create(true);
    selector.ngOnInit();
    expect(context.load).toHaveBeenCalledTimes(1);
  });

  it('swallows a failed load so the screen still opens', () => {
    const { selector, context } = create(true, () => throwError(() => new Error('down')));
    expect(() => selector.ngOnInit()).not.toThrow();
    expect(context.load).toHaveBeenCalled();
  });

  it('switches the session to the chosen organization', () => {
    const { selector, context } = create(true);
    selector.choose([A, B], 'b', A);
    expect(context.switchTo).toHaveBeenCalledWith(B);
  });

  it('does nothing when the user may not switch organizations', () => {
    const { selector, context } = create(false);
    selector.choose([A, B], 'b', A);
    expect(context.switchTo).not.toHaveBeenCalled();
  });

  it('does nothing when the chosen organization is already current', () => {
    const { selector, context } = create(true);
    selector.choose([A, B], 'a', A);
    expect(context.switchTo).not.toHaveBeenCalled();
  });

  it('ignores an id that is not in the list', () => {
    const { selector, context } = create(true);
    selector.choose([A, B], 'zzz', A);
    expect(context.switchTo).not.toHaveBeenCalled();
  });

  it('switches when there is no current organization yet', () => {
    const { selector, context } = create(true);
    selector.choose([A, B], 'a', null);
    expect(context.switchTo).toHaveBeenCalledWith(A);
  });

  it('tracks organizations by tenant id', () => {
    const { selector } = create(true);
    expect(selector.trackOrganization(0, B)).toBe('b');
  });
});
