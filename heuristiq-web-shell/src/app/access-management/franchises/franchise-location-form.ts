import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HqPrivilegeAccessService } from 'hq-shared-lib';
import { Observable, of } from 'rxjs';
import { PendingChangesAware } from '../../shared/guards/pending-changes.guard';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';
import { ACCESS_MANAGEMENT_BASE_PATH } from '../admin-form-page/admin-form-page.constants';
import { FranchiseApi } from './franchise-api';
import { Location } from './franchise.models';
import {
  DEFAULT_LOCATION_COUNTRY, FRANCHISE_LOCATIONS_ROUTE, HTTP_CONFLICT, LOCATION_PRIVILEGES, LOCATION_TYPES, LOCATION_TYPE_LABEL_KEYS,
} from './franchise-locations.constants';
import { LocationForm, allowedParentTypes, emptyLocationForm, locationFormError, locationToForm, toLocationRequest } from './franchise-locations.helpers';
import { labelKey } from './franchise-earnings.helpers';
import { CountryOption, countryOptions } from './franchises.helpers';

/**
 * The page to add a place or change one, opened from the places list (its own route, like the UAM forms): the country and type are
 * fixed once a place exists, and a place's parent is chosen from a cascading picker. Saving returns to the list; leaving with
 * unsaved changes asks first.
 */
@Component({
  selector: 'app-franchise-location-form',
  standalone: false,
  templateUrl: './franchise-location-form.html',
  styleUrls: ['./franchise-form-page.scss', './franchise-location-form.scss'],
})
export class FranchiseLocationForm implements OnInit, PendingChangesAware {
  readonly privileges = LOCATION_PRIVILEGES;
  readonly types = LOCATION_TYPES;
  readonly countries: CountryOption[] = countryOptions();
  readonly allowedParentTypes = allowedParentTypes;

  form: LocationForm = emptyLocationForm(DEFAULT_LOCATION_COUNTRY);
  /** The place being changed; null while adding one. */
  editing: Location | null = null;
  editVersion: number | null = null;
  /** While changing a place: whether the parent picker is open (otherwise the parent stays as it is). */
  changingParent = false;
  isLoading = false;
  loadFailed = false;
  saving = false;
  private saved = false;
  private initial = '';

  constructor(
    private api: FranchiseApi,
    private notify: NotificationService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
    private access: HqPrivilegeAccessService,
    private router: Router,
    private route: ActivatedRoute,
    private dialogs: AppDialogService,
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      const country = this.route.snapshot.queryParamMap.get('country');
      this.form = emptyLocationForm(country || DEFAULT_LOCATION_COUNTRY);
      this.changingParent = true;
      this.initial = this.snapshot();
      return;
    }
    // The list hands the row over, so someone who may change a place but not read one still gets the page.
    const given = (history.state as { place?: Location } | null)?.place;
    if (given && given.id === id) {
      this.open(given);
      return;
    }
    this.isLoading = true;
    this.api.location(id).subscribe({
      next: (place) => { this.open(place); this.isLoading = false; this.cdr.detectChanges(); },
      error: (err: unknown) => {
        this.isLoading = false;
        this.loadFailed = true;
        this.notify.error(apiErrorMessage(err, this.translate.instant('accessManagement.franchiseLocations.errorMessage')));
        this.cdr.detectChanges();
      },
    });
  }

  private open(place: Location): void {
    this.editing = place;
    this.editVersion = place.version;
    this.changingParent = false;
    this.form = locationToForm(place);
    this.initial = this.snapshot();
  }

  get titleKey(): string {
    return this.editing ? 'accessManagement.franchiseLocations.editTitle' : 'accessManagement.franchiseLocations.newTitle';
  }

  typeLabel(type: string): string {
    const key = labelKey(LOCATION_TYPE_LABEL_KEYS, type);
    return key ? this.translate.instant(key) : type;
  }

  /** The type changed: a parent chosen for the old type may no longer be allowed, so the choice starts again. */
  onTypeChange(): void {
    this.form.parent = null;
  }

  onCountryChange(): void {
    this.form.parent = null;
  }

  onParentPicked(place: Location | null): void {
    this.form.parent = place;
  }

  save(): void {
    const error = locationFormError(this.form, this.editing !== null);
    if (error) {
      this.notify.error(this.translate.instant(error));
      return;
    }
    const request = toLocationRequest(this.form, this.editing ? this.editVersion : null);
    const editing = this.editing;
    this.saving = true;
    const call$ = editing ? this.api.changeLocation(editing.id, request) : this.api.addLocation(request);
    call$.subscribe({
      next: () => {
        this.notify.success(this.translate.instant(editing
          ? 'accessManagement.franchiseLocations.messages.changed' : 'accessManagement.franchiseLocations.messages.added'));
        this.saving = false;
        this.saved = true;
        this.back();
      },
      error: (err: { status?: number }) => {
        this.saving = false;
        // The service's own text says why (for example a duplicate place or "changed by someone else").
        this.notify.error(apiErrorMessage(err, this.translate.instant('accessManagement.franchiseLocations.messages.saveFailed')));
        if (editing && err?.status === HTTP_CONFLICT) {
          this.refreshVersion(editing);
        }
        this.cdr.detectChanges();
      },
    });
  }

  /** After a conflict the place is read again so the next save carries the version now stored (the edits in the form stay). */
  private refreshVersion(place: Location): void {
    if (!this.access.can(this.privileges.read)) {
      return;
    }
    this.api.location(place.id).subscribe({
      next: (fresh) => { this.editVersion = fresh.version; this.cdr.detectChanges(); },
      error: () => {},
    });
  }

  cancel(): void {
    this.back();
  }

  private back(): void {
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, FRANCHISE_LOCATIONS_ROUTE]);
  }

  // ── leaving with unsaved changes ──────────────────────────────────────────

  hasPendingChanges(): boolean {
    return !this.saved && !this.isLoading && this.snapshot() !== this.initial;
  }

  confirmDiscardChanges(): Observable<boolean> {
    if (!this.hasPendingChanges()) {
      return of(true);
    }
    const entity = this.translate.instant('accessManagement.franchiseLocations.entity').toLowerCase();
    return this.dialogs.confirm({
      title: this.translate.instant('shared.adminCrud.discard.title', { entity }),
      message: this.translate.instant('shared.adminCrud.discard.message', { entity }),
      confirmText: this.translate.instant('shared.adminCrud.discard.confirm'),
      cancelText: this.translate.instant('shared.adminCrud.discard.cancel'),
      variant: 'danger',
    });
  }

  private snapshot(): string {
    return JSON.stringify({ ...this.form, parent: this.form.parent?.id ?? null });
  }
}
