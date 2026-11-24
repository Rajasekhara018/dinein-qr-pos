import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqCommonTableColumn, HqPrivilegeAccessService } from 'hq-shared-lib';
import { Subject } from 'rxjs';
import { watchColumnsUntil } from '../../shared/table-columns.helpers';
import { NotificationService } from '../../shared/services/notification-service';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';
import {
  SETUP_CHECKLIST_APPLICATION_COLUMNS, SETUP_CHECKLIST_FRANCHISE_EDIT_PATH, SETUP_CHECKLIST_FRANCHISE_EDIT_SEGMENT, SETUP_CHECKLIST_ITEMS,
  SETUP_CHECKLIST_LINKS, SETUP_CHECKLIST_MODEL_LABEL_KEYS, SETUP_CHECKLIST_PLACELESS_COLUMNS, SETUP_CHECKLIST_PRIVILEGES,
  SETUP_CHECKLIST_STATE_LABEL_KEYS,
} from './setup-checklist.constants';
import { missingCount, setupItemState } from './setup-checklist.helpers';
import {
  SetupApplication, SetupApplicationItem, SetupChecklist, SetupCityWithoutPlace, SetupItemState, SetupPlacelessApplication,
} from './setup-checklist.models';
import { SetupChecklistApi } from './setup-checklist.service';

/**
 * What the organization still has to set up for its operating model: places, and per subscribed application a commission plan
 * and, when it works through franchises, a package template and a franchise plan. It only reports and links to the screens
 * where each item is made; nothing is created here.
 */
@Component({
  selector: 'app-setup-checklist',
  standalone: false,
  templateUrl: './setup-checklist.html',
  styleUrls: ['../franchises/franchises-list.scss', './setup-checklist.scss'],
})
export class SetupChecklistScreen implements OnInit, OnDestroy {
  private readonly api = inject(SetupChecklistApi);
  private readonly notify = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly access = inject(HqPrivilegeAccessService);

  readonly privileges = SETUP_CHECKLIST_PRIVILEGES;
  readonly links = SETUP_CHECKLIST_LINKS;
  readonly items: readonly SetupApplicationItem[] = SETUP_CHECKLIST_ITEMS;
  applicationColumns: HqCommonTableColumn<SetupApplication>[] = [];
  placelessColumns: HqCommonTableColumn<SetupPlacelessApplication>[] = [];
  private readonly destroy$ = new Subject<void>();

  checklist: SetupChecklist | null = null;
  isLoading = false;
  hasError = false;

  ngOnInit(): void {
    watchColumnsUntil(this.translate, SETUP_CHECKLIST_APPLICATION_COLUMNS, (columns) => (this.applicationColumns = columns), this.destroy$);
    watchColumnsUntil(this.translate, SETUP_CHECKLIST_PLACELESS_COLUMNS, (columns) => (this.placelessColumns = columns), this.destroy$);
    if (this.access.can(this.privileges.read)) {
      this.load();
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  load(): void {
    this.isLoading = true;
    this.hasError = false;
    this.api.checklist().subscribe({
      next: (checklist) => {
        this.checklist = checklist;
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: (err: unknown) => {
        this.checklist = null;
        this.isLoading = false;
        this.hasError = true;
        this.notify.error(apiErrorMessage(err, this.translate.instant('accessManagement.setupChecklist.errorMessage')));
        this.cdr.detectChanges();
      },
    });
  }

  /** The model's translated label, or its code when there is none. */
  modelLabel(model: string): string {
    const key = SETUP_CHECKLIST_MODEL_LABEL_KEYS[model];
    return key ? this.translate.instant(key) : model;
  }

  state(value: boolean | null | undefined): SetupItemState {
    return setupItemState(value);
  }

  stateLabelKey(value: boolean | null | undefined): string {
    return SETUP_CHECKLIST_STATE_LABEL_KEYS[setupItemState(value)];
  }

  /** How many items are still missing in the whole checklist (places count when franchises are used). */
  missing(checklist: SetupChecklist): number {
    const placeItem = checklist.franchisesUsed ? [checklist.placesLoaded] : [];
    return missingCount([...placeItem, ...checklist.applications.flatMap((app) => this.items.map((item) => app[item]))]);
  }

  /** Places are needed when franchises are used; otherwise they show as done when loaded and not needed when not. */
  placesValue(checklist: SetupChecklist): boolean | null {
    if (checklist.franchisesUsed) {
      return checklist.placesLoaded;
    }
    return checklist.placesLoaded ? true : null;
  }

  /** Screen that fixes a missing item (typed lookup for the untyped template variable of the item cell). */
  linkFor(item: SetupApplicationItem): string {
    return this.links[item];
  }

  itemValue(app: SetupApplication, item: SetupApplicationItem): boolean | null {
    return app[item];
  }

  placeless(checklist: SetupChecklist): SetupPlacelessApplication[] {
    return checklist.applicationsWithoutPlace ?? [];
  }

  citiesWithoutPlace(checklist: SetupChecklist): SetupCityWithoutPlace[] {
    return checklist.citiesWithoutPlace ?? [];
  }

  /** The franchise page where the application's place is set. */
  setPlaceLink(row: SetupPlacelessApplication): string[] {
    return [...SETUP_CHECKLIST_FRANCHISE_EDIT_PATH, row.franchiseId, SETUP_CHECKLIST_FRANCHISE_EDIT_SEGMENT];
  }
}
