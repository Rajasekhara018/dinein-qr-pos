import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, UrlTree } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HQ_PERMISSION_PRIVILEGES } from 'hq-shared-lib';
import { Observable, of } from 'rxjs';
import { vi } from 'vitest';
import { NAV_ITEMS } from '../layout/nav-items.constants';
import { AuthService } from '../shared/services/auth-service';
import { NotificationService } from '../shared/services/notification-service';
import { sanitizePathToKey } from '../shared/services/screen-view-service';
import { FOOD_PERMISSION, FOOD_SCREEN_PERMISSIONS } from './food-admin.constants';
import { foodScreenGuard } from './food-screen-guard';

describe('foodScreenGuard', () => {
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

  /** The chain Rider_Web builds for /food/<segments>: layout '', the 'food' mount, the remote's shell '' and the screen route. */
  const screen = (routePath: string, segments: string[]): ActivatedRouteSnapshot => {
    const routes = [
      { routeConfig: { path: '' }, url: [] },
      { routeConfig: { path: 'food' }, url: [{ path: 'food' }] },
      { routeConfig: { path: '' }, url: [] },
      { routeConfig: { path: routePath }, url: segments.map((path) => ({ path })) },
    ];
    (routes[3] as Record<string, unknown>)['pathFromRoot'] = routes;
    return routes[3] as unknown as ActivatedRouteSnapshot;
  };
  const run = (route: ActivatedRouteSnapshot) => TestBed.runInInjectionContext(() => foodScreenGuard(route, {} as never));
  const outcome = (route: ActivatedRouteSnapshot): unknown => {
    let result: unknown;
    (run(route) as Observable<unknown>).subscribe((r) => (result = r));
    return result;
  };

  it('checks the key of the resource in the URL, for its list, detail, new and edit pages', () => {
    auth.canAccess.mockReturnValue(true);

    expect(outcome(screen(':resource', ['orders']))).toBe(true);
    expect(outcome(screen(':resource/:id/edit', ['delivery-partners', '7', 'edit']))).toBe(true);
    expect(outcome(screen('notifications/send', ['notifications', 'send']))).toBe(true);
    expect(outcome(screen('dashboard', ['dashboard']))).toBe(true);

    expect(auth.canAccess.mock.calls.map((c) => c[0])).toEqual([
      'view_food_orders', 'view_food_delivery_partners', 'view_food_notifications', 'view_food_dashboard',
    ]);
  });

  it('blocks a direct URL to a screen the user may not open, naming the privilege it needs', () => {
    auth.canAccess.mockReturnValue(false);

    expect(outcome(screen(':resource/:id', ['payments', '3']))).toEqual({ commands: ['/today'] });
    expect(auth.canAccess).toHaveBeenCalledWith('view_food_payments');
    expect(notify.error).toHaveBeenCalledWith('shared.errors.noAccess');
  });

  it('lets the remote shell and unknown resources through without a check', () => {
    const shell = { routeConfig: { path: '' }, url: [], pathFromRoot: [{ routeConfig: { path: 'food' }, url: [{ path: 'food' }] }] };
    expect(run(shell as unknown as ActivatedRouteSnapshot)).toBe(true);
    expect(run(screen(':resource', ['unknown-thing']))).toBe(true);
    expect(auth.ensurePrivileges).not.toHaveBeenCalled();
  });

  it('maps each Food screen to the catalogue READ privilege of its main call, and the mount to any of them', () => {
    const expected: Record<string, string> = {
      restaurants: 'FOOD_RESTAURANT_READ', categories: 'FOOD_CATEGORY_READ', foods: 'FOOD_FOOD_READ',
      customers: 'FOOD_CUSTOMER_READ', 'admin-users': 'FOOD_ADMIN_USER_READ', orders: 'FOOD_ORDER_READ',
      payments: 'FOOD_PAYMENT_READ', refunds: 'FOOD_REFUND_READ', coupons: 'FOOD_COUPON_READ', offers: 'FOOD_OFFER_READ',
      reviews: 'FOOD_REVIEW_READ', 'delivery-partners': 'FOOD_DELIVERY_PARTNER_READ', 'support-tickets': 'FOOD_SUPPORT_TICKET_READ',
      notifications: 'FOOD_NOTIFICATION_READ', settings: 'FOOD_SETTING_READ',
      // Operations screens: the LIST/READ of their main call.
      'live-board': 'FOOD_LIVE_BOARD_READ', 'refund-requests': 'FOOD_REFUND_REQUEST_LIST',
      'restaurant-approvals': 'FOOD_RESTAURANT_APPLICATION_LIST', 'menu-reviews': 'FOOD_MENU_REVIEW_LIST',
      settlements: 'FOOD_SETTLEMENT_LIST',
    };
    for (const [resource, code] of Object.entries(expected)) {
      expect([...HQ_PERMISSION_PRIVILEGES[FOOD_SCREEN_PERMISSIONS[resource]]], resource).toEqual([code]);
    }
    // The insight screens open with their own READ privilege or, as before, the platform operator privilege (the food backend
    // admits operators only).
    const insights: Record<string, string> = { dashboard: 'FOOD_DASHBOARD_READ', reports: 'FOOD_REPORT_READ', analytics: 'FOOD_ANALYTICS_READ' };
    for (const [screen, code] of Object.entries(insights)) {
      expect([...HQ_PERMISSION_PRIVILEGES[FOOD_SCREEN_PERMISSIONS[screen]]], screen).toEqual([code, 'ORGANIZATION_LIST']);
    }
    const all = new Set(Object.values(FOOD_SCREEN_PERMISSIONS).flatMap((key) => [...HQ_PERMISSION_PRIVILEGES[key]]));
    expect(new Set(HQ_PERMISSION_PRIVILEGES[FOOD_PERMISSION])).toEqual(all);
  });

  it('gives every Food sidebar item the same key the guard checks', () => {
    const food = NAV_ITEMS.filter((item) => item.path.startsWith('/food/'));
    expect(food.length).toBe(Object.keys(FOOD_SCREEN_PERMISSIONS).length);
    for (const item of food) {
      expect(item.permission, item.path).toBe(FOOD_SCREEN_PERMISSIONS[item.path.replace('/food/', '')]);
    }
  });

  it('reports each Food screen under its own audit key, derived from its URL (food.<screen>), as the QA catalog expects', () => {
    for (const segment of Object.keys(FOOD_SCREEN_PERMISSIONS)) {
      expect(sanitizePathToKey(`/food/${segment}`)).toBe(`food.${segment}`);
    }
    expect(sanitizePathToKey('/food/orders/123/edit')).toBe('food.orders.id.edit');
  });
});
