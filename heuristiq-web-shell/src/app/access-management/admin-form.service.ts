import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { GoLiveGovernanceService } from '../application-readiness/go-live-governance.service';
import { UamService } from '../shared/services/uam-service';
import { AppDialogService } from '../shared/services/app-dialog-service';
import { NotificationService } from '../shared/services/notification-service';
import { ADMIN_FORM_CLIENT_SECRET_FIELD } from './admin-form.constants';
import { AdminFormConfig, AdminFormEntity, AdminFormField, AdminFormOption, AdminFormPayload } from './admin-form.types';
import { errorDetail } from './admin-form.utils';
import {
  ACCESS_MANAGEMENT_BASE_PATH, ADMIN_FORM_EDIT_SEGMENT, ADMIN_FORM_NEW_SEGMENT, ADMIN_FORM_PAGES,
} from './admin-form-page/admin-form-page.constants';

/** Options per field key, for the fields that load theirs live (entity-picker / multiselect fed by UAM). */
type FieldOptions = Record<string, AdminFormOption[]>;

/**
 * Create / edit of the config-driven UAM entities (users, groups, organizations, applications,
 * API clients, security policies, notification templates). Each list screen's "New" / "Edit" action
 * calls {@link create} / {@link edit}, which open the entity's full page (AdminFormPage); that page
 * loads its options with {@link loadOptions} and sends the result to UAM with {@link save}.
 */
@Injectable({ providedIn: 'root' })
export class AdminFormService {
  constructor(
    private router: Router,
    private uam: UamService,
    private dialogs: AppDialogService,
    private notify: NotificationService,
    private translate: TranslateService,
    private goLive: GoLiveGovernanceService,
  ) {}

  /** Opens the "New …" page of this entity. */
  create(config: AdminFormConfig): void {
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, this.pathOf(config), ADMIN_FORM_NEW_SEGMENT]);
  }

  /** Opens the "Edit …" page of the entity with this id. */
  edit(config: AdminFormConfig, id: string): void {
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, this.pathOf(config), id, ADMIN_FORM_EDIT_SEGMENT]);
  }

  /** Live options for every field that declares a loader; a failed loader leaves that field with no choices. */
  loadOptions(fields: readonly AdminFormField[]): Observable<FieldOptions> {
    const loaders: Record<string, Observable<AdminFormOption[]>> = {};
    for (const field of fields) {
      if (field.loadOptions) {
        loaders[field.key] = field.loadOptions(this.uam).pipe(catchError(() => of([] as AdminFormOption[])));
      }
    }
    return Object.keys(loaders).length ? forkJoin(loaders) : of({});
  }

  /** Sends the payload to UAM and raises the created / updated / failed toast; emits whether it was saved. */
  save(config: AdminFormConfig, id: string | null, payload: AdminFormPayload,
       onCreated?: (created: AdminFormEntity | null) => Observable<unknown>): Observable<boolean> {
    const entity = this.translate.instant(config.entityName);
    const call: Observable<AdminFormEntity | null> = id
      ? this.uam.update<AdminFormPayload, AdminFormEntity | null>(config.resourcePath, id, payload)
      : this.uam.create<AdminFormPayload, AdminFormEntity | null>(config.resourcePath, payload);
    return call.pipe(
      switchMap((res) => {
        this.notify.success(this.translate.instant(id ? 'shared.adminCrud.messages.updated' : 'shared.adminCrud.messages.created', { entity }));
        // A follow-up the form wants after a create (an organization's first administrator). Its own failure is
        // reported by the caller; the entity is already saved either way.
        if (!id && onCreated) {
          return onCreated(res).pipe(map(() => true), catchError(() => of(true)));
        }
        // Some resources (API clients) return a one-time secret on create -- surface it before it is gone.
        const secret = !id ? res?.[ADMIN_FORM_CLIENT_SECRET_FIELD] : undefined;
        if (typeof secret === 'string' && secret) {
          return this.dialogs.confirm({
            title: this.translate.instant('shared.adminCrud.clientSecret.title'),
            message: this.translate.instant('shared.adminCrud.clientSecret.message', { secret }),
            confirmText: this.translate.instant('shared.adminCrud.clientSecret.confirm'),
            cancelText: this.translate.instant('shared.adminCrud.clientSecret.cancel'),
          }).pipe(map(() => true));
        }
        return of(true);
      }),
      // A refused go-live (HTTP 409) opens the blocked dialog instead of a toast; nothing was saved either way.
      catchError((err: unknown) => this.goLive.handleRefusal(err).pipe(
        map((refusal) => {
          if (refusal === null) this.notify.error(errorDetail(err) ?? this.translate.instant('shared.adminCrud.messages.saveFailed'));
          return false;
        }),
      )),
    );
  }

  /** The route segment of an entity's pages, e.g. "organizations". */
  private pathOf(config: AdminFormConfig): string {
    return ADMIN_FORM_PAGES.find((page) => page.config === config)?.path ?? config.resourcePath.replace(/^\//, '');
  }
}
