import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { HqSharedLibConfig, HQ_CONFIG, hqUrls } from '../hq-common.config';
import { HqCommonResponse } from '../hq-common.models';
import { HqAppSetting } from '../hq-ui/hq-app-setting';
import { HQ_ACCESS_TOKEN_KEY, HQ_MY_ORGANIZATIONS_PATH } from './hq-organization-defaults.constants';
import { hqTenantIdFromToken } from './hq-organization-defaults.helpers';

/** The regional settings of one organization the account belongs to, as UAM lists them. */
export interface HqOrganizationRegional {
  tenantId: string;
  currency?: string | null;
  country?: string | null;
  timeZone?: string | null;
  defaultLanguage?: string | null;
}

/**
 * The current organization's currency and country for an app that has no organization context of its own (a federated
 * remote such as Rider_AdminWeb). The first read starts one request for the account's organizations; until it answers, and
 * when it fails, the reads give null and the app's own default is used. The organization is the access token's.
 */
@Injectable({ providedIn: 'root' })
export class HqOrganizationDefaultsService {
  private readonly http = inject(HttpClient);
  private readonly config: HqSharedLibConfig | null = inject(HQ_CONFIG, { optional: true });
  private organizations: HqOrganizationRegional[] | null = null;
  private requested = false;

  currency(): string | null {
    return this.current()?.currency || null;
  }

  country(): string | null {
    return this.current()?.country || null;
  }

  private current(): HqOrganizationRegional | null {
    this.load();
    const tenantId = hqTenantIdFromToken(this.token());
    return this.organizations?.find((o) => o.tenantId === tenantId) ?? null;
  }

  private load(): void {
    if (this.requested || !this.config || !this.token()) return;
    this.requested = true;
    this.http.get<HqCommonResponse<HqOrganizationRegional[]>>(`${hqUrls(this.config).uam}${HQ_MY_ORGANIZATIONS_PATH}`).subscribe({
      next: (res) => (this.organizations = res?.responseObject ?? []),
      error: () => (this.organizations = []),
    });
  }

  private token(): string | null {
    try {
      return localStorage.getItem(HQ_ACCESS_TOKEN_KEY);
    } catch {
      return null;
    }
  }
}

/** Provider factory: amount fields start on the current organization's currency, else `fallback`. */
export function hqOrganizationCurrency(fallback: string): () => HqAppSetting {
  return () => {
    const organizations = inject(HqOrganizationDefaultsService);
    return () => organizations.currency() ?? fallback;
  };
}

/** Provider factory: phone numbers default to the current organization's country, else `fallback`. */
export function hqOrganizationCountry(fallback: string): () => HqAppSetting {
  return () => {
    const organizations = inject(HqOrganizationDefaultsService);
    return () => organizations.country() ?? fallback;
  };
}
