import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { HqSlideToggle } from './hq-ui/hq-slide-toggle/hq-slide-toggle';
import { ModuleWithProviders, NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { OverlayModule } from '@angular/cdk/overlay';
import { A11yModule } from '@angular/cdk/a11y';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterModule } from '@angular/router';
import { HQ_CONFIG, HqSharedLibConfig } from './hq-common.config';
import { HqClose } from './hq-close/hq-close';
import { HqOpenFile } from './hq-open-file/hq-open-file';
import { HqFileDrop } from './hq-file-drop/hq-file-drop';
import { HqDocUpload } from './hq-doc-upload/hq-doc-upload';
import { HqMultiDocUpload } from './hq-multi-doc-upload/hq-multi-doc-upload';
import { HqCommonModel } from './hq-common-model/hq-common-model';
import { HqHasPrivilege } from './hq-privilege/hq-has-privilege.directive';
import { HqToastHost } from './hq-ui/hq-toast-host/hq-toast-host';
import { HqAppDialog } from './hq-ui/hq-app-dialog/hq-app-dialog';
import { HqPageHeader } from './hq-ui/hq-page-header/hq-page-header';
import { HqStatCards } from './hq-ui/hq-stat-cards/hq-stat-cards';
import { HqCommonTable, HqCommonTableCell, HqCommonTableHead } from './hq-ui/hq-common-table/hq-common-table';
import { HqAddressInput } from './hq-ui/hq-address-input/hq-address-input';
import { HqPhoneNumberInput } from './hq-ui/hq-phone-number-input/hq-phone-number-input';
import { HqAmountInput } from './hq-ui/hq-amount-input/hq-amount-input';
import { HqLegalProfileForm } from './hq-ui/hq-legal-profile/hq-legal-profile-form';
import { HqMoneyPipe } from './hq-ui/hq-amount-input/hq-money.pipe';
import { HqLanguageSwitcher } from './hq-ui/hq-language-switcher/hq-language-switcher';
import { HqImageCropDialog } from './hq-ui/hq-image-crop-dialog/hq-image-crop-dialog';
import { HqTableFooter } from './hq-ui/hq-table-footer/hq-table-footer';
import { HqOnboardingForm } from './hq-ui/hq-onboarding-form/hq-onboarding-form';
import { HqSearchableOption, HqSearchableSelect } from './hq-ui/hq-searchable-select/hq-searchable-select';
import { HqField } from './hq-ui/hq-field/hq-field';
import { HqCheckbox } from './hq-ui/hq-checkbox/hq-checkbox';
import { HqButton } from './hq-ui/hq-button/hq-button';
import { HqStatusBadge } from './hq-ui/hq-status-badge/hq-status-badge';
import { HqEmptyState, HqErrorState, HqLoadingState } from './hq-ui/hq-view-states/hq-view-states';
import { HqSpinner } from './hq-ui/hq-spinner/hq-spinner';
import { HqInlineConfirm } from './hq-ui/hq-inline-confirm/hq-inline-confirm';
import { HqTab, HqTabs } from './hq-ui/hq-tabs/hq-tabs';
import { HqStepRail } from './hq-ui/hq-step-rail/hq-step-rail';
import { HqTooltip, HqTooltipPanel } from './hq-ui/hq-tooltip/hq-tooltip';
import { HqAlert } from './hq-ui/hq-alert/hq-alert';
import { HqModal } from './hq-ui/hq-modal/hq-modal';
import { HqCard, HqCardActions, HqCardFooter } from './hq-ui/hq-card/hq-card';
import { HqFormGrid } from './hq-ui/hq-form-grid/hq-form-grid';
import { HqFormActions } from './hq-ui/hq-form-actions/hq-form-actions';
import { HqFormField } from './hq-ui/hq-form-field/hq-form-field';
import { HqDrawer } from './hq-ui/hq-drawer/hq-drawer';
import { HqKeyValueList } from './hq-ui/hq-key-value-list/hq-key-value-list';
import { HqSkeleton } from './hq-ui/hq-skeleton/hq-skeleton';
import { HqFilterActions, HqFilterBar } from './hq-ui/hq-filter-bar/hq-filter-bar';
import { HqIdleTimeoutDialog } from './hq-ui/hq-idle-timeout-dialog/hq-idle-timeout-dialog';
import { HqRadio, HqRadioGroup } from './hq-ui/hq-radio-group/hq-radio-group';
import { HqDateTimeInput } from './hq-ui/hq-date-time-input/hq-date-time-input';
import { HqLookupDialog } from './hq-ui/hq-lookup-dialog/hq-lookup-dialog';
import { HqScreenHelp } from './hq-ui/hq-screen-help/hq-screen-help';
import { HqApplicationSelect } from './hq-applications/hq-application-select/hq-application-select';
import { HqDatePipe } from './hq-timezone/hq-date.pipe';
import { HqTimezoneBadge } from './hq-timezone/hq-timezone-badge';

const COMPONENTS = [
  HqSlideToggle,
  HqClose, HqOpenFile, HqFileDrop, HqDocUpload, HqMultiDocUpload, HqCommonModel, HqHasPrivilege, HqToastHost, HqAppDialog,
  HqPageHeader, HqStatCards, HqCommonTable, HqCommonTableCell, HqCommonTableHead, HqTableFooter, HqAddressInput, HqPhoneNumberInput, HqAmountInput, HqLegalProfileForm, HqMoneyPipe, HqLanguageSwitcher,
  HqImageCropDialog, HqLookupDialog, HqOnboardingForm, HqScreenHelp, HqApplicationSelect, HqSearchableSelect, HqSearchableOption, HqField, HqDateTimeInput,
  HqCheckbox, HqRadioGroup, HqRadio,
  HqButton,
  HqStatusBadge, HqEmptyState, HqErrorState, HqLoadingState, HqSpinner, HqInlineConfirm,
  HqTabs, HqTab, HqStepRail, HqTooltip, HqTooltipPanel, HqAlert, HqModal,
  HqCard, HqCardActions, HqCardFooter, HqFormGrid, HqFormActions, HqFormField,
  HqDrawer, HqKeyValueList, HqSkeleton, HqFilterBar, HqFilterActions, HqIdleTimeoutDialog,
];

/**
 * Rider reusable components -- ports of tou-angular-common's tou-doc-upload,
 * tou-multi-doc-upload, tou-common-model and tou-common-search, plus the shared UI pieces
 * (table + table footer, page header, stat cards, address / phone inputs, language switcher,
 * crop dialog, confirm / prompt / form dialog). The library has no translation dependency: each
 * component's words come from a `HQ_*_TEXTS` provider (English defaults otherwise).
 *
 * Import once with forRoot() (supplying the app's URLs and current-user lookup), then plain
 * HqSharedLibModule anywhere else the components are used.
 */
@NgModule({
  declarations: COMPONENTS,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    OverlayModule,
    A11yModule,
    MatSlideToggleModule,
    MatChipsModule,
    MatDialogModule,
    MatDividerModule,
    MatIconModule,
    MatMenuModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatTooltipModule,
    RouterModule,
    HqDatePipe,
    HqTimezoneBadge,
  ],
  // The standalone date pipe (`| hqDate`) and timezone badge come with the module so every screen that already imports
  // HqSharedLibModule (through the app's SharedModule) can use them.
  exports: [...COMPONENTS, HqDatePipe, HqTimezoneBadge],
})
export class HqSharedLibModule {
  static forRoot(config: HqSharedLibConfig): ModuleWithProviders<HqSharedLibModule> {
    return {
      ngModule: HqSharedLibModule,
      providers: [{ provide: HQ_CONFIG, useValue: config }],
    };
  }
}
