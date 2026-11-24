import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, UrlTree } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HQ_PERMISSION_PRIVILEGES } from 'hq-shared-lib';
import { Observable, of } from 'rxjs';
import { vi } from 'vitest';
import { NAV_ITEMS } from '../layout/nav-items.constants';
import { AuthService } from '../shared/services/auth-service';
import { NotificationService } from '../shared/services/notification-service';
import { FOOD_PARTNER_PERMISSION, FOOD_PARTNER_ROUTE, FOOD_PARTNER_SCREEN_PERMISSIONS, FOOD_PERMISSION } from './food-admin.constants';
import { foodPartnerScreenGuard } from './food-partner-screen-guard';

describe('foodPartnerScreenGuard', () => {
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

  /** The chain Rider_Web builds for /food-partner/<segment>: layout '', the mount, the remote's shell '' and the screen. */
  const screen = (segment: string): ActivatedRouteSnapshot => {
    const routes = [
      { routeConfig: { path: '' }, url: [] },
      { routeConfig: { path: 'food-partner' }, url: [{ path: 'food-partner' }] },
      { routeConfig: { path: '' }, url: [] },
      { routeConfig: { path: segment }, url: [{ path: segment }] },
    ];
    (routes[3] as Record<string, unknown>)['pathFromRoot'] = routes;
    return routes[3] as unknown as ActivatedRouteSnapshot;
  };
  const run = (route: ActivatedRouteSnapshot) => TestBed.runInInjectionContext(() => foodPartnerScreenGuard(route, {} as never));

  it('opens the join screen to any signed-in user, without a privilege check', () => {
    expect(run(screen('join'))).toBe(true);
    expect(auth.canAccess).not.toHaveBeenCalled();
  });

  it('checks the partner key on every partner screen and blocks one the user may not open', () => {
    auth.canAccess.mockReturnValue(false);
    let result: unknown;
    (run(screen('staff')) as Observable<unknown>).subscribe((r) => (result = r));
    expect(result).toEqual({ commands: ['/today'] });
    expect(auth.canAccess).toHaveBeenCalledWith(FOOD_PARTNER_PERMISSION);
    expect(Object.keys(FOOD_PARTNER_SCREEN_PERMISSIONS)).toEqual(['dashboard', 'menu', 'hours', 'statements', 'staff']);
  });

  it('is opened by a partner privilege only, never by the staff Food privileges', () => {
    expect([...HQ_PERMISSION_PRIVILEGES[FOOD_PARTNER_PERMISSION]]).toEqual(['FOOD_PARTNER_RESTAURANT_LIST']);
    expect(HQ_PERMISSION_PRIVILEGES[FOOD_PERMISSION]).not.toContain('FOOD_PARTNER_RESTAURANT_LIST');
    const item = NAV_ITEMS.find((n) => n.path === FOOD_PARTNER_ROUTE);
    expect(item?.permission).toBe(FOOD_PARTNER_PERMISSION);
    expect(item?.label).toBe('nav.items.foodPartner');
  });
});
