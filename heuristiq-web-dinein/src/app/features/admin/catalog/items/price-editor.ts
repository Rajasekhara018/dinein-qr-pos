import {
  afterNextRender,
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  Injector,
  input,
  output,
  signal,
  viewChildren,
} from '@angular/core';
import { AbstractControl, FormControl, FormRecord, ValidationErrors } from '@angular/forms';
import { ItemResponse } from '../../../../core/api/models';
import { parsePrice, PriceChange } from '../data/inline-price';

interface PriceField {
  key: string;
  label: string;
  variantId: number | null;
}

function priceText(control: AbstractControl): ValidationErrors | null {
  return parsePrice(control.value as string) === null ? { price: true } : null;
}

/**
 * Inline rate editor for one item row: shows the price(s); click → inputs (one per size); Enter saves, Escape
 * cancels. Emits only valid changes; the parent applies them optimistically.
 */
@Component({
  selector: 'app-price-editor',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block min-w-0' },
  templateUrl: './price-editor.html',
})
export class PriceEditor {
  private readonly injector = inject(Injector);

  readonly item = input.required<ItemResponse>();
  readonly busy = input(false, { transform: booleanAttribute });
  readonly save = output<PriceChange>();

  protected readonly editing = signal(false);
  protected readonly invalid = signal(false);
  protected readonly form = new FormRecord<FormControl<string>>({});
  private readonly inputs = viewChildren<ElementRef<HTMLInputElement>>('priceInput');

  protected readonly fields = computed<PriceField[]>(() => {
    const item = this.item();
    if (item.hasVariants && item.variants.length) {
      return item.variants.map((v) => ({ key: `v${v.id}`, label: v.name, variantId: v.id }));
    }
    return [{ key: 'base', label: 'Price', variantId: null }];
  });

  protected readonly summary = computed(() => {
    const item = this.item();
    if (item.hasVariants && item.variants.length) {
      return item.variants.map((v) => `${v.name} ₹${v.price}`).join(', ');
    }
    return `₹${item.basePrice ?? item.displayPrice ?? 0}`;
  });

  start(): void {
    if (this.busy()) return;
    const item = this.item();
    for (const key of Object.keys(this.form.controls)) this.form.removeControl(key);
    for (const field of this.fields()) {
      const value =
        field.variantId === null
          ? (item.basePrice ?? item.displayPrice ?? '')
          : (item.variants.find((v) => v.id === field.variantId)?.price ?? '');
      this.form.addControl(
        field.key,
        new FormControl(String(value), { nonNullable: true, validators: [priceText] }),
      );
    }
    this.invalid.set(false);
    this.editing.set(true);
    afterNextRender(
      () => {
        const first = this.inputs()[0]?.nativeElement;
        first?.focus();
        first?.select();
      },
      { injector: this.injector },
    );
  }

  protected cancel(): void {
    this.editing.set(false);
    this.invalid.set(false);
  }

  protected commit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.invalid.set(true);
      return;
    }
    const change: PriceChange = {};
    for (const field of this.fields()) {
      const price = parsePrice(this.form.controls[field.key].value) as number;
      if (field.variantId === null) change.basePrice = price;
      else (change.variants ??= []).push({ id: field.variantId, price });
    }
    this.editing.set(false);
    this.save.emit(change);
  }

  protected isInvalid(key: string): boolean {
    const control = this.form.controls[key];
    return !!control && control.invalid && (control.touched || this.invalid());
  }
}
