import { TestBed } from '@angular/core/testing';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { Client } from '@stomp/stompjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiService } from './api-service';
import { AuthService } from './auth-service';
import { InAppNotificationService } from './in-app-notification-service';

/**
 * The specs of this app run in one shared module cache (the builder sets isolate: false), so the real stomp client may
 * already be bound into InAppNotificationService by an earlier file and vi.mock could not replace it. The real Client class
 * is therefore kept and only its network-touching methods are spied on in beforeEach.
 */
interface FakeSub { dest: string; cb: (m: { body: string }) => void; unsubscribe: ReturnType<typeof vi.fn> }
type FakeClient = Client & { subs: FakeSub[]; onConnect: () => void; beforeConnect: () => Promise<void>; webSocketFactory: () => WebSocket };
const stomp = {
  instances: [] as FakeClient[],
  activate: undefined as unknown as ReturnType<typeof vi.fn>,
  deactivate: undefined as unknown as ReturnType<typeof vi.fn>,
};

function spyOnStomp(): void {
  stomp.instances = [];
  stomp.activate = vi.spyOn(Client.prototype, 'activate').mockImplementation(function (this: Client) {
    stomp.instances.push(this as FakeClient);
    Object.defineProperty(this, 'active', { value: true, configurable: true });
  }) as never;
  stomp.deactivate = vi.spyOn(Client.prototype, 'deactivate').mockImplementation(function (this: Client) {
    Object.defineProperty(this, 'active', { value: false, configurable: true });
    return Promise.resolve();
  }) as never;
  vi.spyOn(Client.prototype, 'subscribe').mockImplementation(function (this: FakeClient, dest: string, cb: unknown) {
    const sub: FakeSub = { dest, cb: cb as FakeSub['cb'], unsubscribe: vi.fn() };
    (this.subs ??= []).push(sub);
    return sub as never;
  });
}

const raw = (id: string, over: Record<string, unknown> = {}) =>
  ({ id, userId: 'u1', title: `T${id}`, message: `M${id}`, read: false, createdAt: '2026-01-01T00:00:00Z', ...over });

/** A JWT whose exp is `secondsFromNow` from now. */
const jwt = (secondsFromNow: number | null) =>
  `h.${btoa(JSON.stringify(secondsFromNow === null ? {} : { exp: Math.floor(Date.now() / 1000) + secondsFromNow }))}.s`;

describe('InAppNotificationService', () => {
  let user$: BehaviorSubject<unknown>;
  let refresh: ReturnType<typeof vi.fn>;
  let api: { get: ReturnType<typeof vi.fn>; patch: ReturnType<typeof vi.fn> };
  let svc: InAppNotificationService;

  function create(): InAppNotificationService {
    TestBed.configureTestingModule({
      providers: [{ provide: ApiService, useValue: api }, { provide: AuthService, useValue: { user$, refresh } }],
    });
    return TestBed.inject(InAppNotificationService);
  }

  beforeEach(() => {
    localStorage.clear();
    spyOnStomp();
    refresh = vi.fn().mockReturnValue(of({ accessToken: 'unused' }));
    user$ = new BehaviorSubject<unknown>(null);
    api = {
      get: vi.fn((path: string) => path.endsWith('unread-count')
        ? of({ unreadCount: 3 })
        : of({ content: [raw('1'), raw('2')], totalElements: 2 })),
      patch: vi.fn().mockReturnValue(of(undefined)),
    };
  });
  afterEach(() => {
    TestBed.inject(InAppNotificationService).ngOnDestroy();
    localStorage.clear();
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  describe('lifecycle', () => {
    it('stays idle while nobody is signed in', () => {
      svc = create();
      expect(api.get).not.toHaveBeenCalled();
      expect(stomp.instances).toHaveLength(0);
    });

    it('does not start without an access token', () => {
      svc = create();
      user$.next({ id: 'u1' });
      expect(api.get).not.toHaveBeenCalled();
    });

    it('on sign-in loads the unread count and recent items and opens the socket', () => {
      localStorage.setItem('access_token', jwt(600));
      svc = create();
      user$.next({ id: 'u1' });
      expect(api.get).toHaveBeenCalledWith('/v1/nms/in-app/notifications/unread-count');
      expect(api.get).toHaveBeenCalledWith('/v1/nms/in-app/notifications', { page: 0, size: 10 });
      expect(svc.unreadCount$.value).toBe(3);
      expect(svc.notifications$.value.map((n) => n.id)).toEqual(['1', '2']);
      expect(svc.notifications$.value[0]).toMatchObject({ adminUserId: 'u1', type: 'SYSTEM_ALERT', title: 'T1' });
      expect(stomp.instances).toHaveLength(1);
      expect(stomp.activate).toHaveBeenCalled();
    });

    it('keeps the current state when loading fails', () => {
      localStorage.setItem('access_token', jwt(600));
      api.get.mockReturnValue(throwError(() => new Error('down')));
      svc = create();
      user$.next({ id: 'u1' });
      expect(svc.unreadCount$.value).toBe(0);
      expect(svc.notifications$.value).toEqual([]);
    });

    it('does not open a second socket while one is active', () => {
      localStorage.setItem('access_token', jwt(600));
      svc = create();
      user$.next({ id: 'u1' });
      user$.next({ id: 'u1' });
      expect(stomp.instances).toHaveLength(1);
    });

    it('on sign-out closes the socket and clears everything', () => {
      localStorage.setItem('access_token', jwt(600));
      svc = create();
      user$.next({ id: 'u1' });
      const client = stomp.instances[0];
      client['onConnect']();
      const sub = client.subs[0];
      user$.next(null);
      expect(sub.unsubscribe).toHaveBeenCalled();
      expect(stomp.deactivate).toHaveBeenCalled();
      expect(svc.notifications$.value).toEqual([]);
      expect(svc.unreadCount$.value).toBe(0);
    });
  });

  describe('live notifications', () => {
    let client: FakeClient;

    beforeEach(() => {
      localStorage.setItem('access_token', jwt(600));
      svc = create();
      user$.next({ id: 'u1' });
      client = stomp.instances[0];
      client['onConnect']();
    });

    it('subscribes to the per-user queue and prepends a pushed notification, counting it unread', () => {
      expect(client.subs[0].dest).toBe('/user/queue/notifications');
      client.subs[0].cb({ body: JSON.stringify(raw('9')) });
      expect(svc.notifications$.value[0].id).toBe('9');
      expect(svc.notifications$.value).toHaveLength(3);
      expect(svc.unreadCount$.value).toBe(4);
    });

    it('plays a two-tone chime once audio has been unlocked by a user gesture', () => {
      const osc = () => ({ type: '', frequency: { value: 0 }, connect: vi.fn(), start: vi.fn(), stop: vi.fn() });
      const oscillators: Array<ReturnType<typeof osc>> = [];
      const ctx = {
        state: 'suspended', currentTime: 0, destination: {},
        resume: vi.fn().mockResolvedValue(undefined),
        createOscillator: vi.fn(() => { const o = osc(); oscillators.push(o); return o; }),
        createGain: vi.fn(() => ({ gain: { setValueAtTime: vi.fn(), exponentialRampToValueAtTime: vi.fn() }, connect: vi.fn() })),
      };
      vi.stubGlobal('AudioContext', vi.fn(function () { return ctx; }));
      document.dispatchEvent(new Event('click'));
      client.subs[0].cb({ body: JSON.stringify(raw('9')) });
      expect(ctx.resume).toHaveBeenCalled();
      expect(oscillators.map((o) => o.frequency.value)).toEqual([880, 1175]);
      oscillators.forEach((o) => expect(o.start).toHaveBeenCalled());
    });

    it('reads the notification aloud shortly after, when speech synthesis exists', () => {
      vi.useFakeTimers();
      const speak = vi.fn();
      const cancel = vi.fn();
      vi.stubGlobal('speechSynthesis', { speak, cancel, resume: vi.fn() });
      let created: { text: string; onend?: () => void } | undefined;
      vi.stubGlobal('SpeechSynthesisUtterance', vi.fn(function (this: { text: string }, text: string) { this.text = text; created = this; }));
      Object.defineProperty(window, 'speechSynthesis', { value: { speak, cancel, resume: vi.fn() }, configurable: true });
      client.subs[0].cb({ body: JSON.stringify(raw('9')) });
      expect(speak).not.toHaveBeenCalled();
      vi.advanceTimersByTime(300);
      expect(speak).toHaveBeenCalledTimes(1);
      expect(created?.text).toBe('T9. M9');
      created?.onend?.();
      delete (window as unknown as Record<string, unknown>)['speechSynthesis'];
    });

    it('builds the websocket URL with the current token and without iframe transports', () => {
      localStorage.setItem('access_token', 'fresh-token');
      const sock = client['webSocketFactory']() as unknown as { url: string; _transportsWhitelist: string[]; close: () => void };
      expect(sock.url).toContain('?access_token=fresh-token');
      expect(sock._transportsWhitelist).toEqual(['websocket', 'xhr-streaming', 'xhr-polling']);
      sock.close();
    });
  });

  describe('token refresh before reconnect', () => {
    beforeEach(() => {
      localStorage.setItem('access_token', jwt(5));
      localStorage.setItem('refresh_token', 'r');
      svc = create();
      user$.next({ id: 'u1' });
    });

    it('refreshes a token that is about to expire and sends the new one on CONNECT', async () => {
      refresh.mockImplementation(() => { localStorage.setItem('access_token', 'renewed'); return of({ accessToken: 'renewed' }); });
      const client = stomp.instances[0];
      await client['beforeConnect']();
      expect(refresh).toHaveBeenCalled();
      expect(client.connectHeaders).toEqual({ Authorization: 'Bearer renewed' });
    });

    it('goes on with the old token when the refresh fails', async () => {
      refresh.mockReturnValue(throwError(() => new Error('401')));
      const client = stomp.instances[0];
      const old = localStorage.getItem('access_token');
      await client['beforeConnect']();
      expect(client.connectHeaders).toEqual({ Authorization: `Bearer ${old}` });
    });

    it('does not refresh a token with plenty of life left, or without a refresh token', async () => {
      const client = stomp.instances[0];
      localStorage.setItem('access_token', jwt(600));
      await client['beforeConnect']();
      localStorage.setItem('access_token', jwt(1));
      localStorage.removeItem('refresh_token');
      await client['beforeConnect']();
      expect(refresh).not.toHaveBeenCalled();
    });

    it('treats an unreadable token or one without exp as expiring', async () => {
      const client = stomp.instances[0];
      localStorage.setItem('access_token', 'garbage');
      refresh.mockReturnValue(of({ accessToken: 'x' }));
      await client['beforeConnect']();
      localStorage.setItem('access_token', jwt(null));
      await client['beforeConnect']();
      expect(refresh).toHaveBeenCalledTimes(2);
    });
  });

  describe('actions', () => {
    beforeEach(() => {
      localStorage.setItem('access_token', jwt(600));
      svc = create();
      user$.next({ id: 'u1' });
    });

    it('getNotifications asks for a page and normalises each row', () => {
      let page: { content: Array<{ type: string; adminUserId: string }>; totalElements: number } | undefined;
      svc.getNotifications(2, 5).subscribe((p) => (page = p as never));
      expect(api.get).toHaveBeenCalledWith('/v1/nms/in-app/notifications', { page: 2, size: 5 });
      expect(page?.content.every((n) => n.type === 'SYSTEM_ALERT' && n.adminUserId === 'u1')).toBe(true);
      expect(page?.totalElements).toBe(2);
    });

    it('markAsRead flags that item and lowers the unread count, never below zero', () => {
      svc.markAsRead('1');
      expect(api.patch).toHaveBeenCalledWith('/v1/nms/in-app/notifications/1/read');
      expect(svc.notifications$.value.map((n) => n.read)).toEqual([true, false]);
      expect(svc.unreadCount$.value).toBe(2);
      svc.unreadCount$.next(0);
      svc.markAsRead('2');
      expect(svc.unreadCount$.value).toBe(0);
    });

    it('markAsRead leaves state alone when the call fails', () => {
      api.patch.mockReturnValue(throwError(() => new Error('x')));
      svc.markAsRead('1');
      expect(svc.notifications$.value.every((n) => !n.read)).toBe(true);
      expect(svc.unreadCount$.value).toBe(3);
    });

    it('markAllAsRead flags everything and zeroes the count; a failure changes nothing', () => {
      api.patch.mockReturnValue(throwError(() => new Error('x')));
      svc.markAllAsRead();
      expect(svc.unreadCount$.value).toBe(3);
      api.patch.mockReturnValue(of(undefined));
      svc.markAllAsRead();
      expect(api.patch).toHaveBeenLastCalledWith('/v1/nms/in-app/notifications/read-all');
      expect(svc.notifications$.value.every((n) => n.read)).toBe(true);
      expect(svc.unreadCount$.value).toBe(0);
    });
  });
});
