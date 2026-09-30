import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { FormControl, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { AdminKioskApi } from '../../../core/api/admin.api';
import { KioskPairingResponse } from '../../../core/api/models';
import { errorMessage } from '../../../shared/util/form-errors';
import { formatCountdown, secondsUntil } from './kiosk-logic';

export interface KioskPairingDialogData {
  /** `null`: ask for a name and create the kiosk first. Otherwise show this (already issued) code straight away. */
  issued: KioskPairingResponse | null;
}

/** Add a kiosk, then show its one-time pairing code (also used to show a re-issued code). Closes with `true` once a
 *  kiosk was created or re-issued, so the page refreshes. */
@Component({
  selector: 'app-kiosk-pairing-dialog',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './kiosk-pairing-dialog.html',
})
export class KioskPairingDialog {
  private readonly api = inject(AdminKioskApi);
  protected readonly ref = inject<DialogRef<boolean, KioskPairingDialog>>(DialogRef);
  protected readonly data = inject<KioskPairingDialogData>(DIALOG_DATA);

  protected readonly name = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(60), Validators.pattern(/\S/)],
  });
  protected readonly issued = signal<KioskPairingResponse | null>(this.data.issued);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  private readonly now = signal(Date.now());

  protected readonly remainingSeconds = computed(() => {
    const issued = this.issued();
    return issued ? secondsUntil(issued.pairingExpiresAt, this.now()) : 0;
  });
  protected readonly countdown = computed(() => formatCountdown(this.remainingSeconds()));
  protected readonly expired = computed(() => !!this.issued() && this.remainingSeconds() === 0);
  protected readonly title = computed(() =>
    this.issued() ? `Pair ${this.issued()?.name}` : 'Add kiosk',
  );

  constructor() {
    const timer = setInterval(() => this.now.set(Date.now()), 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  protected async create(): Promise<void> {
    if (this.name.invalid) {
      this.name.markAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set('');
    try {
      const result = await firstValueFrom(this.api.createDevice({ name: this.name.value.trim() }));
      this.now.set(Date.now());
      this.issued.set(result);
    } catch (error) {
      this.error.set(errorMessage(error, 'Could not add the kiosk.'));
    } finally {
      this.saving.set(false);
    }
  }

  protected done(): void {
    this.ref.close(this.issued() !== null);
  }
}
