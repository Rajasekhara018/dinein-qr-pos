import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiError } from '../../core/api/api-error';
import { AuthStore } from '../../core/auth/auth.store';
import { PlatformApi } from '../../core/api/platform.api';
import { OnboardRestaurantResponse, RestaurantSummary } from '../../core/api/models';
import {
  generateTemporaryPassword,
  PASSWORD_MESSAGES,
  passwordPolicy,
} from '../admin/auth/password-validators';
import {
  COLOR_PATTERN,
  FSSAI_PATTERN,
  GSTIN_PATTERN,
  PHONE_PATTERN,
} from '../admin/settings/settings-form';
import { errorMessage, setServerError } from '../../shared/util/form-errors';
import { PlatformPrefs } from './data/platform-prefs';

interface OnboardForm {
  restaurantName: FormControl<string>;
  slug: FormControl<string>;
  address: FormControl<string>;
  phone: FormControl<string>;
  gstin: FormControl<string>;
  fssaiNo: FormControl<string>;
  pricesIncludeGst: FormControl<boolean>;
  openingTime: FormControl<string>;
  closingTime: FormControl<string>;
  brandColor: FormControl<string>;
  takeawayEnabled: FormControl<boolean>;
  ownerDisplayName: FormControl<string>;
  ownerUsername: FormControl<string>;
  ownerEmail: FormControl<string>;
  ownerPhone: FormControl<string>;
  ownerPassword: FormControl<string>;
}

const USERNAME_PATTERN = /^[A-Za-z0-9._-]+$/;

/**
 * Internal tool: onboard a new restaurant and see every restaurant on the platform with its owner account(s).
 * Two ways in, matching `PlatformApi`/the backend: already logged into the admin panel with a `platformAdmin`
 * account (the normal path — no key prompt at all, calls go out with the staff JWT), or a shared secret
 * (`X-Platform-Admin-Key`) entered by hand for use outside any login — see `PlatformPrefs`.
 *
 * The form mirrors `RestaurantSettingsEntity`/`StaffUserEntity` (see `SettingsForm`/`StaffDialog`) so a restaurant
 * can be handed to its owner fully set up instead of them having to fill in GSTIN, hours, branding etc. themselves
 * on first login — every field past the restaurant name is optional and falls back to the entity's own default.
 */
@Component({
  selector: 'app-platform-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './platform-page.html',
})
export class PlatformPage {
  private readonly api = inject(PlatformApi);
  protected readonly prefs = inject(PlatformPrefs);
  protected readonly auth = inject(AuthStore);

  protected readonly checkingKey = signal(false);
  protected readonly keyError = signal('');

  /** Already signed in as a platform admin, or unlocked with the shared key for this tab. */
  protected readonly unlocked = computed(() => this.auth.isPlatformAdmin() || !!this.prefs.key());

  protected readonly restaurants = signal<RestaurantSummary[] | null>(null);
  protected readonly loading = signal(false);
  protected readonly loadError = signal<unknown>(null);

  protected readonly passwordMessages = PASSWORD_MESSAGES;

  protected readonly form = new FormGroup<OnboardForm>({
    restaurantName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
    slug: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(60)] }),
    address: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(300)] }),
    phone: new FormControl('', { nonNullable: true, validators: [Validators.pattern(PHONE_PATTERN)] }),
    gstin: new FormControl('', { nonNullable: true, validators: [Validators.pattern(GSTIN_PATTERN)] }),
    fssaiNo: new FormControl('', { nonNullable: true, validators: [Validators.pattern(FSSAI_PATTERN)] }),
    pricesIncludeGst: new FormControl(false, { nonNullable: true }),
    openingTime: new FormControl('', { nonNullable: true }),
    closingTime: new FormControl('', { nonNullable: true }),
    brandColor: new FormControl('', { nonNullable: true, validators: [Validators.pattern(COLOR_PATTERN)] }),
    takeawayEnabled: new FormControl(true, { nonNullable: true }),
    ownerDisplayName: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(80)] }),
    ownerUsername: new FormControl('', {
      nonNullable: true,
      validators: [Validators.minLength(3), Validators.maxLength(50), Validators.pattern(USERNAME_PATTERN)],
    }),
    ownerEmail: new FormControl('', { nonNullable: true, validators: [Validators.email, Validators.maxLength(120)] }),
    ownerPhone: new FormControl('', { nonNullable: true, validators: [Validators.pattern(/^\d{10}$/)] }),
    ownerPassword: new FormControl('', { nonNullable: true, validators: [passwordPolicy] }),
  });
  protected readonly showPassword = signal(false);
  protected readonly submitting = signal(false);
  protected readonly formError = signal('');
  protected readonly result = signal<OnboardRestaurantResponse | null>(null);

  constructor() {
    if (this.unlocked()) void this.refresh();
  }

  /** `null` tells `PlatformApi` to use the caller's own staff JWT instead of the shared-key header. */
  private currentKey(): string | null {
    return this.auth.isPlatformAdmin() ? null : this.prefs.key();
  }

  protected async unlock(rawKey: string): Promise<void> {
    const key = rawKey.trim();
    if (!key) return;
    this.checkingKey.set(true);
    this.keyError.set('');
    try {
      const restaurants = await firstValueFrom(this.api.list(key));
      this.prefs.setKey(key);
      this.restaurants.set(restaurants);
    } catch (error) {
      this.keyError.set(errorMessage(error, 'Invalid platform admin key.'));
    } finally {
      this.checkingKey.set(false);
    }
  }

  protected lock(): void {
    this.prefs.clear();
    this.restaurants.set(null);
  }

  protected async refresh(): Promise<void> {
    this.loading.set(true);
    this.loadError.set(null);
    try {
      this.restaurants.set(await firstValueFrom(this.api.list(this.currentKey())));
    } catch (error) {
      this.loadError.set(error);
    } finally {
      this.loading.set(false);
    }
  }

  protected generatePassword(): void {
    this.form.controls.ownerPassword.setValue(generateTemporaryPassword());
    this.form.controls.ownerPassword.markAsTouched();
    this.showPassword.set(true);
  }

  protected uppercaseGstin(): void {
    this.form.controls.gstin.setValue(this.form.controls.gstin.value.toUpperCase());
  }

  protected async onboard(): Promise<void> {
    this.formError.set('');
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    try {
      const v = this.form.getRawValue();
      const orNull = (s: string) => s.trim() || null;
      const response = await firstValueFrom(
        this.api.onboard(this.currentKey(), {
          restaurantName: v.restaurantName.trim(),
          slug: orNull(v.slug),
          address: orNull(v.address),
          phone: orNull(v.phone),
          gstin: orNull(v.gstin.toUpperCase()),
          fssaiNo: orNull(v.fssaiNo),
          pricesIncludeGst: v.pricesIncludeGst,
          openingTime: orNull(v.openingTime),
          closingTime: orNull(v.closingTime),
          brandColor: orNull(v.brandColor),
          takeawayEnabled: v.takeawayEnabled,
          ownerDisplayName: orNull(v.ownerDisplayName),
          ownerUsername: orNull(v.ownerUsername),
          ownerEmail: orNull(v.ownerEmail),
          ownerPhone: orNull(v.ownerPhone),
          ownerPassword: orNull(v.ownerPassword),
        }),
      );
      this.result.set(response);
      this.form.reset({
        restaurantName: '',
        slug: '',
        address: '',
        phone: '',
        gstin: '',
        fssaiNo: '',
        pricesIncludeGst: false,
        openingTime: '',
        closingTime: '',
        brandColor: '',
        takeawayEnabled: true,
        ownerDisplayName: '',
        ownerUsername: '',
        ownerEmail: '',
        ownerPhone: '',
        ownerPassword: '',
      });
      this.showPassword.set(false);
      await this.refresh();
    } catch (error) {
      this.handleOnboardError(error);
    } finally {
      this.submitting.set(false);
    }
  }

  private handleOnboardError(error: unknown): void {
    if (error instanceof ApiError) {
      if (error.code === 'USERNAME_TAKEN') {
        setServerError(this.form.controls.ownerUsername, error.message);
        return;
      }
      if (error.code === 'WEAK_PASSWORD') {
        setServerError(this.form.controls.ownerPassword, error.message);
        return;
      }
    }
    this.formError.set(errorMessage(error, 'Could not onboard this restaurant.'));
  }

  protected dismissResult(): void {
    this.result.set(null);
  }
}
