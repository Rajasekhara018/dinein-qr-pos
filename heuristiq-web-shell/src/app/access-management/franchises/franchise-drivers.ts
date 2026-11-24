import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MatDialog } from '@angular/material/dialog';
import { HqCommonTableColumn, HqPrivilegeAccessService, HqStatCardItem, HqTimezoneService } from 'hq-shared-lib';
import { Subject, takeUntil } from 'rxjs';
import { LIST_PAGE_SIZE_OPTIONS } from '../../shared/list-page.constants';
import { NotificationService } from '../../shared/services/notification-service';
import { FranchiseApi } from './franchise-api';
import { FranchiseDriver, PageResult } from './franchise.models';
import { DRIVER_ONBOARDING_PATH, FRANCHISE_DRIVERS_PAGE_SIZE, FRANCHISE_DRIVERS_PRIVILEGES } from './franchise-drivers.constants';
import { driverErrorKey, driverOnboardingQuery } from './franchise-drivers.helpers';
import { dayOf } from './franchise-members.helpers';
import { isLastPage } from './franchise-earnings.helpers';
import { DriverSubscriptionDialog, DriverSubscriptionDialogData } from './driver-subscription-dialog';
import { DRIVER_SUBSCRIBE_PRIVILEGES, DRIVER_SUBSCRIPTION_DIALOG_TITLE_ID, DRIVER_SUBSCRIPTION_DIALOG_WIDTH } from './driver-subscription.constants';

/**
 * "My drivers" (route `franchises/:franchiseId/drivers`): the drivers of a franchise the signed-in person owns, a page at a
 * time, and the way into the driver onboarding form (served by the admin remote) with the organization and franchise preset.
 * Only the owner can read the list; the service answers 404 to anyone else.
 */
@Component({
  selector: 'app-franchise-drivers',
  standalone: false,
  templateUrl: './franchise-drivers.html',
  styleUrls: ['./franchises-list.scss', './franchise-drivers.scss'],
})
export class FranchiseDriversScreen implements OnInit, OnDestroy {
  readonly privileges = FRANCHISE_DRIVERS_PRIVILEGES;
  readonly pageSizeOptions = LIST_PAGE_SIZE_OPTIONS;
  pageSize = FRANCHISE_DRIVERS_PAGE_SIZE;
  columns: HqCommonTableColumn<FranchiseDriver>[] = [];

  franchiseId = '';
  /** The franchise's code: handed over by the screen that opened this one, or read from the owner's franchises. */
  franchiseCode = '';
  result: PageResult<FranchiseDriver> | null = null;
  page = 0;
  isLoading = false;
  errorMessage = '';

  private readonly destroy$ = new Subject<void>();

  constructor(
    private api: FranchiseApi,
    private notify: NotificationService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
    private access: HqPrivilegeAccessService,
    private timezone: HqTimezoneService,
    private route: ActivatedRoute,
    private router: Router,
    private dialog: MatDialog,
  ) {}

  ngOnInit(): void {
    const heading = (key: string): string => this.translate.instant(`accessManagement.franchiseDrivers.${key}`);
    this.columns = [
      { key: 'name', label: heading('name') },
      { key: 'phone', label: heading('phone') },
      { key: 'email', label: heading('email') },
      { key: 'onboardedAt', label: heading('joined') },
    ];
    this.franchiseId = this.route.snapshot.paramMap.get('franchiseId') ?? '';
    this.franchiseCode = (history.state as { code?: string } | null)?.code ?? '';
    if (!this.franchiseCode) {
      this.readFranchiseCode();
    }
    this.load();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /** The day of an instant in the reader's (user or organisation) time zone. */
  day(instant: string | null | undefined): string {
    return dayOf(instant, this.timezone.effectiveTimezone());
  }

  /** Reads one page of the franchise's drivers. */
  load(page = 0): void {
    if (!this.franchiseId || !this.access.can(this.privileges.listDrivers)) {
      this.result = null;
      return;
    }
    this.page = Math.max(page, 0);
    this.isLoading = true;
    this.errorMessage = '';
    this.api.drivers(this.franchiseId, this.page, this.pageSize).pipe(takeUntil(this.destroy$)).subscribe({
      next: (result) => {
        this.result = result;
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: (err: { status?: number }) => {
        this.result = null;
        this.isLoading = false;
        this.errorMessage = this.translate.instant(driverErrorKey(err?.status));
        this.notify.error(this.errorMessage);
        this.cdr.detectChanges();
      },
    });
  }

  goToPage(page: number): void {
    this.load(page);
  }

  onPageSizeChange(size: number): void {
    this.pageSize = size;
    this.load(0);
  }

  /** Opens the driver onboarding form of the admin remote, with this franchise (and so its organization) preset. */
  onboardDriver(): void {
    if (!this.franchiseId) {
      return;
    }
    void this.router.navigate([...DRIVER_ONBOARDING_PATH], { queryParams: driverOnboardingQuery(this.franchiseId, this.franchiseCode) });
  }

  /** The owner may subscribe their drivers to a plan (the plans open to a driver and the subscribe call). */
  get canSubscribe(): boolean {
    return this.access.can(DRIVER_SUBSCRIBE_PRIVILEGES.ownerPlans) && this.access.can(DRIVER_SUBSCRIBE_PRIVILEGES.ownerSubscribe);
  }

  /** The headline number above the table. */
  get stats(): HqStatCardItem[] {
    return [{ label: this.translate.instant('accessManagement.franchiseDrivers.statDrivers'), value: this.total, icon: 'bi-person-badge', tone: 'blue' }];
  }

  /** The first letter of a driver's name, for the avatar. */
  initial(name: string | null | undefined): string {
    return (name ?? '').trim().charAt(0).toUpperCase() || '?';
  }

  /** Opens the subscription dialog for one of the owner's drivers. */
  openSubscription(driver: FranchiseDriver): void {
    if (!this.canSubscribe || !this.franchiseId) {
      return;
    }
    this.dialog.open<DriverSubscriptionDialog, DriverSubscriptionDialogData, boolean>(DriverSubscriptionDialog, {
      data: { driverUserId: driver.userId, driverName: driver.name, franchiseId: this.franchiseId, asAdmin: false },
      width: DRIVER_SUBSCRIPTION_DIALOG_WIDTH,
      maxWidth: '94vw',
      ariaLabelledBy: DRIVER_SUBSCRIPTION_DIALOG_TITLE_ID,
      restoreFocus: true,
    });
  }

  get drivers(): FranchiseDriver[] {
    return this.result?.items ?? [];
  }

  get total(): number {
    return this.result?.totalElements ?? 0;
  }

  get isLast(): boolean {
    return isLastPage(this.page, this.pageSize, this.total);
  }

  /** The code of this franchise from the owner's own franchises, when the opening screen did not hand it over. */
  private readFranchiseCode(): void {
    if (!this.franchiseId || !this.access.can(this.privileges.listOwnFranchises)) {
      return;
    }
    this.api.mine().pipe(takeUntil(this.destroy$)).subscribe({
      next: (franchises) => {
        this.franchiseCode = franchises.find((f) => f.unitId === this.franchiseId)?.code ?? '';
        this.cdr.detectChanges();
      },
      error: () => undefined,
    });
  }
}
