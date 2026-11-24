import { AppAreaLogo } from '../shared/app-area-logo/app-area-logo';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { provideRouter, RouterModule } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HQ_CONFIG, HqDocumentService, HqSharedLibModule } from 'hq-shared-lib';
import { Observable, Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { NAV_AREAS } from '../layout/nav-areas.constants';
import { ApiService } from '../shared/services/api-service';
import { AuthService } from '../shared/services/auth-service';
import { BrandingService } from '../shared/services/branding-service';
import { MaterialModule } from '../shared/material-module';
import { NotificationService } from '../shared/services/notification-service';
import { Home } from './home';
import { HomeApplicationsService } from './home-applications.service';
import { APP_CODE_BY_SUITE_KEY, CORE_SUITE_APP_KEYS, FEATURE_PREVIEW_COUNT, HUB_EXTRA_APPS, PLATFORM_CAPABILITIES } from './home.constants';
import { HomeLoadStatus } from './home.models';

describe('Home (Applications hub)', () => {
  const user: { id?: string; name?: string; email?: string } = { id: 'u1', name: 'Asha Rao' };
  let allowed: (permission: string) => boolean;
  const auth = {
    get currentUser() { return user; },
    canAccess: (permission: string) => allowed(permission),
  };
  const notify = { success: vi.fn(), error: vi.fn() };
  const api = { post: vi.fn() };
  let fixture: ComponentFixture<Home>;
  const status = signal<HomeLoadStatus>('ready');
  const subscribedCodes = signal<string[]>([]);
  let granted: string[] = [];
  const subscriptions = { status, subscribedCodes, load: vi.fn(), grantedCodes: () => granted };

  function create(translations?: Record<string, string>): Home {
    TestBed.configureTestingModule({
      declarations: [Home],
      imports: [TranslateModule.forRoot(), HqSharedLibModule, MaterialModule, FormsModule, RouterModule, AppAreaLogo],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: NotificationService, useValue: notify },
        { provide: ApiService, useValue: api },
        { provide: HomeApplicationsService, useValue: subscriptions },
        { provide: HqDocumentService, useValue: { list: () => of([]) } },
        { provide: HQ_CONFIG, useValue: { currentUserId: () => null } },
      ],
    });
    fixture = TestBed.createComponent(Home);
    if (translations) {
      const translate = TestBed.inject(TranslateService);
      translate.setTranslation('en', translations);
      translate.use('en');
    }
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  const el = (): HTMLElement => fixture.nativeElement;
  const cards = (): HTMLElement[] => Array.from(el().querySelectorAll('.app-card'));
  const card = (key: string): HTMLElement => el().querySelector(`.app-card[data-app="${key}"]`) as HTMLElement;
  const testId = (id: string): string => (el().querySelector(`[data-testid="${id}"]`)?.textContent ?? '').replace(/\s+/g, ' ').trim();
  /** The component changes its own state in these tests, so mark the view dirty before checking it (as the app's other specs do). */
  const refresh = (): void => {
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
  };
  const checkButton = (): HTMLButtonElement => el().querySelector('.hub-check__btn') as HTMLButtonElement;

  beforeEach(() => {
    vi.clearAllMocks();
    user.id = 'u1';
    user.name = 'Asha Rao';
    user.email = undefined;
    allowed = () => true;
    api.post.mockReturnValue(of({}));
    status.set('ready');
    subscribedCodes.set(Object.values(APP_CODE_BY_SUITE_KEY));
    granted = Object.values(APP_CODE_BY_SUITE_KEY);
    subscriptions.load.mockReturnValue(of(undefined));
  });

  it('greets the user with the deployment name from the branding', () => {
    const component = create({ 'home.title': 'Welcome to {{appName}}, {{name}}' });
    const branding = TestBed.inject(BrandingService).branding;

    expect(component.userName).toBe('Asha Rao');
    expect(el().textContent).toContain(`Welcome to ${branding.appName}, Asha Rao`);
  });

  it('falls back from name to the email prefix to a default name', () => {
    const component = create();
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { 'home.defaultName': 'friend' });
    translate.use('en');

    user.name = '';
    user.email = 'ravi.k@example.com';
    expect(component.userName).toBe('ravi.k');
    user.email = undefined;
    expect(component.userName).toBe('friend');
  });

  it('shows a card for every application of the suite', () => {
    create();

    // products first, then the shared applications; the extras are the ones the sidebar has no area for
    expect(cards().map((c) => c.getAttribute('data-app'))).toEqual([
      'franchise', 'wallet', 'matrimony', 'admin', 'food', 'ims', 'train',
      'access', 'owner', 'agent', 'accounting', 'reports', 'developer', 'qa', 'communications', 'documents', 'support', 'payments',
    ]);
    expect(NAV_AREAS.every((area) => card(area.key) !== null)).toBe(true);
  });

  it('lists the features of an application, a few first and all of them on request', () => {
    const component = create();
    const admin = (): HTMLElement => card('admin');

    expect(admin().querySelectorAll('.feature')).toHaveLength(FEATURE_PREVIEW_COUNT);
    const toggle = admin().querySelector('.app-card__more') as HTMLButtonElement;
    expect(toggle.getAttribute('aria-expanded')).toBe('false');

    toggle.click();
    refresh();

    expect(component.featuresOf(component.apps.find((a) => a.key === 'admin')!).length).toBeGreaterThan(FEATURE_PREVIEW_COUNT);
    expect(admin().querySelectorAll('.feature').length).toBeGreaterThan(FEATURE_PREVIEW_COUNT);
    expect((admin().querySelector('.app-card__more') as HTMLButtonElement).getAttribute('aria-expanded')).toBe('true');
  });

  it('groups the cards into products and shared applications', () => {
    create();

    const keys = (kind: string) => Array.from(el().querySelectorAll(`.hub-group[data-kind="${kind}"] .app-card`)).map((c) => c.getAttribute('data-app'));
    expect(keys('product')).toContain('wallet');
    expect(keys('product')).toContain('franchise');
    expect(keys('shared')).toContain('access');
    expect(keys('shared')).not.toContain('wallet');
  });

  describe('which applications the organization and the user may see', () => {
    it('loads the organization subscriptions when it opens', () => {
      create();

      expect(subscriptions.load).toHaveBeenCalledWith(false);
    });

    it('hides applications the organization is not subscribed to, also from the stats and the filter chips', () => {
      subscribedCodes.set([APP_CODE_BY_SUITE_KEY['wallet']]);
      const component = create();

      const keys = cards().map((c) => c.getAttribute('data-app'));
      expect(keys).toContain('wallet');
      expect(keys).not.toContain('matrimony');
      expect(keys).not.toContain('food');
      expect(component.stats.apps).toBe(keys.length);
      expect(testId('stat-available')).toContain(`/ ${keys.length}`);
      component.setFilter('locked');
      refresh();
      expect(cards().map((c) => c.getAttribute('data-app'))).not.toContain('matrimony');
    });

    it('shows platform tooling areas whatever the subscriptions', () => {
      subscribedCodes.set([]);
      create();

      // the core tooling areas, plus the hub-only applications (they need no subscription); products first, then the shared ones
      expect(cards().map((c) => c.getAttribute('data-app'))).toEqual([
        'franchise', 'wallet', 'ims', 'train',
        'access', 'owner', 'agent', 'accounting', 'reports', 'developer', 'qa', 'communications', 'documents', 'support', 'payments',
      ]);
      expect(CORE_SUITE_APP_KEYS.every((key) => card(key) !== null)).toBe(true);
    });

    it('hides a subscribed application the user can neither open nor is granted, and shows it locked when granted', () => {
      allowed = (permission) => !permission.startsWith('view_matrimony');
      granted = [];
      create();
      expect(card('matrimony')).toBeNull();

      granted = [APP_CODE_BY_SUITE_KEY['matrimony']];
      refresh();
      expect(card('matrimony').classList.contains('app-card--locked')).toBe(true);
    });

    it('shows a loading placeholder and no cards while the subscriptions load', () => {
      status.set('loading');
      create();

      expect(el().querySelector('[data-testid="apps-loading"]')).not.toBeNull();
      expect(cards().filter((c) => c.hasAttribute('data-app'))).toHaveLength(0);
      expect(testId('stat-available')).toContain('0');
    });

    it('fails closed with a message and a retry when the subscriptions cannot be read', () => {
      status.set('failed');
      create();

      expect(el().querySelector('[data-testid="apps-error"]')).not.toBeNull();
      expect(cards()).toHaveLength(0);

      (el().querySelector('[data-testid="apps-error"] button') as HTMLButtonElement).click();
      expect(subscriptions.load).toHaveBeenLastCalledWith(true);
    });
  });

  it('opens an application at its landing screen and previews its screens as links', () => {
    create();

    const matrimony = card('matrimony');
    expect(matrimony.querySelector('.app-card__open')?.getAttribute('href')).toBe('/matrimony/dashboard');
    expect(matrimony.querySelectorAll('.app-card__chips a')).toHaveLength(3);
    expect(matrimony.classList.contains('app-card--locked')).toBe(false);
  });

  it('shows an application the user may not use as not enabled, with no way in', () => {
    allowed = (permission) => !permission.startsWith('view_matrimony');
    create();

    const matrimony = card('matrimony');
    expect(matrimony.classList.contains('app-card--locked')).toBe(true);
    expect(matrimony.querySelector('.app-card__open')).toBeNull();
    expect(matrimony.querySelectorAll('.app-card__chips a')).toHaveLength(0);
    expect(matrimony.querySelector('.app-card__help')).not.toBeNull();
  });

  it('counts the applications and screens in the hero strip', () => {
    allowed = () => false;
    const component = create();

    const stats = component.stats;
    expect(testId('stat-available')).toContain(`${stats.available}`);
    expect(testId('stat-available')).toContain(`${stats.apps}`);
    expect(testId('stat-locked')).toBe(`${stats.locked}`);
    expect(testId('stat-screens')).toBe(`${stats.screens}`);
    expect(stats.locked).toBeGreaterThan(0);
  });

  it('narrows the grid with the filter chips', () => {
    allowed = (permission) => permission === 'view_reports_dashboard';
    const component = create();

    component.setFilter('available');
    refresh();
    const availableKeys = cards().map((c) => c.getAttribute('data-app'));
    expect(availableKeys).toContain('reports');
    expect(availableKeys).not.toContain('matrimony');

    component.setFilter('locked');
    refresh();
    const lockedKeys = cards().map((c) => c.getAttribute('data-app'));
    expect(lockedKeys).toContain('matrimony');
    expect(lockedKeys).not.toContain('reports');
  });

  it('searches applications by the name of their screens, and offers a reset when nothing matches', () => {
    const component = create({ 'nav.items.walletCustomers': 'Wallet customers', 'nav.areas.wallet': 'Wallet', 'home.apps.none': 'Nothing found' });

    component.query = 'wallet customers';
    refresh();
    expect(cards().map((c) => c.getAttribute('data-app'))).toContain('wallet');

    component.query = 'no such application anywhere';
    refresh();
    expect(cards()).toHaveLength(0);
    expect(el().querySelector('.hub-empty')?.textContent).toContain('Nothing found');

    (el().querySelector('.hub-empty__reset') as HTMLButtonElement).click();
    refresh();
    expect(component.query).toBe('');
    expect(cards()).toHaveLength(NAV_AREAS.length + HUB_EXTRA_APPS.length);
  });

  it('shows Train on the hub only, as a product the user can open', () => {
    create();

    expect(card('train')).not.toBeNull();
    expect(el().querySelector('.hub-group[data-kind="product"] .app-card[data-app="train"]')).not.toBeNull();
  });

  it('shows every platform capability, as a link only when the user may open its screen', () => {
    allowed = (permission) => permission === 'view_roles';
    create();

    const tiles = Array.from(el().querySelectorAll('.cap'));
    expect(tiles).toHaveLength(PLATFORM_CAPABILITIES.length);
    expect(el().querySelector('[data-capability="access"] .cap__go')?.getAttribute('href')).toBe('/access-management/roles');
    expect(el().querySelector('[data-capability="audit"] .cap__go')).toBeNull();
  });

  it('sends a test notification to the signed-in user over in-app and push', () => {
    const component = create();

    checkButton().click();

    expect(api.post).toHaveBeenCalledTimes(1);
    const [path, body] = api.post.mock.calls[0];
    expect(path).toBe('/v1/nms/notify');
    expect(body.requestObject).toMatchObject({ event: 'TEST_NOTIFICATION', userIds: ['u1'], channels: ['IN_APP', 'FCM_PUSH'] });
    expect(notify.success).toHaveBeenCalledWith('home.messages.testSent');
    expect(component.sendingTestNotification).toBe(false);
  });

  it('disables the send button and changes its label while the request is outstanding', () => {
    const pending = new Subject<unknown>();
    api.post.mockReturnValue(pending as Observable<unknown>);
    const component = create();

    checkButton().click();
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();

    expect(component.sendingTestNotification).toBe(true);
    expect(checkButton().disabled).toBe(true);
    expect(checkButton().textContent).toContain('home.placeholder.sending');

    pending.next({});
    pending.complete();
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    expect(checkButton().disabled).toBe(false);
  });

  it('shows the server detail when the test notification fails, or a default message', () => {
    const component = create();

    api.post.mockReturnValue(throwError(() => ({ error: { detail: 'NMS is down' } })));
    component.sendTestNotification();
    expect(notify.error).toHaveBeenLastCalledWith('NMS is down');
    expect(component.sendingTestNotification).toBe(false);

    api.post.mockReturnValue(throwError(() => new Error('x')));
    component.sendTestNotification();
    expect(notify.error).toHaveBeenLastCalledWith('home.messages.testFailed');
  });

  it('refuses to send a test notification when the session has no user id', () => {
    user.id = undefined;
    const component = create();

    component.sendTestNotification();

    expect(api.post).not.toHaveBeenCalled();
    expect(notify.error).toHaveBeenCalledWith('home.messages.noUserId');
    expect(component.sendingTestNotification).toBe(false);
  });
});
