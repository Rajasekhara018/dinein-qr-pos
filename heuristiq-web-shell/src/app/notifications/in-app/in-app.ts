import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { HqTimezoneService } from 'hq-shared-lib';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { InAppNotificationService } from '../../shared/services/in-app-notification-service';
import { InAppNotification, InAppNotificationType } from '../../shared/models/models';

type FilterTab = 'all' | 'unread';

@Component({
  standalone: false,
  selector: 'app-in-app',
  templateUrl: './in-app.html',
  styleUrls: ['./in-app.scss'],
})
export class InApp implements OnInit, OnDestroy {
  private readonly hqTimezone = inject(HqTimezoneService);

  notifications: InAppNotification[] = [];
  filtered: InAppNotification[] = [];
  activeTab: FilterTab = 'all';
  isLoading = false;
  hasMore = false;
  page = 0;
  readonly pageSize = 20;
  unreadCount = 0;

  private subs: Subscription[] = [];

  constructor(
    private inAppService: InAppNotificationService,
    private router: Router,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.subs.push(
      this.inAppService.unreadCount$.subscribe(c => (this.unreadCount = c)),
    );
    this.load(true);
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
  }

  load(reset = false): void {
    if (reset) {
      this.page = 0;
      this.notifications = [];
    }
    this.isLoading = true;
    this.inAppService.getNotifications(this.page, this.pageSize).subscribe({
      next: (res) => {
        this.notifications = [...this.notifications, ...res.content];
        this.hasMore = !res.last;
        this.applyFilter();
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.isLoading = false;
        this.cdr.detectChanges();
      },
    });
  }

  loadMore(): void {
    this.page++;
    this.load(false);
  }

  setTab(tab: FilterTab): void {
    this.activeTab = tab;
    this.applyFilter();
  }

  private applyFilter(): void {
    this.filtered = this.activeTab === 'unread'
      ? this.notifications.filter(n => !n.read)
      : [...this.notifications];
  }

  onItemClick(n: InAppNotification): void {
    if (!n.read) {
      this.inAppService.markAsRead(n.id);
      n = { ...n, read: true };
      this.notifications = this.notifications.map(x => x.id === n.id ? n : x);
      this.applyFilter();
      this.cdr.detectChanges();
    }
    const route = this.resolveRoute(n);
    if (route) this.router.navigate(route);
  }

  markAllRead(): void {
    this.inAppService.markAllAsRead();
    this.notifications = this.notifications.map(n => ({ ...n, read: true }));
    this.applyFilter();
    this.cdr.detectChanges();
  }

  // Mirrors the deep-link resolution in layout/header/header.ts — keep both in sync.
  private resolveRoute(n: InAppNotification): string[] | null {
    if (!n.referenceId) return null;
    const type = n.referenceType?.toUpperCase();
    if (type === 'BOOKING') return ['/my-bookings', n.referenceId];
    return null;
  }

  // Mirrors layout/header/header.ts's notificationIcon() — keep both in sync.
  notificationIcon(type: InAppNotificationType): string {
    if (!type) return 'bi-bell-fill';
    if (type.startsWith('BOOKING')) return 'bi-ticket-perforated-fill';
    if (type.startsWith('DRIVER')) return 'bi-person-fill';
    if (type.startsWith('RIDE')) return 'bi-car-front-fill';
    if (type === 'REFUND_REQUESTED') return 'bi-arrow-return-left';
    return 'bi-bell-fill';
  }

  // Mirrors layout/header/header.ts's notificationIconColor() — keep both in sync.
  notificationIconColor(type: InAppNotificationType): string {
    if (!type) return 'icon-blue';
    if (type.includes('CONFIRMED') || type.includes('COMPLETED')) return 'icon-green';
    if (type.includes('CANCELLED')) return 'icon-red';
    if (type.includes('ALERT') || type.includes('REQUESTED')) return 'icon-amber';
    return 'icon-blue';
  }

  typeLabel(type: InAppNotificationType): string {
    return type.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
  }

  formatDate(iso: string): string {
    return this.hqTimezone.formatLocale(iso, 'datetime', {
      dateStyle: 'medium',
      timeStyle: 'short',
    } as Intl.DateTimeFormatOptions, 'en-IN');
  }
}
