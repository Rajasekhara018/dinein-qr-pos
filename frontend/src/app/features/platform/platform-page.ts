import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { PlatformApi } from '../../core/api/platform.api';
import { OnboardRestaurantResponse, RestaurantSummary } from '../../core/api/models';
import { errorMessage } from '../../shared/util/form-errors';
import { PlatformPrefs } from './data/platform-prefs';

interface OnboardForm {
  restaurantName: FormControl<string>;
  slug: FormControl<string>;
  ownerDisplayName: FormControl<string>;
}

/**
 * Internal tool: onboard a new restaurant and see every restaurant on the platform with its owner account(s).
 * Gated by a shared secret (`X-Platform-Admin-Key`), not staff auth — see `PlatformApi` and `PlatformPrefs`.
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

  protected readonly checkingKey = signal(false);
  protected readonly keyError = signal('');

  protected readonly restaurants = signal<RestaurantSummary[] | null>(null);
  protected readonly loading = signal(false);
  protected readonly loadError = signal<unknown>(null);

  protected readonly form = new FormGroup<OnboardForm>({
    restaurantName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
    slug: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(60)] }),
    ownerDisplayName: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(80)] }),
  });
  protected readonly submitting = signal(false);
  protected readonly formError = signal('');
  protected readonly result = signal<OnboardRestaurantResponse | null>(null);

  constructor() {
    if (this.prefs.key()) void this.refresh();
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
      this.restaurants.set(await firstValueFrom(this.api.list(this.prefs.key())));
    } catch (error) {
      this.loadError.set(error);
    } finally {
      this.loading.set(false);
    }
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
      const response = await firstValueFrom(
        this.api.onboard(this.prefs.key(), {
          restaurantName: v.restaurantName.trim(),
          slug: v.slug.trim() || null,
          ownerDisplayName: v.ownerDisplayName.trim() || null,
        }),
      );
      this.result.set(response);
      this.form.reset({ restaurantName: '', slug: '', ownerDisplayName: '' });
      await this.refresh();
    } catch (error) {
      this.formError.set(errorMessage(error, 'Could not onboard this restaurant.'));
    } finally {
      this.submitting.set(false);
    }
  }

  protected dismissResult(): void {
    this.result.set(null);
  }
}
