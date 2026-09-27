import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  Injector,
  input,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { firstValueFrom, startWith } from 'rxjs';
import { ApiError } from '../../../../core/api/api-error';
import { AdminMenuApi } from '../../../../core/api/admin.api';
import {
  CategoryResponse,
  FoodType,
  ItemResponse,
  UploadResult,
} from '../../../../core/api/models';
import { ToastService } from '../../../../core/ui/toast.service';
import { ConfirmService } from '../../shared/confirm.service';
import { applyServerErrors, errorMessage, setServerError } from '../../shared/form-errors';
import {
  ADDONS_MESSAGES,
  addAddon,
  addVariant,
  applyHasSizes,
  createItemForm,
  GST_SLABS,
  MAX_ADDONS,
  MAX_VARIANTS,
  patchItemForm,
  removeVariant,
  setDefaultVariant,
  toItemRequest,
  toPreviewItem,
  VARIANTS_MESSAGES,
} from '../data/item-form';
import { sortByDisplayOrder } from '../data/reorder';

const FOOD_TYPES: { value: FoodType; label: string }[] = [
  { value: 'VEG', label: 'Veg' },
  { value: 'NON_VEG', label: 'Non-veg' },
  { value: 'EGG', label: 'Egg' },
];

/** Create / edit an item: details, image, sizes (FormArray), add-ons (FormArray) and a live guest-card preview. */
@Component({
  selector: 'app-admin-item-form-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './item-form-page.html',
})
export class ItemFormPage implements OnInit {
  private readonly api = inject(AdminMenuApi);
  private readonly router = inject(Router);
  private readonly toasts = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  /** Route param (`items/:id`); undefined for `items/new`. */
  readonly id = input<string | undefined>();
  /** `?categoryId=` preselects the category of a new item. */
  readonly categoryId = input<string | undefined>();

  protected readonly form = createItemForm();
  protected readonly foodTypes = FOOD_TYPES;
  protected readonly variantMessages = VARIANTS_MESSAGES;
  protected readonly addonMessages = ADDONS_MESSAGES;
  protected readonly maxVariants = MAX_VARIANTS;
  protected readonly maxAddons = MAX_ADDONS;

  protected readonly item = signal<ItemResponse | null>(null);
  protected readonly categories = signal<CategoryResponse[]>([]);
  protected readonly loading = signal(false);
  protected readonly loadError = signal<unknown>(null);
  protected readonly saving = signal(false);
  protected readonly attempted = signal(false);
  protected readonly formError = signal('');
  protected readonly conflict = signal(false);
  private readonly uploadedImage = signal<UploadResult | null>(null);

  protected readonly isEdit = computed(() => !!this.id());
  protected readonly title = computed(() =>
    this.isEdit() ? `Edit ${this.item()?.name ?? 'item'}` : 'New item',
  );

  private readonly formValue = toSignal(this.form.valueChanges.pipe(startWith(null)));
  protected readonly hasSizes = toSignal(
    this.form.controls.hasSizes.valueChanges.pipe(startWith(this.form.controls.hasSizes.value)),
    { initialValue: false },
  );

  protected readonly gstOptions = computed(() => {
    this.formValue();
    const current = this.form.controls.gstPercent.value;
    return current !== null && !GST_SLABS.includes(current) ? [...GST_SLABS, current] : GST_SLABS;
  });

  /** Image shown in the upload box and the preview. */
  protected readonly imageUrl = computed(() => {
    this.formValue();
    if (this.form.controls.imageId.value === null) return null;
    const uploaded = this.uploadedImage();
    if (uploaded && uploaded.imageId === this.form.controls.imageId.value) return uploaded.thumbUrl;
    return this.item()?.thumbUrl ?? null;
  });

  protected readonly preview = computed(() => {
    this.formValue();
    const thumb = this.imageUrl() ?? undefined;
    return toPreviewItem(this.form, { thumbUrl: thumb, imageUrl: thumb });
  });

  constructor() {
    this.form.controls.hasSizes.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((hasSizes) => applyHasSizes(this.form, hasSizes));
  }

  ngOnInit(): void {
    void this.loadCategories();
    const id = this.id();
    if (id) {
      void this.load(Number(id));
    } else {
      const categoryId = Number(this.categoryId());
      if (Number.isFinite(categoryId) && categoryId > 0) {
        this.form.controls.categoryId.setValue(categoryId);
      }
    }
  }

  protected async load(id: number): Promise<void> {
    this.loading.set(true);
    this.loadError.set(null);
    this.conflict.set(false);
    try {
      const item = await firstValueFrom(this.api.item(id));
      this.item.set(item);
      this.uploadedImage.set(null);
      patchItemForm(this.form, item);
    } catch (error) {
      this.loadError.set(error);
    } finally {
      this.loading.set(false);
    }
  }

  private async loadCategories(): Promise<void> {
    try {
      this.categories.set(sortByDisplayOrder(await firstValueFrom(this.api.categories())));
    } catch {
      this.formError.set('Could not load categories. Reload the page to try again.');
    }
  }

  protected onImageUploaded(result: UploadResult): void {
    this.uploadedImage.set(result);
  }

  protected addVariant(): void {
    addVariant(this.form);
  }

  protected removeVariant(index: number): void {
    removeVariant(this.form, index);
  }

  protected setDefault(index: number): void {
    setDefaultVariant(this.form, index);
  }

  protected addAddon(): void {
    addAddon(this.form);
  }

  protected removeAddon(index: number): void {
    this.form.controls.addons.removeAt(index);
  }

  async save(): Promise<void> {
    this.attempted.set(true);
    this.formError.set('');
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.focusFirstInvalid();
      return;
    }
    this.saving.set(true);
    try {
      const current = this.item();
      const body = toItemRequest(this.form, current?.version ?? null);
      const saved = current
        ? await firstValueFrom(this.api.updateItem(current.id, body))
        : await firstValueFrom(this.api.createItem(body));
      this.form.markAsPristine();
      this.toasts.success(current ? `${saved.name} saved.` : `${saved.name} added to the menu.`);
      await this.router.navigate(['/admin/menu/items'], {
        queryParams: { categoryId: saved.categoryId },
      });
    } catch (error) {
      this.handleSaveError(error);
    } finally {
      this.saving.set(false);
    }
  }

  protected async remove(): Promise<void> {
    const item = this.item();
    if (!item) return;
    const confirmed = await this.confirmService.confirm({
      title: `Delete ${item.name}?`,
      message: 'The item disappears from the menu. Past orders and bills are not affected.',
      confirmLabel: 'Delete item',
    });
    if (!confirmed) return;
    try {
      await firstValueFrom(this.api.deleteItem(item.id));
      this.toasts.success(`${item.name} deleted.`);
      await this.router.navigate(['/admin/menu/items'], {
        queryParams: { categoryId: item.categoryId },
      });
    } catch (error) {
      this.toasts.error(errorMessage(error, 'Could not delete the item.'));
    }
  }

  protected reloadAfterConflict(): void {
    const id = this.item()?.id;
    if (id) void this.load(id);
  }

  /** `unsavedChangesGuard` — asks before leaving with unsaved edits (the form is already pristine right after a
   *  successful save, so a normal "Cancel"/breadcrumb navigation right after saving is never interrupted). */
  async canDeactivate(): Promise<boolean> {
    if (!this.form.dirty) return true;
    const result = await this.confirmService.confirm({
      title: 'Discard unsaved changes?',
      message: 'Your edits to this item have not been saved.',
      confirmLabel: 'Discard changes',
      cancelLabel: 'Keep editing',
    });
    return !!result;
  }

  private handleSaveError(error: unknown): void {
    if (!(error instanceof ApiError)) {
      this.formError.set(errorMessage(error));
      return;
    }
    switch (error.code) {
      case 'CONCURRENT_MODIFICATION':
        this.conflict.set(true);
        return;
      case 'VALIDATION_FAILED': {
        const unmatched = applyServerErrors(this.form, error);
        this.formError.set(unmatched.length ? unmatched.join(' · ') : 'Please fix the highlighted fields.');
        this.focusFirstInvalid();
        return;
      }
      case 'DUPLICATE_NAME':
        setServerError(this.form.controls.name, error.message);
        this.focusFirstInvalid();
        return;
      case 'PRICE_REQUIRED':
        if (this.form.controls.basePrice.enabled) setServerError(this.form.controls.basePrice, error.message);
        else this.formError.set(error.message);
        return;
      case 'UNKNOWN_CATEGORY':
        setServerError(this.form.controls.categoryId, error.message);
        return;
      default:
        this.formError.set(error.message);
    }
  }

  private focusFirstInvalid(): void {
    afterNextRender(
      () => {
        const el = this.host.nativeElement.querySelector<HTMLElement>(
          '[aria-invalid="true"], input.ng-invalid, select.ng-invalid, textarea.ng-invalid',
        );
        el?.focus();
      },
      { injector: this.injector },
    );
  }
}
