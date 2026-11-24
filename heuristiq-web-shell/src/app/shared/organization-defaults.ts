import { inject } from '@angular/core';
import { HqAppSetting } from 'hq-shared-lib';
import { environment } from '../../environments/environment';
import { OrganizationContextService } from './services/organization-context-service';

/** Amount fields start on the current organization's currency, or this deployment's default until it is known. */
export function organizationCurrencySetting(): HqAppSetting {
  const organizations = inject(OrganizationContextService);
  return () => organizations.currentCurrency() ?? environment.defaultCurrency;
}

/** Phone numbers default to the current organization's country, or this deployment's default until it is known. */
export function organizationCountrySetting(): HqAppSetting {
  const organizations = inject(OrganizationContextService);
  return () => organizations.currentCountry() ?? environment.defaultPhoneCountry;
}
