import {
  DestroyRef,
  DOCUMENT,
  inject,
  Injectable,
  InjectionToken,
  OnDestroy,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ReconnectionTimeMode } from '@stomp/stompjs';
import { RxStomp, RxStompState } from '@stomp/rx-stomp';
import { defer, filter, finalize, map, Observable, share, skip, Subject, Subscription } from 'rxjs';

export type ConnectionState = 'connecting' | 'connected' | 'reconnecting' | 'disconnected';

/** Returns the value for the STOMP `Authorization` CONNECT header (e.g. `Bearer dvc_…`), or null for none. */
export type AuthHeaderProvider = () => string | null;

export interface RealtimeConfig {
  /** Absolute ws(s) URL. Default: same origin `/ws`. */
  url?: string;
  heartbeatMs: number;
  initialReconnectDelayMs: number;
  maxReconnectDelayMs: number;
}

export const REALTIME_CONFIG = new InjectionToken<RealtimeConfig>('REALTIME_CONFIG', {
  providedIn: 'root',
  factory: () => ({
    heartbeatMs: 10_000,
    initialReconnectDelayMs: 1_000,
    maxReconnectDelayMs: 30_000,
  }),
});

/** Factory seam so tests can supply a fake client. */
export const RX_STOMP_FACTORY = new InjectionToken<() => RxStomp>('RX_STOMP_FACTORY', {
  providedIn: 'root',
  factory: () => () => new RxStomp(),
});

/**
 * STOMP-over-WebSocket client (`/ws`). WebSocket is only a notification channel — features must REFETCH REST state
 * whenever {@link connected$} emits (first connect and every reconnect).
 *
 * - Lazy: no socket is opened until a feature calls {@link watch} (or {@link connect}).
 * - Auto-reconnects with exponential backoff (1s → 30s cap); 10s heart-beats both ways.
 * - Guests: no header — the HttpOnly guest cookie rides along on the handshake.
 *   Staff: call {@link setAuthProvider} with a function returning `Bearer <jwt|device token>`; it is re-evaluated
 *   before every (re)connect, so refreshed tokens are picked up.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService implements OnDestroy {
  private readonly document = inject(DOCUMENT);
  private readonly config = inject(REALTIME_CONFIG);
  private readonly createClient = inject(RX_STOMP_FACTORY);

  private client: RxStomp | null = null;
  private authProvider: AuthHeaderProvider | null = null;
  private everConnected = false;
  private readonly stateSub = new Subscription();

  private readonly _connectionState = signal<ConnectionState>('disconnected');
  /** `connecting` (first attempt) · `connected` · `reconnecting` (lost, retrying) · `disconnected` (inactive). */
  readonly connectionState = this._connectionState.asReadonly();

  private readonly connectedSubject = new Subject<void>();
  /** Emits on every successful connection (first and re-connections). Refetch REST state here. */
  readonly connected$: Observable<void> = this.connectedSubject.asObservable();
  /** Emits on re-connections only (skips the first connection). */
  readonly reconnected$: Observable<void> = this.connected$.pipe(skip(1));

  private readonly topics = new Map<string, Observable<unknown>>();

  /** Sets (or clears) the CONNECT `Authorization` header source; reconnects if already active. */
  setAuthProvider(provider: AuthHeaderProvider | null): void {
    const changed = this.authProvider !== provider;
    this.authProvider = provider;
    if (changed && this.client?.active) {
      void this.reconnect();
    }
  }

  /** Opens the connection if not already active. Normally implicit via {@link watch}. */
  connect(): void {
    const client = this.ensureClient();
    if (!client.active) {
      this._connectionState.set(this.everConnected ? 'reconnecting' : 'connecting');
      client.activate();
    }
  }

  /** Closes the socket and stops reconnecting (e.g. on logout). */
  async disconnect(): Promise<void> {
    const client = this.client;
    if (!client) return;
    await client.deactivate();
    this.everConnected = false;
    this._connectionState.set('disconnected');
  }

  /** Forces a fresh connection (e.g. after the auth identity changed). */
  async reconnect(): Promise<void> {
    await this.client?.deactivate();
    this.connect();
  }

  /**
   * Subscribes to a topic and parses JSON bodies. Shared per topic; the server subscription is re-established
   * automatically after reconnects. Activates the connection on first use.
   */
  watch<T>(topic: string): Observable<T> {
    let shared = this.topics.get(topic) as Observable<T> | undefined;
    if (!shared) {
      shared = defer(() => {
        this.connect();
        return this.ensureClient().watch({ destination: topic });
      }).pipe(
        map((message) => parseBody<T>(message.body)),
        filter((body): body is T => body !== undefined),
        finalize(() => this.topics.delete(topic)),
        share(),
      );
      this.topics.set(topic, shared);
    }
    return shared;
  }

  /**
   * Runs `callback` on every (re)connection until `destroyRef` is destroyed. Call from an injection context or pass
   * a DestroyRef explicitly.
   */
  onConnected(callback: () => void, destroyRef: DestroyRef = inject(DestroyRef)): void {
    this.connected$.pipe(takeUntilDestroyed(destroyRef)).subscribe(callback);
  }

  ngOnDestroy(): void {
    this.stateSub.unsubscribe();
    void this.client?.deactivate();
  }

  private ensureClient(): RxStomp {
    if (this.client) return this.client;
    const client = this.createClient();
    client.configure({
      brokerURL: this.config.url ?? this.defaultUrl(),
      heartbeatIncoming: this.config.heartbeatMs,
      heartbeatOutgoing: this.config.heartbeatMs,
      reconnectDelay: this.config.initialReconnectDelayMs,
      maxReconnectDelay: this.config.maxReconnectDelayMs,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      connectionTimeout: 10_000,
      beforeConnect: (stomp) => {
        const header = this.authProvider?.() ?? null;
        stomp.configure({ connectHeaders: header ? { Authorization: header } : {} });
      },
    });
    this.stateSub.add(client.connectionState$.subscribe((state) => this.onState(client, state)));
    this.stateSub.add(
      client.connected$.subscribe(() => {
        this.everConnected = true;
        this.connectedSubject.next();
      }),
    );
    this.client = client;
    return client;
  }

  private onState(client: RxStomp, state: RxStompState): void {
    switch (state) {
      case RxStompState.OPEN:
        this._connectionState.set('connected');
        break;
      case RxStompState.CONNECTING:
        this._connectionState.set(this.everConnected ? 'reconnecting' : 'connecting');
        break;
      case RxStompState.CLOSING:
      case RxStompState.CLOSED:
        if (!client.active) {
          this._connectionState.set('disconnected');
        } else {
          this._connectionState.set(this.everConnected ? 'reconnecting' : 'connecting');
        }
        break;
    }
  }

  private defaultUrl(): string {
    const location = this.document.location;
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
    return `${scheme}://${location.host}/ws`;
  }
}

function parseBody<T>(body: string): T | undefined {
  try {
    return JSON.parse(body) as T;
  } catch {
    return undefined;
  }
}
