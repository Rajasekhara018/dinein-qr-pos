import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, UrlTree } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HQ_PERMISSION_PRIVILEGES } from 'hq-shared-lib';
import { Observable, of } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../shared/services/auth-service';
import { NotificationService } from '../shared/services/notification-service';
import { inventoryScreenGuard } from './inventory-screen-guard';
import { INVENTORY_PERMISSION, INVENTORY_SCREEN_KEYS_BY_ROUTE, INVENTORY_SCREEN_PERMISSIONS } from './inventory.constants';

describe('inventoryScreenGuard', () => {
  const auth = { ensurePrivileges: vi.fn(), canAccess: vi.fn() };
  const router = { createUrlTree: vi.fn((commands: string[]) => ({ commands }) as unknown as UrlTree) };
  const notify = { error: vi.fn() };

  beforeEach(() => {
    TestBed.resetTestingModule();
    vi.clearAllMocks();
    auth.ensurePrivileges.mockReturnValue(of(undefined));
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
        { provide: NotificationService, useValue: notify },
      ],
    });
  });

  /** The activated chain from the root down to the route under test, as [routeConfig path, consumed URL segments]. */
  const snapshot = (...chain: Array<[string, string[]]>): ActivatedRouteSnapshot => {
    const routes = chain.map(([path, url]) => ({ routeConfig: { path }, url: url.map((p) => ({ path: p })) }));
    const leaf = routes[routes.length - 1] as Record<string, unknown>;
    leaf['pathFromRoot'] = routes;
    return leaf as unknown as ActivatedRouteSnapshot;
  };
  const under = (...chain: Array<[string, string[]]>) => snapshot(['', []], ['inventory', ['inventory']], ...chain);
  const run = (route: ActivatedRouteSnapshot) => TestBed.runInInjectionContext(() => inventoryScreenGuard(route, {} as never));

  it('checks the permission key of the inventory screen being opened', () => {
    auth.canAccess.mockReturnValue(true);
    let result: unknown;

    (run(under(['items', ['items']])) as Observable<unknown>).subscribe((r) => (result = r));

    expect(auth.canAccess).toHaveBeenCalledWith('view_inventory_items');
    expect(result).toBe(true);
  });

  it('sends the user home, naming the privileges, when they may not open that screen', () => {
    auth.canAccess.mockReturnValue(false);
    let result: unknown;

    (run(under(['ledger', ['ledger']])) as Observable<unknown>).subscribe((r) => (result = r));

    expect(auth.canAccess).toHaveBeenCalledWith('view_inventory_ledger');
    expect(notify.error).toHaveBeenCalledWith('shared.errors.noAccess');
    expect(result).toEqual({ commands: ['/today'] });
  });

  it('lets deeper routes and unknown paths through without another check', () => {
    expect(run(under(['receiving', ['receiving']], [':id', ['42']]))).toBe(true);
    expect(run(under(['items', ['items']], ['', []]))).toBe(true);
    expect(run(under(['toString', ['toString']]))).toBe(true);
    expect(run(snapshot(['', []], ['admin', ['admin']], ['items', ['items']]))).toBe(true);
    expect(run({ routeConfig: null, url: [] } as unknown as ActivatedRouteSnapshot)).toBe(true);
    expect(auth.ensurePrivileges).not.toHaveBeenCalled();
  });

  it('maps every inventory screen to a key the shared privilege map knows, and the mount key to all of them', () => {
    const keys = Object.values(INVENTORY_SCREEN_PERMISSIONS);
    for (const key of keys) {
      expect(HQ_PERMISSION_PRIVILEGES[key]?.length, key).toBeGreaterThan(0);
    }
    const screenCodes = new Set(keys.flatMap((key) => [...HQ_PERMISSION_PRIVILEGES[key]]));
    expect(new Set(HQ_PERMISSION_PRIVILEGES[INVENTORY_PERMISSION])).toEqual(screenCodes);
  });

  it('gives every inventory screen an audit key of the inventory area', () => {
    for (const path of Object.keys(INVENTORY_SCREEN_PERMISSIONS)) {
      expect(INVENTORY_SCREEN_KEYS_BY_ROUTE[`inventory/${path}`], path).toBe(`inventory.${path}`);
    }
  });
});
