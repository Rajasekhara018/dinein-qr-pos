import { InjectionToken } from '@angular/core';

/**
 * The words HqLegalProfileForm shows. The consuming app provides HQ_LEGAL_PROFILE_TEXTS with its translated text (each
 * member is called when needed, so a language switch is picked up); without it the English defaults below are used.
 * `entityType`, `taxType` and `error` receive the code or message key and return its label (the code itself when unknown).
 */
export interface HqLegalProfileTexts {
  entitySection(): string;
  entityTypeLabel(): string;
  entityTypeNone(): string;
  entityType(code: string): string;
  legalNameLabel(): string;
  tradingNameLabel(): string;
  registrationNumberLabel(): string;
  registrationCountryLabel(): string;
  countryNone(): string;
  taxSection(): string;
  taxRegisteredLabel(): string;
  taxTypeLabel(): string;
  taxType(code: string): string;
  taxValueLabel(): string;
  taxExample(example: string): string;
  addTaxId(): string;
  removeTaxId(): string;
  noTaxIds(): string;
  addressSection(): string;
  registeredAddress(): string;
  operatingAddress(): string;
  operatingDifferent(): string;
  line1Label(): string;
  line2Label(): string;
  cityLabel(): string;
  regionLabel(): string;
  postalCodeLabel(): string;
  addressCountryLabel(): string;
  noPostalCodes(): string;
  /** The label of the district picker (a district or city), when states and districts come from a list. */
  districtLabel?(): string;
  /** Shown under the district picker until a state is chosen. */
  chooseStateFirst?(): string;
  error(key: string): string;
}

export const HQ_LEGAL_PROFILE_TEXTS = new InjectionToken<HqLegalProfileTexts>('HQ_LEGAL_PROFILE_TEXTS');

export const HQ_LEGAL_PROFILE_DEFAULT_TEXTS: HqLegalProfileTexts = {
  entitySection: () => 'Legal entity',
  entityTypeLabel: () => 'Type of entity',
  entityTypeNone: () => '— Choose —',
  entityType: (code) => code,
  legalNameLabel: () => 'Legal name',
  tradingNameLabel: () => 'Trading name',
  registrationNumberLabel: () => 'Registration number',
  registrationCountryLabel: () => 'Country of registration',
  countryNone: () => '— Choose a country —',
  taxSection: () => 'Tax',
  taxRegisteredLabel: () => 'Registered for tax (GST / VAT)',
  taxTypeLabel: () => 'Tax id type',
  taxType: (code) => code,
  taxValueLabel: () => 'Number',
  taxExample: (example) => `For example ${example}`,
  addTaxId: () => 'Add a tax id',
  removeTaxId: () => 'Remove',
  noTaxIds: () => 'No tax ids yet.',
  addressSection: () => 'Addresses',
  registeredAddress: () => 'Registered address',
  operatingAddress: () => 'Operating address',
  operatingDifferent: () => 'It works from a different address',
  line1Label: () => 'Address line 1',
  line2Label: () => 'Address line 2 (optional)',
  cityLabel: () => 'City',
  regionLabel: () => 'State / region',
  postalCodeLabel: () => 'Postal code',
  addressCountryLabel: () => 'Country',
  noPostalCodes: () => 'This country does not use postal codes.',
  districtLabel: () => 'City / district',
  chooseStateFirst: () => 'Choose the state first.',
  error: (key) => key,
};
