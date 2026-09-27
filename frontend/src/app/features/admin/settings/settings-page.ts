import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { firstValueFrom, startWith } from 'rxjs';
import { ApiError } from '../../../core/api/api-error';
import { AdminSettingsApi } from '../../../core/api/admin.api';
import { SettingsResponse, UploadResult } from '../../../core/api/models';
import { ToastService } from '../../../core/ui/toast.service';
import { brandPalette } from '../../../core/util/color';
import { applyServerErrors, errorMessage } from '../shared/form-errors';
import { createSettingsForm, patchSettingsForm, toSettingsRequest } from './settings-form';

/** OWNER-only restaurant settings. */
@Component({
  selector: 'app-admin-settings-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './settings-page.html',
})
export class SettingsPage {
  private readonly api = inject(AdminSettingsApi);
  private readonly toasts = inject(ToastService);

  protected readonly form = createSettingsForm();
  protected readonly settings = signal<SettingsResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<unknown>(null);
  protected readonly saving = signal(false);
  protected readonly formError = signal('');
  protected readonly attempted = signal(false);
  private readonly uploadedLogo = signal<UploadResult | null>(null);

  private readonly color = toSignal(
    this.form.controls.brandColor.valueChanges.pipe(startWith(this.form.controls.brandColor.value)),
    { initialValue: '#c2410c' },
  );
  protected readonly palette = computed(() => brandPalette(this.color()));

  private readonly formValue = toSignal(this.form.valueChanges.pipe(startWith(null)));
  /** Drives the sticky save bar's visibility — it should only appear once something has actually changed. */
  protected readonly dirty = computed(() => {
    this.formValue();
    return this.form.dirty;
  });
  protected readonly logoUrl = computed(() => this.uploadedLogo()?.thumbUrl ?? this.settings()?.logoUrl ?? null);

  constructor() {
    void this.load();
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    try {
      const settings = await firstValueFrom(this.api.settings());
      this.settings.set(settings);
      this.uploadedLogo.set(null);
      patchSettingsForm(this.form, settings);
      this.loadError.set(null);
    } catch (error) {
      this.loadError.set(error);
    } finally {
      this.loading.set(false);
    }
  }

  protected onLogoUploaded(result: UploadResult): void {
    this.uploadedLogo.set(result);
  }

  protected onColorPicked(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.form.controls.brandColor.setValue(value);
    this.form.controls.brandColor.markAsDirty();
  }

  protected uppercaseGstin(): void {
    const control = this.form.controls.gstin;
    const upper = control.value.toUpperCase().trim();
    if (upper !== control.value) control.setValue(upper);
  }

  async save(): Promise<void> {
    this.attempted.set(true);
    this.formError.set('');
    this.uppercaseGstin();
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    try {
      const saved = await firstValueFrom(this.api.updateSettings(toSettingsRequest(this.form)));
      this.settings.set(saved);
      patchSettingsForm(this.form, saved);
      this.attempted.set(false);
      this.toasts.success('Settings saved.');
    } catch (error) {
      if (error instanceof ApiError && error.code === 'VALIDATION_FAILED') {
        const unmatched = applyServerErrors(this.form, error);
        this.formError.set(unmatched.length ? unmatched.join(' · ') : 'Please fix the highlighted fields.');
      } else {
        this.formError.set(errorMessage(error, 'Could not save the settings.'));
      }
    } finally {
      this.saving.set(false);
    }
  }

  protected discard(): void {
    const settings = this.settings();
    if (settings) patchSettingsForm(this.form, settings);
    this.uploadedLogo.set(null);
    this.attempted.set(false);
    this.formError.set('');
  }
}
