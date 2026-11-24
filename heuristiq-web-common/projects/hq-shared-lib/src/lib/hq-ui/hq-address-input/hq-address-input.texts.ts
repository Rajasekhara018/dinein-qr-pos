import { InjectionToken } from '@angular/core';

/**
 * The words HqAddressInput shows: the block label (when the `label` input is empty), each
 * sub-field's label and placeholder, and the validation messages. The consuming app provides
 * HQ_ADDRESS_INPUT_TEXTS with its own translated text (each member is called when needed, so a
 * language switch is picked up); without it the English defaults below are used.
 */
export interface HqAddressInputTexts {
  label(): string;
  linesLabel(): string;
  linesPlaceholder(): string;
  landmarkLabel(): string;
  landmarkPlaceholder(): string;
  cityLabel(): string;
  cityPlaceholder(): string;
  stateLabel(): string;
  statePlaceholder(): string;
  pincodeLabel(): string;
  pincodePlaceholder(): string;
  countryLabel(): string;
  linesRequired(): string;
  cityRequired(): string;
  stateRequired(): string;
  pincodeRequired(): string;
  pincodeInvalid(): string;
}

export const HQ_ADDRESS_INPUT_TEXTS = new InjectionToken<HqAddressInputTexts>('HQ_ADDRESS_INPUT_TEXTS');

export const HQ_ADDRESS_INPUT_DEFAULT_TEXTS: HqAddressInputTexts = {
  label: () => 'Address',
  linesLabel: () => 'Address line',
  linesPlaceholder: () => 'House / street / area',
  landmarkLabel: () => 'Landmark (optional)',
  landmarkPlaceholder: () => 'Nearby landmark',
  cityLabel: () => 'City',
  cityPlaceholder: () => 'City',
  stateLabel: () => 'State',
  statePlaceholder: () => 'State',
  pincodeLabel: () => 'Pincode',
  pincodePlaceholder: () => '6-digit pincode',
  countryLabel: () => 'Country',
  linesRequired: () => 'Address line is required.',
  cityRequired: () => 'City is required.',
  stateRequired: () => 'State is required.',
  pincodeRequired: () => 'Pincode is required.',
  pincodeInvalid: () => 'Enter a valid 6-digit pincode.',
};
