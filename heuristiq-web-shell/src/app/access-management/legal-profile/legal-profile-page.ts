import { ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { FormControl } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';
import {
  HqLegalProfile, HqLegalProfileOption, HqPrivilegeAccessService, hqEmptyLegalProfile, hqLegalProfileErrors, hqNormalizeLegalProfile,
} from 'hq-shared-lib';
import { environment } from '../../../environments/environment';
import { PendingChangesAware } from '../../shared/guards/pending-changes.guard';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { OrganizationContextService } from '../../shared/services/organization-context-service';
import { countryOptions } from '../../shared/utils/regional-names.helpers';
import { ACCESS_MANAGEMENT_BASE_PATH } from '../admin-form-page/admin-form-page.constants';
import { LEGAL_PROFILE_SUBJECTS, LegalProfileSubject, LegalProfileSubjectConfig } from './legal-profile.constants';
import { legalProfileIssueLabels, legalProfileSaveError } from './legal-profile.helpers';
import { LegalProfileLocationLookup } from './legal-profile-location-lookup';
import { LegalProfileService } from './legal-profile.service';

/**
 * The legal entity, tax ids and addresses of an organization or a franchise, on a page of its own (route data `subject`).
 * Anyone who may open it sees the profile; only a holder of the subject's update privilege can change it. The name shown in
 * the heading comes from the list that opened the page (router state), falling back to nothing.
 */
@Component({
  selector: 'app-legal-profile-page',
  standalone: false,
  templateUrl: './legal-profile-page.html',
  styleUrls: ['../franchises/franchise-form-page.scss', './legal-profile-page.scss'],
})
export class LegalProfilePage implements OnInit, PendingChangesAware {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(LegalProfileService);
  private readonly access = inject(HqPrivilegeAccessService);
  private readonly organizations = inject(OrganizationContextService);
  private readonly notify = inject(NotificationService);
  private readonly dialogs = inject(AppDialogService);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);
  /** The state and district choices of an address (franchise location data, India only for now). */
  readonly addressLookup = inject(LegalProfileLocationLookup);

  /** Inside the organization setup stepper: no heading, back link or Cancel (the stepper has them), the owner comes from `ownerIdInput`. */
  @Input() embedded = false;
  @Input() ownerIdInput = '';
  /** The profile was read (first load, or after a save): the stepper marks the step. */
  @Output() readonly profileLoaded = new EventEmitter<HqLegalProfile>();
  /** The profile was saved. */
  @Output() readonly profileSaved = new EventEmitter<HqLegalProfile>();

  subject: LegalProfileSubject = 'organization';
  config: LegalProfileSubjectConfig = LEGAL_PROFILE_SUBJECTS.organization;
  ownerId = '';
  ownerName = '';
  canEdit = false;
  countries: HqLegalProfileOption[] = [];
  defaultCountry = '';
  readonly control = new FormControl<HqLegalProfile>(hqEmptyLegalProfile(), { nonNullable: true });
  isLoading = true;
  loadFailed = false;
  saving = false;
  showAllErrors = false;
  private initial = '';
  readonly sections = [
    { id: 'entity', label: 'legalProfile.entitySection', icon: 'bi-building' },
    { id: 'tax', label: 'legalProfile.taxSection', icon: 'bi-receipt' },
    { id: 'addresses', label: 'legalProfile.addressSection', icon: 'bi-geo-alt' },
  ] as const;
  sectionIndex = 0;

  get sectionStepper(): boolean {
    return !this.embedded && this.subject !== 'organization';
  }

  goToSection(index: number): void {
    if (!this.saving && index >= 0 && index < this.sections.length) this.sectionIndex = index;
  }


  ngOnInit(): void {
    this.subject = (this.route.snapshot.data['subject'] as LegalProfileSubject | undefined) ?? 'organization';
    this.config = LEGAL_PROFILE_SUBJECTS[this.subject];
    this.ownerId = this.embedded ? this.ownerIdInput : (this.route.snapshot.paramMap.get('id') ?? '');
    this.ownerName = (history.state?.name as string | undefined) ?? '';
    this.canEdit = this.access.can(this.config.updatePrivilege);
    this.countries = countryOptions(document.documentElement.lang || 'en');
    this.defaultCountry = this.organizations.currentCountry() ?? environment.defaultPhoneCountry;
    if (!this.canEdit) this.control.disable();
    this.load();
  }

  load(): void {
    this.isLoading = true;
    this.loadFailed = false;
    this.api.get(this.subject, this.ownerId).subscribe({
      next: (profile) => this.show(profile),
      error: () => {
        this.isLoading = false;
        this.loadFailed = true;
        this.cdr.markForCheck();
      },
    });
  }

  save(): void {
    if (!this.canEdit || this.saving) return;
    if (this.control.invalid) {
      this.showAllErrors = true;
      if (this.sectionStepper) {
        const field = hqLegalProfileErrors(this.control.getRawValue())[0]?.field ?? '';
        this.sectionIndex = field.startsWith('addresses') ? 2 : field.startsWith('tax') ? 1 : 0;
      }
      this.notify.error(this.translate.instant('legalProfile.messages.fixErrors'));
      return;
    }
    this.saving = true;
    this.api.save(this.subject, this.ownerId, this.control.getRawValue()).subscribe({
      next: (saved) => {
        this.saving = false;
        this.show(saved);
        this.profileSaved.emit(this.control.getRawValue());
        this.notify.success(this.translate.instant('legalProfile.messages.saved'));
      },
      error: (err: unknown) => {
        this.saving = false;
        this.notify.error(legalProfileSaveError(err, this.translate));
        this.cdr.markForCheck();
      },
    });
  }

  back(): void {
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, this.config.backRoute]);
  }

  hasPendingChanges(): boolean {
    return this.canEdit && !this.isLoading && this.snapshot() !== this.initial;
  }

  /** The names of the fields the profile still needs filled in or corrected before it can be saved (the setup action bar lists them). */
  missingFields(): string[] {
    if (this.isLoading || this.loadFailed || !this.canEdit) return [];
    return legalProfileIssueLabels(hqLegalProfileErrors(this.control.getRawValue()), this.translate);
  }

  confirmDiscardChanges(): Observable<boolean> {
    if (!this.hasPendingChanges()) return of(true);
    const entity = this.translate.instant('legalProfile.entity').toLowerCase();
    return this.dialogs.confirm({
      title: this.translate.instant('shared.adminCrud.discard.title', { entity }),
      message: this.translate.instant('shared.adminCrud.discard.message', { entity }),
      confirmText: this.translate.instant('shared.adminCrud.discard.confirm'),
      cancelText: this.translate.instant('shared.adminCrud.discard.cancel'),
      variant: 'danger',
    });
  }

  private show(profile: HqLegalProfile | null): void {
    this.control.setValue({ ...hqEmptyLegalProfile(), ...(profile ?? {}) });
    this.initial = this.snapshot();
    this.showAllErrors = false;
    this.isLoading = false;
    this.profileLoaded.emit(this.control.getRawValue());
    this.cdr.markForCheck();
  }

  private snapshot(): string {
    return JSON.stringify(hqNormalizeLegalProfile(this.control.getRawValue()));
  }
}
