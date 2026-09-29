import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiError } from '../../../../core/api/api-error';
import { AdminMenuApi } from '../../../../core/api/admin.api';
import { CategoryResponse, KitchenStationResponse, UploadResult } from '../../../../core/api/models';
import { applyServerErrors, errorMessage, setServerError } from '../../../../shared/util/form-errors';

export interface CategoryDialogData {
  category: CategoryResponse | null;
}

interface CategoryForm {
  name: FormControl<string>;
  description: FormControl<string>;
  imageId: FormControl<number | null>;
  active: FormControl<boolean>;
  stationId: FormControl<number | null>;
}

/** Add / edit a category (dialog on desktop, bottom sheet on phones). Closes with the saved category. */
@Component({
  selector: 'app-category-dialog',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './category-dialog.html',
})
export class CategoryDialog implements OnInit {
  private readonly api = inject(AdminMenuApi);
  protected readonly ref = inject<DialogRef<CategoryResponse, CategoryDialog>>(DialogRef);
  protected readonly data = inject<CategoryDialogData>(DIALOG_DATA);

  protected readonly form = new FormGroup<CategoryForm>({
    name: new FormControl(this.data.category?.name ?? '', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(80)],
    }),
    description: new FormControl(this.data.category?.description ?? '', {
      nonNullable: true,
      validators: [Validators.maxLength(300)],
    }),
    imageId: new FormControl<number | null>(this.data.category?.imageId ?? null),
    active: new FormControl(this.data.category?.active ?? true, { nonNullable: true }),
    stationId: new FormControl<number | null>(this.data.category?.stationId ?? null),
  });

  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly imageUrl = signal<string | null>(this.data.category?.thumbUrl ?? null);
  protected readonly stations = signal<KitchenStationResponse[]>([]);
  protected readonly newStationName = new FormControl('', { nonNullable: true });
  protected readonly addingStation = signal(false);
  protected readonly stationError = signal('');

  ngOnInit(): void {
    this.loadStations();
  }

  private loadStations(): void {
    this.api.kitchenStations().subscribe({
      next: (stations) => this.stations.set(stations.filter((s) => s.active)),
      error: () => {
        // The category can still be saved without a station picker; not worth surfacing an error for this.
      },
    });
  }

  /** Lets the owner add a new kitchen station without leaving this dialog. */
  async addStation(): Promise<void> {
    const name = this.newStationName.value.trim();
    if (!name || this.addingStation()) return;
    this.addingStation.set(true);
    this.stationError.set('');
    try {
      const created = await firstValueFrom(this.api.createKitchenStation({ name }));
      this.stations.update((list) => [...list, created]);
      this.form.controls.stationId.setValue(created.id);
      this.newStationName.setValue('');
    } catch (error) {
      this.stationError.set(errorMessage(error, 'Could not add the station.'));
    } finally {
      this.addingStation.set(false);
    }
  }

  protected onUploaded(result: UploadResult): void {
    this.imageUrl.set(result.thumbUrl);
  }

  async save(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set('');
    const value = this.form.getRawValue();
    const body = {
      name: value.name.trim(),
      description: value.description.trim() || null,
      imageId: value.imageId,
      active: value.active,
      stationId: value.stationId,
    };
    try {
      const existing = this.data.category;
      const saved = existing
        ? await firstValueFrom(this.api.updateCategory(existing.id, body))
        : await firstValueFrom(this.api.createCategory(body));
      this.ref.close(saved);
    } catch (error) {
      if (error instanceof ApiError && error.code === 'DUPLICATE_NAME') {
        setServerError(this.form.controls.name, error.message);
      } else if (error instanceof ApiError && error.code === 'VALIDATION_FAILED') {
        const unmatched = applyServerErrors(this.form, error);
        if (unmatched.length) this.error.set(unmatched.join(' · '));
      } else {
        this.error.set(errorMessage(error, 'Could not save the category.'));
      }
    } finally {
      this.saving.set(false);
    }
  }
}
