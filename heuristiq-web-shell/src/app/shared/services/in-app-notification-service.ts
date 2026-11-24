import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Subscription, firstValueFrom } from 'rxjs';
import { map } from 'rxjs/operators';
import { Client, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { ApiService } from './api-service';
import { AuthService } from './auth-service';
import { InAppNotification, InAppNotificationPage } from '../models/models';
import { environment } from '../../../environments/environment';
import { refreshAccessToken } from '../interceptors/auth-interceptor';

@Injectable({ providedIn: 'root' })
export class InAppNotificationService implements OnDestroy {

  private stompClient: Client | null = null;
  private stompSub: StompSubscription | null = null;
  private authSub: Subscription;

  readonly notifications$ = new BehaviorSubject<InAppNotification[]>([]);
  readonly unreadCount$ = new BehaviorSubject<number>(0);

  // Ringtone for incoming notifications. No audio asset exists anywhere in this app, so this
  // synthesizes a short two-tone beep via the Web Audio API instead of bundling one -- no
  // licensing concern, same reliability. Browsers block audio until a user gesture, so the
  // AudioContext is only created/resumed lazily on the first click/keydown.
  private audioCtx: AudioContext | null = null;
  private audioPrimed = false;

  // Chrome (and some Chromium forks) silently drop a pending speak() if the SpeechSynthesisUtterance
  // has no other live reference once the function that created it returns -- the speech engine
  // runs async and V8 is free to GC a purely-local utterance before it's actually spoken, so speak()
  // appears to do nothing with no error anywhere. Keeping a strong reference on the instance for the
  // duration of the utterance (cleared in its own onend/onerror) works around it.
  private pendingUtterance: SpeechSynthesisUtterance | null = null;

  constructor(
    private api: ApiService,
    private auth: AuthService,
  ) {
    this.authSub = this.auth.user$.subscribe(user => {
      if (user) {
        this.init();
      } else {
        this.disconnect();
      }
    });
    this.primeAudioOnUserGesture();
  }

  private primeAudioOnUserGesture(): void {
    const prime = () => {
      if (this.audioPrimed) return;
      this.audioPrimed = true;
      try {
        this.audioCtx = new (window.AudioContext || (window as any).webkitAudioContext)();
      } catch {
        // Web Audio unavailable -- notifications still work, just silently.
      }
      // Speech synthesis is subject to the same autoplay-style restriction in some browsers
      // (notably Chrome): the engine must be touched inside a user-gesture handler once, or
      // speak() silently does nothing later. Deliberately NOT done by speak()-ing a silent/
      // whitespace-only utterance here -- Chrome frequently never fires that utterance's onend
      // for whitespace-only text, leaving speechSynthesis.speaking stuck true forever, which
      // wedges every later real speak() call behind it (queued but never actually played).
      // cancel()+resume() touches the API inside the gesture without enqueuing anything.
      if ('speechSynthesis' in window) {
        try {
          window.speechSynthesis.cancel();
          window.speechSynthesis.resume();
        } catch {
          // Speech synthesis unavailable -- notifications still work, just silently.
        }
      }
      document.removeEventListener('click', prime);
      document.removeEventListener('keydown', prime);
    };
    document.addEventListener('click', prime);
    document.addEventListener('keydown', prime);
  }

  /** Reads a notification's title + message aloud via the browser's built-in text-to-speech,
   *  shortly after the chime so the two don't talk over each other. Silently no-ops if the
   *  Web Speech API isn't available (older browsers) -- this is a UX enhancement, not required
   *  for the notification to otherwise be delivered/visible. */
  private speak(title: string, message: string): void {
    if (!('speechSynthesis' in window)) return;
    const text = [title, message].filter(Boolean).join('. ');
    if (!text.trim()) return;
    setTimeout(() => {
      try {
        // Defensive: clears anything wedged in the queue from a prior stuck utterance (see the
        // priming comment above) so this notification isn't silently queued behind a dead one.
        window.speechSynthesis.cancel();
        const utterance = new SpeechSynthesisUtterance(text);
        utterance.rate = 1;
        utterance.volume = 1;
        utterance.onend = () => { this.pendingUtterance = null; };
        utterance.onerror = () => { this.pendingUtterance = null; };
        this.pendingUtterance = utterance;
        window.speechSynthesis.speak(utterance);
      } catch {
        // Best-effort only.
      }
    }, 300);
  }

  private playChime(): void {
    const ctx = this.audioCtx;
    if (!ctx) return;
    if (ctx.state === 'suspended') ctx.resume().catch(() => {});
    [880, 1175].forEach((frequency, i) => {
      const oscillator = ctx.createOscillator();
      const gain = ctx.createGain();
      oscillator.type = 'sine';
      oscillator.frequency.value = frequency;
      const startAt = ctx.currentTime + i * 0.15;
      gain.gain.setValueAtTime(0.0001, startAt);
      gain.gain.exponentialRampToValueAtTime(0.2, startAt + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.0001, startAt + 0.18);
      oscillator.connect(gain);
      gain.connect(ctx.destination);
      oscillator.start(startAt);
      oscillator.stop(startAt + 0.2);
    });
  }

  private init(): void {
    const token = localStorage.getItem('access_token');
    if (!token) return;
    this.loadUnreadCount();
    this.loadRecent();
    this.connect(token);
  }

  private connect(token: string): void {
    if (this.stompClient?.active) return;

    this.stompClient = new Client({
      // SockJS can't send an Authorization header on its /info + transport requests, so the
      // gateway accepts the token as ?access_token= on this path only; re-read on each
      // (re)connect so a refreshed token is picked up.
      // The socket never passes through the HTTP interceptor, so nothing refreshes its token:
      // an expired one made every reconnect 401 at the gateway. Refresh it first (sharing the
      // interceptor's in-flight refresh) and send the current token on the STOMP CONNECT too.
      beforeConnect: async () => {
        const current = localStorage.getItem('access_token');
        if (current && this.expiresWithin(current, 30) && localStorage.getItem('refresh_token')) {
          try {
            await firstValueFrom(refreshAccessToken(this.auth));
          } catch {
            // Refresh failed: the next REST call's 401 handling logs the user out.
          }
        }
        const fresh = localStorage.getItem('access_token') ?? token;
        this.stompClient!.connectHeaders = { Authorization: `Bearer ${fresh}` };
      },
      webSocketFactory: () => {
        const current = localStorage.getItem('access_token') ?? token;
        // No iframe-based fallbacks: NMS disables them (allowed-origin patterns are set) and the
        // gateway's X-Frame-Options: DENY blocks them anyway, so trying them only adds 404s.
        return new SockJS(`${environment.wsUrl}?access_token=${encodeURIComponent(current)}`, undefined, {
          transports: ['websocket', 'xhr-streaming', 'xhr-polling'],
        }) as unknown as WebSocket;
      },
      reconnectDelay: 5000,
      onConnect: () => {
        this.stompSub = this.stompClient!.subscribe(
          '/user/queue/notifications',
          (msg) => {
            const n = this.toAppNotification(JSON.parse(msg.body));
            this.notifications$.next([n, ...this.notifications$.value]);
            this.unreadCount$.next(this.unreadCount$.value + 1);
            this.playChime();
            this.speak(n.title, n.message);
          },
        );
      },
    });

    this.stompClient.activate();
  }

  /** True when the JWT's exp claim is within {@code seconds} from now (or unreadable). */
  private expiresWithin(token: string, seconds: number): boolean {
    try {
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      return typeof payload.exp !== 'number' || payload.exp * 1000 - Date.now() < seconds * 1000;
    } catch {
      return true;
    }
  }

  private disconnect(): void {
    this.stompSub?.unsubscribe();
    this.stompClient?.deactivate();
    this.stompClient = null;
    this.stompSub = null;
    this.notifications$.next([]);
    this.unreadCount$.next(0);
  }

  // NMS's InAppNotification has no adminUserId/type — it's a generic, event-driven backend
  // shared across all callers, not admin-scoped. Normalize its shape into what the rest of
  // this app (header bell, /notifications page) already expects from AdminWeb's richer model.
  private toAppNotification(raw: { id: string; userId: string; title: string; message: string; read: boolean; createdAt: string }): InAppNotification {
    return {
      id: raw.id,
      adminUserId: raw.userId,
      title: raw.title,
      message: raw.message,
      type: 'SYSTEM_ALERT',
      read: raw.read,
      createdAt: raw.createdAt,
    };
  }

  private loadRecent(): void {
    this.api.get<InAppNotificationPage>('/v1/nms/in-app/notifications', { page: 0, size: 10 })
      .subscribe({ next: (p) => this.notifications$.next(p.content.map(n => this.toAppNotification(n as any))), error: () => {} });
  }

  loadUnreadCount(): void {
    this.api.get<{ unreadCount: number }>('/v1/nms/in-app/notifications/unread-count')
      .subscribe({ next: (r) => this.unreadCount$.next(r.unreadCount), error: () => {} });
  }

  // Normalizes content through toAppNotification() the same as loadRecent() -- NMS's raw shape
  // has no `type` field, and the inbox/header templates call .includes() on n.type with no null
  // guard, so un-normalized raw rows crash the page as soon as they render.
  getNotifications(page: number, size: number) {
    return this.api.get<InAppNotificationPage>('/v1/nms/in-app/notifications', { page, size })
      .pipe(map((p) => ({ ...p, content: p.content.map((n) => this.toAppNotification(n as any)) })));
  }

  markAsRead(id: string): void {
    this.api.patch<void>(`/v1/nms/in-app/notifications/${id}/read`).subscribe({
      next: () => {
        const updated = this.notifications$.value.map(n => n.id === id ? { ...n, read: true } : n);
        this.notifications$.next(updated);
        this.unreadCount$.next(Math.max(0, this.unreadCount$.value - 1));
      },
      error: () => {},
    });
  }

  markAllAsRead(): void {
    this.api.patch<void>('/v1/nms/in-app/notifications/read-all').subscribe({
      next: () => {
        this.notifications$.next(this.notifications$.value.map(n => ({ ...n, read: true })));
        this.unreadCount$.next(0);
      },
      error: () => {},
    });
  }

  ngOnDestroy(): void {
    this.authSub.unsubscribe();
    this.disconnect();
  }
}
