import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { vi } from 'vitest';
import { environment } from '../../../environments/environment';
import { SCREEN_KEYS_BY_ROUTE, SCREEN_KEY_PATTERN, SCREEN_VIEWS_PATH } from './screen-view.constants';
import { ScreenViewService, resolveScreenKey, sanitizePathToKey } from './screen-view-service';

function chain(...segments: Array<string | { path: string; data: Record<string, unknown> }>): ActivatedRouteSnapshot {
  let child: ActivatedRouteSnapshot | null = null;
  for (const seg of [...segments].reverse()) {
    const cfg = typeof seg === 'string' ? { path: seg } : seg;
    child = { routeConfig: cfg, firstChild: child } as unknown as ActivatedRouteSnapshot;
  }
  return { routeConfig: null, firstChild: child } as unknown as ActivatedRouteSnapshot;
}

describe('screen key resolution', () => {
  it('every mapped key matches the server pattern', () => {
    for (const key of Object.values(SCREEN_KEYS_BY_ROUTE)) {
      expect(SCREEN_KEY_PATTERN.test(key), key).toBe(true);
      expect(key.length).toBeLessThanOrEqual(100);
    }
  });

  it('maps the joined route template', () => {
    expect(resolveScreenKey(chain('', 'payments', 'invoice/:paymentId'), '/payments/invoice/77')).toBe('payments.invoice');
  });

  it('route data screenKey wins over the map', () => {
    const root = chain('payments', { path: 'make-payment', data: { screenKey: 'payments.custom' } });
    expect(resolveScreenKey(root, '/payments/make-payment')).toBe('payments.custom');
  });

  it('keeps a key declared on a parent route when the leaf route declares none', () => {
    const root = chain({ path: 'matrimony', data: { screenKey: 'matrimony.dashboard' } }, '');
    expect(resolveScreenKey(root, '/matrimony/dashboard')).toBe('matrimony.dashboard');
  });

  it('the deepest declared key wins over a parent key', () => {
    const root = chain({ path: 'agent', data: { screenKey: 'matrimony.agent' } }, { path: 'add', data: { screenKey: 'matrimony.agent.add' } });
    expect(resolveScreenKey(root, '/matrimony/agent/add')).toBe('matrimony.agent.add');
  });

  it('falls back to a sanitised dotted path', () => {
    expect(resolveScreenKey(chain('mystery'), '/Mystery/Page One/12345678?x=1')).toBe('mystery.page_one.id');
    expect(sanitizePathToKey('/')).toBe('root');
    expect(sanitizePathToKey('/_x')).toBe('screen._x');
    const long = sanitizePathToKey(`/${'a'.repeat(150)}`);
    expect(long.length).toBe(100);
    expect(SCREEN_KEY_PATTERN.test(long)).toBe(true);
  });
});

describe('ScreenViewService', () => {
  let events$: Subject<unknown>;
  let service: ScreenViewService;
  let fetchMock: ReturnType<typeof vi.fn>;

  const nav = (url: string) => events$.next(new NavigationEnd(1, url, url));
  const sentEvents = (call = 0) => JSON.parse(fetchMock.mock.calls[call][1].body).events as Array<Record<string, unknown>>;
  const flushMicrotasks = () => Promise.resolve().then(() => Promise.resolve());

  beforeEach(() => {
    vi.useFakeTimers();
    localStorage.setItem('access_token', 'tkn');
    events$ = new Subject<unknown>();
    fetchMock = vi.fn().mockResolvedValue({ status: 200 });
    vi.stubGlobal('fetch', fetchMock);
    TestBed.configureTestingModule({
      providers: [
        {
          provide: Router,
          useValue: { events: events$, routerState: { snapshot: { root: chain('home') } } },
        },
      ],
    });
    service = TestBed.inject(ScreenViewService);
    service.start();
  });

  afterEach(() => {
    service.ngOnDestroy();
    localStorage.clear();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('records a navigation and flushes on the interval with the bearer token', async () => {
    nav('/home');
    expect(service.pendingCount).toBe(1);
    expect(fetchMock).not.toHaveBeenCalled();
    vi.advanceTimersByTime(15_000);
    await flushMicrotasks();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${environment.gatewayUrl}${SCREEN_VIEWS_PATH}`);
    expect(init.keepalive).toBe(true);
    expect(init.headers['Authorization']).toBe('Bearer tkn');
    const [event] = sentEvents();
    expect(event['screenKey']).toBe('home.dashboard');
    expect(event['appChannel']).toBe('WEB');
    expect(event['feature']).toBe('home');
    expect(service.pendingCount).toBe(0);
  });

  it('flushes as soon as the buffer reaches the batch size', async () => {
    for (let i = 0; i < 20; i++) nav(`/p${i}`);
    await flushMicrotasks();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(sentEvents()).toHaveLength(20);
  });

  it('ignores a repeated url and the federated /admin area', () => {
    nav('/home');
    nav('/home');
    nav('/admin/finance');
    expect(service.pendingCount).toBe(1);
  });

  it('caps the buffer at 200, dropping the oldest', () => {
    // block auto-flush by going offline so events accumulate
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false);
    for (let i = 0; i < 250; i++) nav(`/p${i}`);
    expect(service.pendingCount).toBe(200);
  });

  it('flushes everything on tab hide', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false);
    for (let i = 0; i < 5; i++) nav(`/p${i}`);
    vi.restoreAllMocks();
    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true });
    document.dispatchEvent(new Event('visibilitychange'));
    await flushMicrotasks();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(sentEvents()).toHaveLength(5);
    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true });
  });

  it('sends nothing and buffers nothing when signed out', async () => {
    localStorage.removeItem('access_token');
    for (let i = 0; i < 25; i++) nav(`/p${i}`);
    expect(service.pendingCount).toBe(0);
    service.flush(true);
    await flushMicrotasks();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('never throws on a network failure and drops the batch', async () => {
    fetchMock.mockRejectedValue(new TypeError('offline'));
    nav('/home');
    expect(() => service.flush()).not.toThrow();
    await flushMicrotasks();
    expect(service.pendingCount).toBe(0);
  });

  it('pauses after a 429 until the pause has elapsed', async () => {
    fetchMock.mockResolvedValue({ status: 429 });
    nav('/home');
    service.flush();
    await flushMicrotasks();
    expect(fetchMock).toHaveBeenCalledTimes(1);

    nav('/profile');
    service.flush();
    await flushMicrotasks();
    expect(fetchMock).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(61_000);
    await flushMicrotasks();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('unsubscribes and stops the timer on destroy', () => {
    service.ngOnDestroy();
    nav('/home');
    expect(service.pendingCount).toBe(0);
    vi.advanceTimersByTime(60_000);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
