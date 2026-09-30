import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { firstValueFrom, startWith } from 'rxjs';
import { AdminKioskApi } from '../../../core/api/admin.api';
import { ApiError } from '../../../core/api/api-error';
import { KioskBrandingResponse, UploadResult } from '../../../core/api/models';
import { ToastService } from '../../../core/ui/toast.service';
import { ConfirmService } from '../../../shared/services/confirm.service';
import { applyServerErrors, errorMessage } from '../../../shared/util/form-errors';
import {
  BrandingFormValue,
  brandingToForm,
  formToBrandingRequest,
  HEX_COLOR,
  IDLE_TIMEOUT_MAX,
  IDLE_TIMEOUT_MIN,
  isLowWhiteContrast,
  KIOSK_DEFAULTS,
  MIN_TEXT_CONTRAST,
  whiteContrast,
  welcomePreview,
} from './kiosk-logic';

interface KioskAppearanceForm {
  kioskEnabled: FormControl<boolean>;
  primaryColor: FormControl<string>;
  secondaryColor: FormControl<string>;
  headline: FormControl<string>;
  subtext: FormControl<string>;
  startButtonLabel: FormControl<string>;
  idleTimeoutSeconds: FormControl<number | null>;
  logoImageId: FormControl<number | null>;
  backgroundImageId: FormControl<number | null>;
}

/** Owner/manager: kiosk kill switch, welcome-screen branding and a live preview. */
@Component({
  selector: 'app-admin-kiosk-appearance-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './kiosk-appearance-page.html',
})
export class KioskAppearancePage {
  private readonly api = inject(AdminKioskApi);
  private readonly toasts = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);

  protected readonly defaults = KIOSK_DEFAULTS;
  protected readonly timeoutRange = { min: IDLE_TIMEOUT_MIN, max: IDLE_TIMEOUT_MAX };
  protected readonly minContrast = MIN_TEXT_CONTRAST;

  protected readonly form = new FormGroup<KioskAppearanceForm>({
    kioskEnabled: new FormControl(false, { nonNullable: true }),
    primaryColor: new FormControl('', {
      nonNullable: true,
      validators: [Validators.pattern(HEX_COLOR)],
    }),
    secondaryColor: new FormControl('', {
      nonNullable: true,
      validators: [Validators.pattern(HEX_COLOR)],
    }),
    headline: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(80)] }),
    subtext: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(120)] }),
    startButtonLabel: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(40)],
    }),
    idleTimeoutSeconds: new FormControl<number | null>(null, {
      validators: [Validators.min(IDLE_TIMEOUT_MIN), Validators.max(IDLE_TIMEOUT_MAX)],
    }),
    logoImageId: new FormControl<number | null>(null),
    backgroundImageId: new FormControl<number | null>(null),
  });

  protected readonly branding = signal<KioskBrandingResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<unknown>(null);
  protected readonly saving = signal(false);
  protected readonly formError = signal('');
  private readonly uploadedLogo = signal<UploadResult | null>(null);
  private readonly uploadedBackground = signal<UploadResult | null>(null);
  /** True while the form is being filled from the server, so the kill-switch confirm does not fire for that. */
  private patching = false;

  private readonly formValue = toSignal(this.form.valueChanges.pipe(startWith(null)));
  private readonly value = computed<BrandingFormValue>(() => {
    this.formValue();
    return this.form.getRawValue();
  });

  protected readonly dirty = computed(() => {
    this.formValue();
    return this.form.dirty;
  });
  protected readonly preview = computed(() => welcomePreview(this.value()));
  protected readonly restaurantName = computed(() => this.branding()?.restaurantName ?? '');
  protected readonly logoUrl = computed(
    () => this.uploadedLogo()?.thumbUrl ?? this.branding()?.logoUrl ?? null,
  );
  protected readonly backgroundUrl = computed(
    () => this.uploadedBackground()?.url ?? this.branding()?.backgroundUrl ?? null,
  );
  /** What the preview shows: cleared images disappear immediately. */
  protected readonly previewLogo = computed(() =>
    this.value().logoImageId === null ? null : this.logoUrl(),
  );
  protected readonly previewBackground = computed(() =>
    this.value().backgroundImageId === null ? null : this.backgroundUrl(),
  );
  protected readonly contrast = computed(() => whiteContrast(this.preview().primaryColor));
  protected readonly lowContrast = computed(() => isLowWhiteContrast(this.preview().primaryColor));
  protected readonly enabled = computed(() => this.value().kioskEnabled);

  constructor() {
    void this.load();
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    try {
      this.apply(await firstValueFrom(this.api.branding()));
      this.loadError.set(null);
    } catch (error) {
      this.loadError.set(error);
    } finally {
      this.loading.set(false);
    }
  }

  private apply(branding: KioskBrandingResponse): void {
    this.branding.set(branding);
    this.uploadedLogo.set(null);
    this.uploadedBackground.set(null);
    this.patching = true;
    try {
      this.form.reset(brandingToForm(branding));
    } finally {
      this.patching = false;
    }
  }

  protected onLogoUploaded(result: UploadResult): void {
    this.uploadedLogo.set(result);
  }

  protected onBackgroundUploaded(result: UploadResult): void {
    this.uploadedBackground.set(result);
  }

  protected onColorPicked(control: 'primaryColor' | 'secondaryColor', event: Event): void {
    const c = this.form.controls[control];
    c.setValue((event.target as HTMLInputElement).value.toUpperCase());
    c.markAsDirty();
  }

  protected resetColor(control: 'primaryColor' | 'secondaryColor'): void {
    const c = this.form.controls[control];
    c.setValue('');
    c.markAsDirty();
  }

  /** Turning the kiosks OFF asks first; if declined the switch snaps back on. */
  protected async onEnabledChange(enabled: boolean): Promise<void> {
    if (this.patching || enabled || !this.branding()?.kioskEnabled) return;
    const confirmed = await this.confirmService.confirm({
      title: 'Turn kiosks off?',
      message:
        'After you save, every kiosk stops taking orders and shows an out-of-service screen until you turn them back on.',
      confirmLabel: 'Turn off',
    });
    if (!confirmed) this.form.controls.kioskEnabled.setValue(true);
  }

  protected async save(): Promise<void> {
    this.formError.set('');
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    try {
      const saved = await firstValueFrom(
        this.api.updateBranding(formToBrandingRequest(this.value())),
      );
      this.apply(saved);
      this.toasts.success('Kiosk appearance saved.');
    } catch (error) {
      if (error instanceof ApiError && error.code === 'VALIDATION_FAILED') {
        const unmatched = applyServerErrors(this.form, error);
        this.formError.set(
          unmatched.length ? unmatched.join(' · ') : 'Please fix the highlighted fields.',
        );
      } else {
        this.formError.set(errorMessage(error, 'Could not save the kiosk appearance.'));
      }
    } finally {
      this.saving.set(false);
    }
  }

  protected discard(): void {
    const branding = this.branding();
    if (branding) this.apply(branding);
    this.formError.set('');
  }
}
