import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { of } from 'rxjs';
import { HqPendingChangesAware, hqPendingChangesGuard } from './hq-pending-changes';

describe('hqPendingChangesGuard', () => {
  const route = {} as ActivatedRouteSnapshot;
  const state = {} as RouterStateSnapshot;

  function component(pending: boolean, answer: ReturnType<HqPendingChangesAware['confirmDiscardChanges']>) {
    return { hasPendingChanges: vi.fn(() => pending), confirmDiscardChanges: vi.fn(() => answer) };
  }

  it('lets navigation through without asking when nothing is pending', () => {
    const c = component(false, false);
    expect(hqPendingChangesGuard(c, route, state, state)).toBe(true);
    expect(c.confirmDiscardChanges).not.toHaveBeenCalled();
  });

  it('returns the component answer when there are pending changes', () => {
    expect(hqPendingChangesGuard(component(true, false), route, state, state)).toBe(false);
    expect(hqPendingChangesGuard(component(true, true), route, state, state)).toBe(true);
  });

  it('passes a promise or observable answer through unchanged', () => {
    const promise = Promise.resolve(true);
    const observable = of(false);
    expect(hqPendingChangesGuard(component(true, promise), route, state, state)).toBe(promise);
    expect(hqPendingChangesGuard(component(true, observable), route, state, state)).toBe(observable);
  });
});
