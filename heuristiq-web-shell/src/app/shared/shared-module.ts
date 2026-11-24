import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';
import { ImageCropperComponent } from 'ngx-image-cropper';
import { AppAreaLogo } from './app-area-logo/app-area-logo';
import {
  HQ_ADDRESS_INPUT_TEXTS,
  HQ_APP_DIALOG_TEXTS,
  HQ_COMMON_TABLE_TEXTS,
  HQ_UI_STATE_TEXTS,
  HQ_IDLE_TIMEOUT_TEXTS,
  HQ_IMAGE_CROP_DIALOG_TEXTS,
  HQ_IMAGE_CROPPER,
  HQ_IMAGE_CROPPER_COMPONENT,
  HQ_LANGUAGE_PROVIDER,
  HQ_LANGUAGE_SWITCHER_TEXTS,
  HQ_LOOKUP_TEXTS,
  HQ_PHONE_DEFAULT_COUNTRY,
  HQ_PHONE_NUMBER_INPUT_TEXTS,
  HQ_AMOUNT_DEFAULT_CURRENCY,
  HQ_LEGAL_PROFILE_TEXTS,
  HQ_AMOUNT_INPUT_TEXTS,
  HQ_FIELD_TEXTS,
  HQ_SCREEN_HELP_TEXTS,
  HQ_TABLE_FOOTER_TEXTS,
  HQ_UPLOAD_TEXTS,
  HqImageCropDialog,
  HqImageCropDialogData,
  HqImageCropDialogResult,
  HqImageCropper,
  HqSharedLibModule,
} from 'hq-shared-lib';
import { TranslateModule } from '@ngx-translate/core';
import { environment } from '../../environments/environment';
import { MaterialModule } from './material-module';
import { LanguageService } from './services/language-service';
import { appDialogTexts } from './app-dialog-texts';
import { screenHelpTexts } from './screen-help-texts';
import { fieldTexts } from './field-texts';
import { organizationCountrySetting, organizationCurrencySetting } from './organization-defaults';
import {
  addressInputTexts,
  commonTableTexts,
  uiStateTexts,
  idleTimeoutTexts,
  imageCropDialogTexts,
  languageSwitcherTexts,
  lookupTexts,
  phoneNumberInputTexts,
  amountInputTexts,
  legalProfileTexts,
  tableFooterTexts,
  uploadTexts,
} from './hq-shared-lib-texts';

/** Signed-in user's id from the session AuthService persists -- default DMS ownerId. */
function storedUserId(): string | null {
  try {
    return JSON.parse(localStorage.getItem('user_data') || 'null')?.id ?? null;
  } catch {
    return null;
  }
}

/** hq-doc-upload's optional crop step, backed by the library's crop dialog (which renders this app's ngx-image-cropper). */
function imageCropperFactory(dialog: MatDialog): HqImageCropper {
  return (event, file, aspectRatio) =>
    firstValueFrom(
      dialog.open<HqImageCropDialog, HqImageCropDialogData, HqImageCropDialogResult | null>(HqImageCropDialog, {
        width: 'min(900px, 94vw)',
        maxWidth: '94vw',
        autoFocus: false,
        panelClass: 'doc-crop-dialog-panel',
        backdropClass: 'doc-crop-dialog-backdrop',
        data: { imageChangedEvent: event, fileName: file.name, aspectRatio },
      }).afterClosed(),
    ).then((result) => result?.finalImage ?? null);
}

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    RouterModule,
    MaterialModule,
    TranslateModule,
    HqSharedLibModule.forRoot({
      gatewayUrl: environment.gatewayUrl,
      adminApiUrl: environment.adminApiUrl,
      currentUserId: storedUserId,
    }),
    AppAreaLogo,
  ],
  exports: [
    // An application's logo in the theme colour (header launcher, Applications hub).
    AppAreaLogo,
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    RouterModule,
    MaterialModule,
    // Every shared UI piece (page header, stat cards, table + footer, address / phone inputs,
    // language switcher, dialogs) comes from hq-shared-lib; screens use the hq-* selectors directly.
    HqSharedLibModule,
    // `| translate` pipe / [translate] directive for every feature module (root config is in AppModule).
    TranslateModule,
  ],
  providers: [
    // The library has no translation dependency: its words are provided here from this app's i18n files.
    { provide: HQ_APP_DIALOG_TEXTS, useFactory: appDialogTexts },
    { provide: HQ_SCREEN_HELP_TEXTS, useFactory: screenHelpTexts },
    { provide: HQ_FIELD_TEXTS, useFactory: fieldTexts },
    { provide: HQ_COMMON_TABLE_TEXTS, useFactory: commonTableTexts },
    { provide: HQ_UI_STATE_TEXTS, useFactory: uiStateTexts },
    { provide: HQ_IDLE_TIMEOUT_TEXTS, useFactory: idleTimeoutTexts },
    { provide: HQ_TABLE_FOOTER_TEXTS, useFactory: tableFooterTexts },
    { provide: HQ_UPLOAD_TEXTS, useFactory: uploadTexts },
    { provide: HQ_LOOKUP_TEXTS, useFactory: lookupTexts },
    { provide: HQ_ADDRESS_INPUT_TEXTS, useFactory: addressInputTexts },
    { provide: HQ_PHONE_NUMBER_INPUT_TEXTS, useFactory: phoneNumberInputTexts },
    { provide: HQ_PHONE_DEFAULT_COUNTRY, useFactory: organizationCountrySetting },
    { provide: HQ_AMOUNT_INPUT_TEXTS, useFactory: amountInputTexts },
    { provide: HQ_AMOUNT_DEFAULT_CURRENCY, useFactory: organizationCurrencySetting },
    { provide: HQ_LEGAL_PROFILE_TEXTS, useFactory: legalProfileTexts },
    { provide: HQ_LANGUAGE_SWITCHER_TEXTS, useFactory: languageSwitcherTexts },
    { provide: HQ_IMAGE_CROP_DIALOG_TEXTS, useFactory: imageCropDialogTexts },
    { provide: HQ_LANGUAGE_PROVIDER, useExisting: LanguageService },
    { provide: HQ_IMAGE_CROPPER_COMPONENT, useValue: ImageCropperComponent },
    { provide: HQ_IMAGE_CROPPER, useFactory: imageCropperFactory, deps: [MatDialog] },
  ],
})
export class SharedModule {}
