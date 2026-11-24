import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { hqReadSetting } from 'hq-shared-lib';
import { environment } from '../../environments/environment';
import { organizationCountrySetting, organizationCurrencySetting } from './organization-defaults';
import { OrganizationContextService } from './services/organization-context-service';

class FakeOrganizations {
  currency: string | null = null;
  country: string | null = null;
  currentCurrency(): string | null { return this.currency; }
  currentCountry(): string | null { return this.country; }
}

function setup() {
  const organizations = new FakeOrganizations();
  TestBed.configureTestingModule({ providers: [{ provide: OrganizationContextService, useValue: organizations }] });
  const currency = TestBed.runInInjectionContext(organizationCurrencySetting);
  const country = TestBed.runInInjectionContext(organizationCountrySetting);
  return { organizations, currency, country };
}

describe('organization defaults', () => {
  it('use the deployment defaults until the organization is known', () => {
    const { currency, country } = setup();
    expect(hqReadSetting(currency, 'XXX')).toBe(environment.defaultCurrency);
    expect(hqReadSetting(country, 'XX')).toBe(environment.defaultPhoneCountry);
  });

  it("follow the current organization's currency and country, read each time", () => {
    const { organizations, currency, country } = setup();
    organizations.currency = 'AED';
    organizations.country = 'AE';
    expect(hqReadSetting(currency, 'XXX')).toBe('AED');
    expect(hqReadSetting(country, 'XX')).toBe('AE');
    organizations.currency = 'USD';
    expect(hqReadSetting(currency, 'XXX')).toBe('USD');
  });
});
