import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom, Subscription } from 'rxjs';
import { AdminNotificationsApi } from '../../../core/api/admin.api';
import {
  ADMIN_TOPICS,
  NotificationView,
  StaffInfo,
  StaffNotificationMessage,
} from '../../../core/api/models';
import { AuthStore } from '../../../core/auth/auth.store';
import { RealtimeService } from '../../../core/realtime/realtime.service';
import { ToastService } from '../../../core/ui/toast.service';

/** True when a staff-topic message targets this user (by role or by user id). */
export function isNotificationForUser(
  message: StaffNotificationMessage,
  user: StaffInfo | null,
): boolean {
  if (!user || message.type !== 'NOTIFICATION') return false;
  if (message.audience === 'STAFF_ROLE') return message.recipient === user.role;
  if (message.audience === 'STAFF_USER') return message.recipient === String(user.id);
  return false;
}

/** Where clicking a notification goes (app-relative admin links only). */
export function notificationTarget(n: NotificationView): string | null {
  if (n.link?.startsWith('/admin')) return n.link;
  if (n.orderId) return `/admin/orders/${n.orderId}`;
  return null;
}

/**
 * Staff inbox for the header bell: unread count, latest notifications, mark read / read all, live updates from
 * `/topic/staff/notifications` (refetching the count on every reconnect).
 */
@Injectable({ providedIn: 'root' })
export class AdminNotificationsStore {
  private readonly api = inject(AdminNotificationsApi);
  private readonly realtime = inject(RealtimeService);
  private readonly auth = inject(AuthStore);
  private readonly toasts = inject(ToastService);
  private readonly router = inject(Router);

  private readonly _unread = signal(0);
  private readonly _items = signal<readonly NotificationView[]>([]);
  private readonly _loading = signal(false);
  private readonly _error = signal<unknown>(null);
  private readonly _loaded = signal(false);

  readonly unread = this._unread.asReadonly();
  readonly items = this._items.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();
  readonly loaded = this._loaded.asReadonly();
  readonly hasUnread = computed(() => this._unread() > 0);

  private subscription: Subscription | null = null;

  /** Starts live updates (idempotent). Call from the admin shell; `stop()` on logout/destroy. */
  start(): void {
    if (this.subscription) return;
    const restaurantId = this.auth.user()?.restaurantId;
    const sub = new Subscription();
    if (restaurantId) {
      sub.add(
        this.realtime
          .watch<StaffNotificationMessage>(ADMIN_TOPICS.staffNotifications(restaurantId))
          .subscribe((message) => this.onMessage(message)),
      );
    }
    sub.add(this.realtime.connected$.subscribe(() => void this.refreshCount()));
    this.subscription = sub;
    void this.refreshCount();
  }

  stop(): void {
    this.subscription?.unsubscribe();
    this.subscription = null;
    this._unread.set(0);
    this._items.set([]);
    this._loaded.set(false);
    this._error.set(null);
  }

  async refreshCount(): Promise<void> {
    try {
      const { count } = await firstValueFrom(this.api.unreadCount());
      this._unread.set(count);
    } catch {
      // Best effort: the bell keeps its last value.
    }
  }

  async load(): Promise<void> {
    this._loading.set(true);
    this._error.set(null);
    try {
      const page = await firstValueFrom(this.api.list({ size: 20 }));
      this._items.set(page.content);
      this._loaded.set(true);
      void this.refreshCount();
    } catch (error) {
      this._error.set(error);
    } finally {
      this._loading.set(false);
    }
  }

  async markRead(notification: NotificationView): Promise<void> {
    if (notification.read) return;
    this.setRead(notification.id, true);
    this._unread.update((n) => Math.max(0, n - 1));
    try {
      await firstValueFrom(this.api.markRead(notification.id));
    } catch {
      this.setRead(notification.id, false);
      this._unread.update((n) => n + 1);
    }
  }

  async markAllRead(): Promise<void> {
    const before = this._items();
    const unreadBefore = this._unread();
    this._items.update((list) => list.map((n) => (n.read ? n : { ...n, read: true })));
    this._unread.set(0);
    try {
      await firstValueFrom(this.api.markAllRead());
    } catch {
      this._items.set(before);
      this._unread.set(unreadBefore);
      this.toasts.error('Could not mark notifications as read.');
    }
  }

  /** Marks read and navigates to the related page. */
  async open(notification: NotificationView): Promise<void> {
    void this.markRead(notification);
    const target = notificationTarget(notification);
    if (target) await this.router.navigateByUrl(target);
  }

  private onMessage(message: StaffNotificationMessage): void {
    if (!isNotificationForUser(message, this.auth.user())) return;
    const n = message.notification;
    if (this._items().some((existing) => existing.id === n.id)) return;
    this._unread.update((count) => count + 1);
    if (this._loaded()) this._items.update((list) => [n, ...list].slice(0, 50));
    const target = notificationTarget(n);
    const show = n.severity === 'HIGH' ? this.toasts.warning : this.toasts.info;
    show.call(this.toasts, n.title, {
      key: `notification-${n.id}`,
      durationMs: n.severity === 'HIGH' ? 10_000 : 5000,
      action: target
        ? { label: 'View', run: () => void this.open({ ...n, read: false }) }
        : undefined,
    });
  }

  private setRead(id: number, read: boolean): void {
    this._items.update((list) => list.map((n) => (n.id === id ? { ...n, read } : n)));
  }
}
