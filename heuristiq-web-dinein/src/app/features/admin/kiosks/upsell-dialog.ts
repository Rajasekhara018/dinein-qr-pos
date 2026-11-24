import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { firstValueFrom, startWith } from 'rxjs';
import { AdminKioskApi } from '../../../core/api/admin.api';
import { ApiError } from '../../../core/api/api-error';
import { KioskUpsellPlacement, KioskUpsellView } from '../../../core/api/models';
import { errorMessage } from '../../../shared/util/form-errors';
import {
  MESSAGE_MAX,
  PLACEMENT_OPTIONS,
  toUpsellRequest,
  TRIGGER_OPTIONS,
  triggerKind,
  triggerProblem,
  UpsellFormValue,
  UpsellTriggerKind,
} from './upsell-logic';

export interface UpsellDialogData {
  rule: KioskUpsellView | null;
  items: readonly { id: number; name: string; categoryName: string }[];
  categories: readonly { id: number; name: string }[];
}

interface UpsellForm {
  triggerKind: FormControl<UpsellTriggerKind>;
  triggerCategoryId: FormControl<number | null>;
  triggerItemId: FormControl<number | null>;
  suggestedItemId: FormControl<number | null>;
  placement: FormControl<KioskUpsellPlacement>;
  message: FormControl<string>;
  active: FormControl<boolean>;
}

/** Create or edit one kiosk upsell rule. Closes with the saved rule. */
@Component({
  selector: 'app-kiosk-upsell-dialog',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './upsell-dialog.html',
})
export class UpsellDialog {
  private readonly api = inject(AdminKioskApi);
  protected readonly ref = inject<DialogRef<KioskUpsellView, UpsellDialog>>(DialogRef);
  protected readonly data = inject<UpsellDialogData>(DIALOG_DATA);

  protected readonly triggerOptions = TRIGGER_OPTIONS;
  protected readonly placementOptions = PLACEMENT_OPTIONS;
  protected readonly messageMax = MESSAGE_MAX;
  protected readonly isEdit = !!this.data.rule;

  protected readonly form = new FormGroup<UpsellForm>({
    triggerKind: new FormControl<UpsellTriggerKind>(
      this.data.rule ? triggerKind(this.data.rule) : 'ANY',
      { nonNullable: true },
    ),
    triggerCategoryId: new FormControl(this.data.rule?.triggerCategoryId ?? null),
    triggerItemId: new FormControl(this.data.rule?.triggerItemId ?? null),
    suggestedItemId: new FormControl(this.data.rule?.suggestedItemId ?? null, {
      validators: [Validators.required],
    }),
    placement: new FormControl<KioskUpsellPlacement>(this.data.rule?.placement ?? 'ITEM_ADDED', {
      nonNullable: true,
    }),
    message: new FormControl(this.data.rule?.message ?? '', {
      nonNullable: true,
      validators: [Validators.maxLength(MESSAGE_MAX)],
    }),
    active: new FormControl(this.data.rule?.active ?? true, { nonNullable: true }),
  });

  private readonly formValue = toSignal(this.form.valueChanges.pipe(startWith(null)));
  protected readonly kind = computed(() => {
    this.formValue();
    return this.form.controls.triggerKind.value;
  });
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly attempted = signal(false);
  protected readonly title = computed(() => (this.isEdit ? 'Edit upsell' : 'New upsell'));

  protected async save(): Promise<void> {
    this.attempted.set(true);
    this.error.set('');
    const value = this.form.getRawValue() as UpsellFormValue;
    if (this.form.invalid || triggerProblem(value)) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const body = toUpsellRequest(
      value as UpsellFormValue & { suggestedItemId: number },
      this.data.rule?.sortOrder,
    );
    try {
      const saved = this.data.rule
        ? await firstValueFrom(this.api.updateUpsell(this.data.rule.id, body))
        : await firstValueFrom(this.api.createUpsell(body));
      this.ref.close(saved);
    } catch (error) {
      // INVALID_UPSELL_TRIGGER / INVALID_UPSELL_ITEM carry a readable server message.
      this.error.set(
        error instanceof ApiError
          ? error.message
          : errorMessage(error, 'Could not save the upsell.'),
      );
    } finally {
      this.saving.set(false);
    }
  }

  protected problem(): string | null {
    return this.attempted() ? triggerProblem(this.form.getRawValue() as UpsellFormValue) : null;
  }
}
