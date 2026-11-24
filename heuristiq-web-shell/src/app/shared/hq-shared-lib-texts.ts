import { inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import {
  HqAddressInputTexts,
  HqAmountInputTexts,
  HqCommonTableTexts,
  HqIdleTimeoutTexts,
  HqImageCropDialogTexts,
  HqLanguageSwitcherTexts,
  HqLegalProfileTexts,
  HqLookupTexts,
  HqPhoneNumberInputTexts,
  HqTableFooterTexts,
  HqUiStateTexts,
  HqUploadTexts,
  hqUploadTextsFrom,
} from 'hq-shared-lib';
import { IDLE_TIMEOUT_I18N } from './services/idle-timeout.constants';

/**
 * The words hq-shared-lib's UI components show, in the user's language. The library has no
 * translation dependency, so each `HQ_*_TEXTS` token is provided here through a factory backed by
 * TranslateService (every member is called when needed, so a language switch is picked up).
 * The confirm/prompt dialog's words live in app-dialog-texts.ts.
 */
/** Texts of the shared empty / error / loading states, confirm box and spinner. */
export function uiStateTexts(): HqUiStateTexts {
  const t = inject(TranslateService);
  return {
    empty: () => t.instant('shared.commonTable.noRecords'),
    error: () => t.instant('shared.commonTable.loadFailed'),
    errorBody: () => t.instant('shared.states.errorBody'),
    retry: () => t.instant('shared.lookup.retry'),
    loading: () => t.instant('shared.adminCrud.loading'),
    confirm: () => t.instant('shared.appDialog.defaultConfirm'),
    cancel: () => t.instant('shared.appDialog.defaultCancel'),
    reasonRequired: (field: string) => t.instant('shared.appDialog.fieldRequired', { label: field }),
    close: () => t.instant('shared.lookup.close'),
    none: () => '—',
    copy: () => t.instant('shared.states.copy'),
    copied: (label: string) => t.instant('shared.states.copied', { label }),
    copyFailed: (label: string) => t.instant('shared.states.copyFailed', { label }),
    filters: () => t.instant('shared.states.filters'),
    breadcrumbs: () => t.instant('header.breadcrumb'),
    translate: (key: string, params?: Record<string, unknown>) => t.instant(key, params),
  };
}

/** Words of the library's idle-timeout dialog, from this app's existing idleTimeout.* keys. */
export function idleTimeoutTexts(): HqIdleTimeoutTexts {
  const t = inject(TranslateService);
  return {
    warningTitle: () => t.instant(IDLE_TIMEOUT_I18N.warningTitle),
    warningMessage: (seconds: number | null) => t.instant(IDLE_TIMEOUT_I18N.warningMessage, { seconds }),
    stayActive: () => t.instant(IDLE_TIMEOUT_I18N.stayActive),
    logout: () => t.instant(IDLE_TIMEOUT_I18N.logout),
    expiredTitle: () => t.instant(IDLE_TIMEOUT_I18N.expiredTitle),
    expiredMessage: () => t.instant(IDLE_TIMEOUT_I18N.expiredMessage),
    loginAgain: () => t.instant(IDLE_TIMEOUT_I18N.loginAgain),
  };
}

export function commonTableTexts(): HqCommonTableTexts {
  const t = inject(TranslateService);
  return {
    noRecords: () => t.instant('shared.commonTable.noRecords'),
    loadFailed: () => t.instant('shared.commonTable.loadFailed'),
    actions: () => t.instant('shared.commonTable.actions'),
    yes: () => t.instant('shared.commonTable.yes'),
    no: () => t.instant('shared.commonTable.no'),
  };
}

export function lookupTexts(): HqLookupTexts {
  const t = inject(TranslateService);
  return {
    search: () => t.instant('shared.lookup.search'),
    typeToSearch: (min) => t.instant('shared.lookup.typeToSearch', { min }),
    noResults: () => t.instant('shared.lookup.noResults'),
    loadFailed: () => t.instant('shared.lookup.loadFailed'),
    retry: () => t.instant('shared.lookup.retry'),
    selected: (count) => t.instant('shared.lookup.selected', { count }),
    clearSelection: () => t.instant('shared.lookup.clearSelection'),
    cancel: () => t.instant('shared.lookup.cancel'),
    select: () => t.instant('shared.lookup.select'),
    close: () => t.instant('shared.lookup.close'),
    searchLabel: () => t.instant('shared.lookup.searchLabel'),
    searchButton: () => t.instant('shared.lookup.searchButton'),
    showingResultsFor: () => t.instant('shared.lookup.showingResultsFor'),
    clearResult: () => t.instant('shared.lookup.clearResult'),
    // The four newest words fall back to the library's English until their keys exist in a language file.
    allFields: () => lookupWord(t, 'shared.lookup.allFields', 'All fields'),
    searchIn: (field) => lookupWord(t, 'shared.lookup.searchIn', `Search in ${field}`, { field }),
    searchFields: () => lookupWord(t, 'shared.lookup.searchFields', 'Search in'),
    selectAllOnPage: () => lookupWord(t, 'shared.lookup.selectAllOnPage', 'Select all on this page'),
    // Words of the advanced conditions (only shown where a screen turns `advanced` on).
    addConditions: () => t.instant('shared.lookup.addConditions'),
    addCondition: () => t.instant('shared.lookup.addCondition'),
    advancedSearch: () => t.instant('shared.lookup.advancedSearch'),
    advancedHint: () => t.instant('shared.lookup.advancedHint'),
    normalSearch: () => t.instant('shared.lookup.normalSearch'),
    join: () => t.instant('shared.lookup.join'),
    field: () => t.instant('shared.lookup.field'),
    condition: () => t.instant('shared.lookup.condition'),
    value: () => t.instant('shared.lookup.value'),
    removeCondition: () => t.instant('shared.lookup.removeCondition'),
    equals: () => t.instant('shared.lookup.equals'),
    notEquals: () => t.instant('shared.lookup.notEquals'),
    contains: () => t.instant('shared.lookup.contains'),
    yes: () => t.instant('shared.lookup.yes'),
    no: () => t.instant('shared.lookup.no'),
  };
}

/** A translation, or the English fallback when the key is not (yet) in the language file. */
function lookupWord(t: TranslateService, key: string, fallback: string, params?: Record<string, string>): string {
  const value = t.instant(key, params);
  return value === key ? fallback : value;
}

export function tableFooterTexts(): HqTableFooterTexts {
  const t = inject(TranslateService);
  return {
    noResults: () => t.instant('shared.tableFooter.noResults'),
    showing: () => t.instant('shared.tableFooter.showing'),
    of: () => t.instant('shared.tableFooter.of'),
    rows: () => t.instant('shared.tableFooter.rows'),
    firstPage: () => t.instant('shared.tableFooter.firstPage'),
    previousPage: () => t.instant('shared.tableFooter.previousPage'),
    nextPage: () => t.instant('shared.tableFooter.nextPage'),
  };
}

export function addressInputTexts(): HqAddressInputTexts {
  const t = inject(TranslateService);
  return {
    label: () => t.instant('shared.addressInput.label'),
    linesLabel: () => t.instant('shared.addressInput.lines.label'),
    linesPlaceholder: () => t.instant('shared.addressInput.lines.placeholder'),
    landmarkLabel: () => t.instant('shared.addressInput.landmark.label'),
    landmarkPlaceholder: () => t.instant('shared.addressInput.landmark.placeholder'),
    cityLabel: () => t.instant('shared.addressInput.city.label'),
    cityPlaceholder: () => t.instant('shared.addressInput.city.placeholder'),
    stateLabel: () => t.instant('shared.addressInput.state.label'),
    statePlaceholder: () => t.instant('shared.addressInput.state.placeholder'),
    pincodeLabel: () => t.instant('shared.addressInput.pincode.label'),
    pincodePlaceholder: () => t.instant('shared.addressInput.pincode.placeholder'),
    countryLabel: () => t.instant('shared.addressInput.country.label'),
    linesRequired: () => t.instant('shared.addressInput.errors.linesRequired'),
    cityRequired: () => t.instant('shared.addressInput.errors.cityRequired'),
    stateRequired: () => t.instant('shared.addressInput.errors.stateRequired'),
    pincodeRequired: () => t.instant('shared.addressInput.errors.pincodeRequired'),
    pincodeInvalid: () => t.instant('shared.addressInput.errors.pincodeInvalid'),
  };
}

export function phoneNumberInputTexts(): HqPhoneNumberInputTexts {
  const t = inject(TranslateService);
  return {
    label: () => t.instant('shared.phoneNumberInput.label'),
    placeholder: () => t.instant('shared.phoneNumberInput.placeholder'),
    required: (label) => t.instant('shared.phoneNumberInput.required', { label }),
    invalid: () => t.instant('shared.phoneNumberInput.invalid'),
    countryLabel: () => t.instant('shared.phoneNumberInput.country'),
    searchPlaceholder: () => t.instant('shared.phoneNumberInput.searchPlaceholder'),
    noCountryFound: () => t.instant('shared.phoneNumberInput.noCountryFound'),
  };
}

export function languageSwitcherTexts(): HqLanguageSwitcherTexts {
  const t = inject(TranslateService);
  return {
    change: () => t.instant('header.language.change'),
    label: () => t.instant('header.language.label'),
  };
}

export function imageCropDialogTexts(): HqImageCropDialogTexts {
  const t = inject(TranslateService);
  return {
    eyebrow: () => t.instant('shared.imageCropDialog.eyebrow'),
    title: () => t.instant('shared.imageCropDialog.title'),
    subtitle: () => t.instant('shared.imageCropDialog.subtitle'),
    close: () => t.instant('shared.imageCropDialog.close'),
    preview: () => t.instant('shared.imageCropDialog.preview'),
    previewAlt: () => t.instant('shared.imageCropDialog.previewAlt'),
    previewNote: () => t.instant('shared.imageCropDialog.previewNote'),
    tipDrag: () => t.instant('shared.imageCropDialog.tips.drag'),
    tipCenter: () => t.instant('shared.imageCropDialog.tips.center'),
    cancel: () => t.instant('shared.imageCropDialog.cancel'),
    useImage: () => t.instant('shared.imageCropDialog.useImage'),
  };
}

/** Words of the shared amount field (label, placeholder, validation), from this app's i18n bundles. */
export function amountInputTexts(): HqAmountInputTexts {
  const t = inject(TranslateService);
  return {
    label: () => t.instant('shared.amountInput.label'),
    placeholder: () => t.instant('shared.amountInput.placeholder'),
    required: (label) => t.instant('shared.amountInput.required', { label }),
    invalid: () => t.instant('shared.amountInput.invalid'),
    min: (min) => t.instant('shared.amountInput.min', { min }),
    max: (max) => t.instant('shared.amountInput.max', { max }),
    currencyLabel: () => t.instant('shared.amountInput.currencyLabel'),
  };
}

/** Words of the shared legal-profile form (entity, tax ids, addresses), from this app's i18n bundles. */
export function legalProfileTexts(): HqLegalProfileTexts {
  const t = inject(TranslateService);
  const k = (key: string) => () => t.instant('legalProfile.' + key);
  const label = (group: string) => (code: string) => {
    const key = 'legalProfile.' + group + '.' + code;
    const text = t.instant(key);
    return text === key ? code : text;
  };
  return {
    entitySection: k('entitySection'),
    entityTypeLabel: k('entityTypeLabel'),
    entityTypeNone: k('entityTypeNone'),
    entityType: label('entityTypes'),
    legalNameLabel: k('legalNameLabel'),
    tradingNameLabel: k('tradingNameLabel'),
    registrationNumberLabel: k('registrationNumberLabel'),
    registrationCountryLabel: k('registrationCountryLabel'),
    countryNone: k('countryNone'),
    taxSection: k('taxSection'),
    taxRegisteredLabel: k('taxRegisteredLabel'),
    taxTypeLabel: k('taxTypeLabel'),
    taxType: label('taxTypes'),
    taxValueLabel: k('taxValueLabel'),
    taxExample: (example) => t.instant('legalProfile.taxExample', { example }),
    addTaxId: k('addTaxId'),
    removeTaxId: k('removeTaxId'),
    noTaxIds: k('noTaxIds'),
    addressSection: k('addressSection'),
    registeredAddress: k('registeredAddress'),
    operatingAddress: k('operatingAddress'),
    operatingDifferent: k('operatingDifferent'),
    line1Label: k('line1Label'),
    line2Label: k('line2Label'),
    cityLabel: k('cityLabel'),
    regionLabel: k('regionLabel'),
    postalCodeLabel: k('postalCodeLabel'),
    addressCountryLabel: k('addressCountryLabel'),
    noPostalCodes: k('noPostalCodes'),
    districtLabel: k('districtLabel'),
    chooseStateFirst: k('chooseStateFirst'),
    error: (key) => label('errors')(key.replace(/^legalProfile\./, '')),
  };
}

/** The words hq-shared-lib's upload components (hq-file-drop, hq-doc-upload, hq-multi-doc-upload) show, read from `shared.upload.*`. */
export function uploadTexts(): HqUploadTexts {
  const t = inject(TranslateService);
  return hqUploadTextsFrom((key, params) => t.instant(key, params), 'shared.upload');
}
