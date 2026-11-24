import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { BehaviorSubject, EMPTY, Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { InAppNotification, InAppNotificationPage, InAppNotificationType } from '../../shared/models/models';
import { InAppNotificationService } from '../../shared/services/in-app-notification-service';
import { InApp } from './in-app';

describe('InApp notifications screen', () => {
  const unread$ = new BehaviorSubject<number>(2);
  const service = { unreadCount$: unread$, getNotifications: vi.fn(), markAsRead: vi.fn(), markAllAsRead: vi.fn() };
  const router = { navigate: vi.fn() };
  let granted: Set<string> | null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };
  let fixture: ComponentFixture<InApp>;

  const note = (id: string, over: Partial<InAppNotification> = {}): InAppNotification => ({
    id,
    adminUserId: 'u1',
    title: `Title ${id}`,
    message: `Message ${id}`,
    type: 'BOOKING_CONFIRMED' as InAppNotificationType,
    read: false,
    createdAt: '2026-03-04T10:00:00Z',
    ...over,
  });
  const page = (content: InAppNotification[], last = true): InAppNotificationPage => ({ content, totalElements: content.length, totalPages: 1, number: 0, size: 20, last });

  function create(): InApp {
    TestBed.configureTestingModule({
      declarations: [InApp],
      imports: [TranslateModule.forRoot(), HqSharedLibModule],
      providers: [
        { provide: InAppNotificationService, useValue: service },
        { provide: Router, useValue: router },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
    fixture = TestBed.createComponent(InApp);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  function render(): void {
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
  }

  const text = (): string => fixture.nativeElement.textContent as string;
  const cards = (): HTMLElement[] => Array.from(fixture.nativeElement.querySelectorAll('.inapp-card'));
  const markAllButton = (): HTMLButtonElement | null => fixture.nativeElement.querySelector('.fn-secondary-btn .bi-check2-all')?.closest('button') ?? null;

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    unread$.next(2);
    service.getNotifications.mockReturnValue(of(page([note('n1'), note('n2', { read: true, type: 'RIDE_ALERT' as InAppNotificationType, referenceId: 'abcdefghijkl', referenceType: 'RIDE' })])));
  });

  it('loads the first page and renders each notification with its type label, unread marker and counts', () => {
    const component = create();

    expect(service.getNotifications).toHaveBeenCalledWith(0, 20);
    expect(component.notifications.length).toBe(2);
    expect(cards().length).toBe(2);
    expect(text()).toContain('Title n1');
    expect(text()).toContain('Message n2');
    expect(text()).toContain('Booking Confirmed');
    expect(text()).toContain('RIDE · abcdefgh');
    expect(cards()[0].classList).toContain('inapp-card--unread');
    expect(cards()[1].classList).not.toContain('inapp-card--unread');
    expect(fixture.nativeElement.querySelectorAll('.status-pill--new').length).toBe(1);
    expect(fixture.nativeElement.querySelector('.tab-count--warn').textContent).toContain('2');
  });

  it('shows a skeleton while the first page is loading', () => {
    service.getNotifications.mockReturnValue(new Subject());
    const component = create();

    expect(component.isLoading).toBe(true);
    expect(fixture.nativeElement.querySelectorAll('.inapp-skeleton-card').length).toBe(5);
  });

  it('stops loading and keeps the list empty when the page cannot be read', () => {
    service.getNotifications.mockReturnValue(throwError(() => new Error('down')));
    const component = create();

    expect(component.isLoading).toBe(false);
    expect(component.notifications).toEqual([]);
    expect(text()).toContain('notifications.empty.allTitle');
  });

  it('filters to unread and explains an empty unread tab', () => {
    const component = create();

    component.setTab('unread');
    render();
    expect(cards().length).toBe(1);
    expect(text()).toContain('Title n1');

    service.getNotifications.mockReturnValue(of(page([note('n9', { read: true })])));
    component.load(true);
    render();
    expect(cards().length).toBe(0);
    expect(text()).toContain('notifications.empty.unreadTitle');
    expect(text()).toContain('notifications.empty.unreadMessage');

    component.setTab('all');
    render();
    expect(cards().length).toBe(1);
  });

  it('appends the next page on "load more" and hides the button after the last page', () => {
    service.getNotifications.mockReturnValueOnce(of(page([note('n1')], false)));
    const component = create();
    const loadMore = (): HTMLButtonElement | null => fixture.nativeElement.querySelector('.text-center button');
    expect(loadMore()).not.toBeNull();

    service.getNotifications.mockReturnValueOnce(of(page([note('n2')], true)));
    loadMore()?.click();
    render();

    expect(service.getNotifications).toHaveBeenLastCalledWith(1, 20);
    expect(component.notifications.map((n) => n.id)).toEqual(['n1', 'n2']);
    expect(loadMore()).toBeNull();
  });

  it('reload resets to the first page', () => {
    service.getNotifications.mockReturnValueOnce(of(page([note('n1')], false)));
    const component = create();
    component.loadMore();
    expect(component.page).toBe(1);

    service.getNotifications.mockReturnValueOnce(of(page([note('fresh')])));
    component.load(true);

    expect(component.page).toBe(0);
    expect(component.notifications.map((n) => n.id)).toEqual(['fresh']);
  });

  it('marks an unread notification read when opened and goes to the booking it refers to', () => {
    service.getNotifications.mockReturnValue(of(page([note('n1', { referenceId: 'b-42', referenceType: 'booking' })])));
    const component = create();

    cards()[0].click();
    render();

    expect(service.markAsRead).toHaveBeenCalledWith('n1');
    expect(component.notifications[0].read).toBe(true);
    expect(router.navigate).toHaveBeenCalledWith(['/my-bookings', 'b-42']);
    expect(cards()[0].classList).not.toContain('inapp-card--unread');
  });

  it('does not mark an already-read notification again, nor navigate when it refers to nothing known', () => {
    create();

    cards()[1].click();
    expect(service.markAsRead).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('does not navigate for an unread notification without a reference', () => {
    const component = create();

    component.onItemClick(component.notifications[0]);

    expect(service.markAsRead).toHaveBeenCalledWith('n1');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('marks every notification read from the privileged button', () => {
    const component = create();

    markAllButton()?.click();
    render();

    expect(service.markAllAsRead).toHaveBeenCalledTimes(1);
    expect(component.notifications.every((n) => n.read)).toBe(true);
  });

  it('disables "mark all read" when nothing is unread', () => {
    create();
    unread$.next(0);
    render();

    expect(markAllButton()?.disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('.tab-count--warn')).toBeNull();
  });

  it.each([
    [['NMS_IN_APP_NOTIFICATION_MARK_ALL_READ'], true],
    [['NMS_IN_APP_ACCESS'], true],
    [['NMS_MANAGE'], true],
    [['NMS_IN_APP_NOTIFICATION_LIST'], false],
    [[], false],
  ])('shows "mark all read" for privileges %j: %s', (codes, visible) => {
    granted = new Set(codes);
    create();

    expect(markAllButton() !== null).toBe(visible);
  });

  it('follows the live unread counter and stops following once destroyed', () => {
    const component = create();

    unread$.next(7);
    expect(component.unreadCount).toBe(7);

    fixture.destroy();
    unread$.next(9);
    expect(component.unreadCount).toBe(7);
  });

  it('picks an icon and colour per notification type', () => {
    const component = create();
    const t = (v: string) => v as InAppNotificationType;

    expect(component.notificationIcon(t(''))).toBe('bi-bell-fill');
    expect(component.notificationIcon(t('BOOKING_CANCELLED'))).toBe('bi-ticket-perforated-fill');
    expect(component.notificationIcon(t('DRIVER_ASSIGNED'))).toBe('bi-person-fill');
    expect(component.notificationIcon(t('RIDE_STARTED'))).toBe('bi-car-front-fill');
    expect(component.notificationIcon(t('REFUND_REQUESTED'))).toBe('bi-arrow-return-left');
    expect(component.notificationIcon(t('SOMETHING'))).toBe('bi-bell-fill');

    expect(component.notificationIconColor(t(''))).toBe('icon-blue');
    expect(component.notificationIconColor(t('BOOKING_CONFIRMED'))).toBe('icon-green');
    expect(component.notificationIconColor(t('RIDE_COMPLETED'))).toBe('icon-green');
    expect(component.notificationIconColor(t('BOOKING_CANCELLED'))).toBe('icon-red');
    expect(component.notificationIconColor(t('SYSTEM_ALERT'))).toBe('icon-amber');
    expect(component.notificationIconColor(t('REFUND_REQUESTED'))).toBe('icon-amber');
    expect(component.notificationIconColor(t('OTHER'))).toBe('icon-blue');
  });

  it('turns a type code into a readable label', () => {
    const component = create();

    expect(component.typeLabel('DRIVER_ASSIGNED' as InAppNotificationType)).toBe('Driver Assigned');
  });

  it('formats the creation time in the Indian locale', () => {
    const component = create();

    const label = component.formatDate('2026-03-04T10:00:00Z');

    expect(label).toContain('2026');
    expect(label).toContain('Mar');
  });
});
