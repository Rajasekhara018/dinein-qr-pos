import { Injectable, inject } from '@angular/core';
import { HqTimezoneService } from 'hq-shared-lib';
import { BehaviorSubject, Observable, combineLatest, map, tap } from 'rxjs';
import { OrganizationChoice } from '../models/uam.models';
import { AuthService } from './auth-service';
import { UamService } from './uam-service';
import { MY_ORGANIZATIONS_PATH, VIEW_ALL_ORGANIZATIONS_STORAGE_KEY } from './organization-context.constants';
import { currentOrganization, isOrgWideFromToken, isViewingAllOrganizations, tenantIdFromToken } from './organization-context.helpers';

/**
 * Which organization the signed-in person is working in, and which others they may switch to. The session's organization
 * is the one in the access token; switching refreshes the session naming another one, then reloads so every screen
 * (org builder, chart, import, ...) starts clean in that organization.
 */
@Injectable({ providedIn: 'root' })
export class OrganizationContextService {
  private readonly hqTimezone = inject(HqTimezoneService);
  private readonly auth = inject(AuthService);
  private readonly uam = inject(UamService);
  private readonly list$ = new BehaviorSubject<OrganizationChoice[]>([]);
  private readonly tenantId$ = new BehaviorSubject<string | null>(null);

  readonly organizations$: Observable<OrganizationChoice[]> = this.list$.asObservable();
  readonly current$: Observable<OrganizationChoice | null> = combineLatest([this.list$, this.tenantId$]).pipe(
    map(([list, tenantId]) => currentOrganization(list, tenantId)),
  );
  /** The current organization's name, or null until it is known. */
  readonly currentName$: Observable<string | null> = this.current$.pipe(map((org) => org?.name ?? null));

  /**
   * True for an organization-wide caller (the token's orgWide claim), the only kind who may switch to another organization
   * or add one. Everyone else works in their account's organization and sees it, but cannot change it.
   */
  get canSwitch(): boolean {
    return isOrgWideFromToken(localStorage.getItem('access_token'));
  }

  /** Reads the account's organizations; called once the person is signed in. */
  load(): Observable<OrganizationChoice[]> {
    this.tenantId$.next(tenantIdFromToken(localStorage.getItem('access_token')));
    return this.uam.get<OrganizationChoice[]>(MY_ORGANIZATIONS_PATH).pipe(tap((list) => {
      this.list$.next(list ?? []);
      // Display timezone of the organization; only used while the opt-in flag is on (HqTimezoneService).
      this.hqTimezone.setOrganizationTimezone(currentOrganization(list ?? [], this.tenantId$.value)?.timeZone ?? null);
    }));
  }

  /** The current organization's currency (ISO 4217), or null until the list has loaded or when it has none. */
  currentCurrency(): string | null {
    return currentOrganization(this.list$.value, this.tenantId$.value)?.currency || null;
  }

  /** The current organization's country (ISO 3166 alpha-2), or null until the list has loaded or when it has none. */
  currentCountry(): string | null {
    return currentOrganization(this.list$.value, this.tenantId$.value)?.country || null;
  }

  /** The organization the session works in, read from the access token (known before the organizations list has loaded). */
  currentTenantId(): string | null {
    return tenantIdFromToken(localStorage.getItem('access_token'));
  }

  /**
   * True while an organization-wide person has chosen "All organizations": every list (read) then shows all organizations' records.
   * Changes still apply to the session's own organization.
   */
  get viewingAll(): boolean {
    return isViewingAllOrganizations(localStorage.getItem('access_token'), readViewAll());
  }

  /** Lists every organization's records (organization-wide people only), then reloads so every screen reads them again. */
  viewAll(): void {
    if (!this.canSwitch) return;
    writeViewAll(true);
    window.location.reload();
  }

  /** Works in another organization: a new session naming it, then a reload. Leaves "All organizations". */
  switchTo(organization: OrganizationChoice): void {
    if (!this.canSwitch) return;
    writeViewAll(false);
    this.auth.refresh(organization.code).subscribe({ next: () => window.location.reload() });
  }
}

function readViewAll(): string | null {
  try {
    return localStorage.getItem(VIEW_ALL_ORGANIZATIONS_STORAGE_KEY);
  } catch {
    return null;
  }
}

function writeViewAll(on: boolean): void {
  try {
    if (on) localStorage.setItem(VIEW_ALL_ORGANIZATIONS_STORAGE_KEY, 'true');
    else localStorage.removeItem(VIEW_ALL_ORGANIZATIONS_STORAGE_KEY);
  } catch {
    // Storage unavailable: the choice simply does not stick.
  }
}
