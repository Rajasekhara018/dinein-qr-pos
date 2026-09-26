import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  convertToParamMap,
  provideRouter,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CartStore } from './cart.store';
import { guestSessionGuard } from './guest-session.guard';
import { GuestSessionStore, SessionOutcome } from './guest-session.store';

describe('guestSessionGuard', () => {
  const store = {
    status: signal<string>('idle'),
    table: signal<{ id: number; label: string } | null>(null),
    start: vi.fn<(t?: string | null) => Promise<SessionOutcome>>(),
  };
  const cart = { bindTable: vi.fn() };

  beforeEach(() => {
    store.status.set('idle');
    store.table.set(null);
    store.start.mockReset();
    cart.bindTable.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: GuestSessionStore, useValue: store },
        { provide: CartStore, useValue: cart },
      ],
    });
  });

  const routeWith = (params: Record<string, string>) =>
    ({ queryParamMap: convertToParamMap(params) }) as ActivatedRouteSnapshot;
  const run = (params: Record<string, string>, url: string) =>
    TestBed.runInInjectionContext(() => guestSessionGuard(routeWith(params), { url } as RouterStateSnapshot));

  it('validates the QR token, binds the table cart and strips `t` from the URL', async () => {
    store.start.mockImplementation(async () => {
      store.table.set({ id: 3, label: 'T3' });
      return 'ready';
    });
    const result = await run({ t: 'abc' }, '/menu?t=abc&x=1');
    expect(store.start).toHaveBeenCalledWith('abc');
    expect(cart.bindTable).toHaveBeenCalledWith(3);
    expect(result instanceof UrlTree && result.toString()).toBe('/menu?x=1');
  });

  it('resumes from the cookie without a token', async () => {
    store.start.mockImplementation(async () => {
      store.table.set({ id: 3, label: 'T3' });
      return 'ready';
    });
    expect(await run({}, '/menu/orders/5')).toBe(true);
    expect(store.start).toHaveBeenCalledWith(null);
  });

  it('sends invalid tables / missing sessions to the scan page', async () => {
    store.start.mockResolvedValue('invalid');
    const result = await run({ t: 'bad' }, '/menu?t=bad');
    expect(result instanceof UrlTree && result.toString()).toBe('/menu/scan');
  });

  it('lets the shell show a retryable error (keeping the token) on network errors', async () => {
    store.start.mockResolvedValue('error');
    expect(await run({ t: 'abc' }, '/menu?t=abc')).toBe(true);
  });

  it('skips the request when a session is already loaded', async () => {
    store.status.set('ready');
    expect(await run({}, '/menu/cart')).toBe(true);
    expect(store.start).not.toHaveBeenCalled();
  });
});
