import { TestBed } from '@angular/core/testing';
import { Event as RouterEvent, NavigationCancel, NavigationEnd, NavigationError, NavigationStart } from '@angular/router';
import { Subject } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BrandingService } from '../shared/services/branding-service';
import { Layout } from './layout';

describe('Layout', () => {
  let events: Subject<RouterEvent>;
  let layout: Layout;
  let query: Record<string, string>;

  beforeEach(() => {
    events = new Subject<RouterEvent>();
    query = {};
    const router = { events, parseUrl: () => ({ queryParams: query }) };
    TestBed.configureTestingModule({ providers: [{ provide: BrandingService, useValue: { branding: { appName: 'Acme' } } }] });
    layout = TestBed.runInInjectionContext(() => new Layout(router as never));
    layout.ngOnInit();
  });

  const end = (url: string) => events.next(new NavigationEnd(1, url, url));

  it('exposes the deployment branding', () => {
    expect(layout.branding.appName).toBe('Acme');
  });

  it('shows the loading bar between navigation start and end, cancel or error', () => {
    events.next(new NavigationStart(1, '/x'));
    expect(layout.routeLoading).toBe(true);
    end('/home');
    expect(layout.routeLoading).toBe(false);
    events.next(new NavigationStart(2, '/x'));
    events.next(new NavigationCancel(2, '/x', 'no'));
    expect(layout.routeLoading).toBe(false);
    events.next(new NavigationStart(3, '/x'));
    events.next(new NavigationError(3, '/x', new Error('x')));
    expect(layout.routeLoading).toBe(false);
  });

  it('uses the hand-written title and breadcrumbs for known routes', () => {
    end('/payments/make-payment');
    expect(layout.pageTitleKey).toBe('pageMeta.payments.title');
    expect(layout.breadcrumbs).toEqual([{ label: 'pageMeta.home.breadcrumb', path: '/home' }, { label: 'pageMeta.payments.breadcrumb' }]);
  });

  it('takes the title and breadcrumb of any other screen from the sidebar menu', () => {
    end('/profile');
    expect(layout.pageTitleKey).toBe('nav.items.myProfile');
    expect(layout.breadcrumbs).toEqual([{ label: 'nav.sections.account' }, { label: 'nav.items.myProfile' }]);
  });

  it('titles the reports create / edit screens from their setup menu item, query string or not', () => {
    const cases: Array<[string, string]> = [
      ['/reports/reports-config/cre?productName=MATRIMONY', 'nav.items.reportsConfig'],
      ['/reports/reports-config/6650f1c2a9e4b1', 'nav.items.reportsConfig'],
      ['/reports/report-template/cre', 'nav.items.reportsTemplates'],
      ['/reports/reports-custom-fields/cre', 'nav.items.reportsCustomFields'],
      ['/reports/system-config/cre', 'nav.items.reportsSystemConfig'],
    ];
    for (const [url, title] of cases) {
      end(url);
      expect(layout.pageTitleKey, url).toBe(title);
      expect(layout.breadcrumbs, url).toEqual([{ label: 'nav.sections.reportsSetup' }, { label: title }]);
    }
  });

  it('titles the page it was created on (reload / new tab), before any further navigation', () => {
    const cases: Array<[string, string]> = [
      ['/reports/reports-config/cre?productName=MATRIMONY', 'nav.items.reportsConfig'],
      ['/reports/reports-config/6650f1c2a9e4b1', 'nav.items.reportsConfig'],
      ['/reports/report-template/cre', 'nav.items.reportsTemplates'],
      ['/reports/reports-custom-fields/cre', 'nav.items.reportsCustomFields'],
      ['/reports/system-config/cre', 'nav.items.reportsSystemConfig'],
    ];
    for (const [url, title] of cases) {
      const router = { url, events: new Subject<RouterEvent>(), parseUrl: () => ({ queryParams: {} }) };
      const fresh = TestBed.runInInjectionContext(() => new Layout(router as never));
      fresh.ngOnInit();
      expect(fresh.pageTitleKey, url).toBe(title);
      expect(fresh.breadcrumbs, url).toEqual([{ label: 'nav.sections.reportsSetup' }, { label: title }]);
    }
  });

  it('hides the sidebar when created on a forced change-password visit', () => {
    const router = { url: '/change-password?reason=forced', events: new Subject<RouterEvent>(), parseUrl: () => ({ queryParams: { reason: 'forced' } }) };
    const fresh = TestBed.runInInjectionContext(() => new Layout(router as never));
    fresh.ngOnInit();
    expect(fresh.hideSidebar).toBe(true);
    expect(fresh.isFullBleed).toBe(true);
  });

  it('falls back to no title and no breadcrumbs for an unknown route', () => {
    end('/nowhere-at-all');
    expect(layout.pageTitleKey).toBeNull();
    expect(layout.breadcrumbs).toEqual([]);
  });

  it('treats change-password as full bleed and hides the sidebar only for a forced visit', () => {
    end('/change-password');
    expect(layout.isFullBleed).toBe(true);
    expect(layout.hideSidebar).toBe(false);
    query['reason'] = 'forced';
    end('/change-password?reason=forced');
    expect(layout.hideSidebar).toBe(true);
    end('/profile');
    expect(layout.isFullBleed).toBe(false);
    expect(layout.hideSidebar).toBe(false);
  });

  it('renders every food console screen full bleed, and nothing that only starts with the same letters', () => {
    for (const url of ['/food', '/food/dashboard', '/food/orders/7?tab=timeline']) {
      end(url);
      expect(layout.isFullBleed, url).toBe(true);
    }
    end('/foodstuff');
    expect(layout.isFullBleed).toBe(false);
    end('/inventory/items');
    expect(layout.isFullBleed).toBe(false);
  });

  it('closes the mobile menu after navigating', () => {
    layout.openMobile();
    expect(layout.mobileOpen).toBe(true);
    end('/profile');
    expect(layout.mobileOpen).toBe(false);
    layout.openMobile();
    layout.closeMobile();
    expect(layout.mobileOpen).toBe(false);
  });

  it('toggles the collapsed sidebar', () => {
    layout.toggleSidebar();
    expect(layout.collapsed).toBe(true);
    layout.toggleSidebar();
    expect(layout.collapsed).toBe(false);
  });
  describe('after a language change', () => {
    afterEach(() => vi.useRealTimers());

    function withLanguages() {
      vi.useFakeTimers();
      const langChange = new Subject<{ lang: string }>();
      const router = { events: new Subject<RouterEvent>(), parseUrl: () => ({ queryParams: {} }) };
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [
          { provide: BrandingService, useValue: { branding: { appName: 'Acme' } } },
          { provide: TranslateService, useValue: { onLangChange: langChange } },
        ],
      });
      const fresh = TestBed.runInInjectionContext(() => new Layout(router as never));
      fresh.ngOnInit();
      return { fresh, langChange };
    }

    it('takes the page out for an instant and puts it back, so the router creates it again in the new language', () => {
      const { fresh, langChange } = withLanguages();
      expect(fresh.showPage).toBe(true);

      langChange.next({ lang: 'te' });
      expect(fresh.showPage).toBe(false);
      vi.runAllTimers();
      expect(fresh.showPage).toBe(true);
    });

    it('handles two quick changes in a row and stops listening once the layout is gone', () => {
      const { fresh, langChange } = withLanguages();

      langChange.next({ lang: 'te' });
      langChange.next({ lang: 'en' });
      vi.runAllTimers();
      expect(fresh.showPage).toBe(true);

      fresh.ngOnDestroy();
      langChange.next({ lang: 'hi' });
      expect(fresh.showPage).toBe(true);
    });
  });
});
