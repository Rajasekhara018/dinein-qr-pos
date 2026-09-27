import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { RouterModule } from '@angular/router';
import { SharedModule } from '../../../shared/shared-module';
import { ImageUpload } from './image-upload/image-upload';
import { OrderList } from './order-list/order-list';
import { PageHeader } from './page-header/page-header';
import { StatCard } from './stat-card/stat-card';
import { ToggleSwitch } from './toggle-switch/toggle-switch';

const DECLARATIONS = [ImageUpload, OrderList, PageHeader, StatCard, ToggleSwitch];

/**
 * Admin-only UI (image upload, switch, order list, form helpers). Imported by `AdminModule` and by every lazily
 * loaded admin section module; re-exports `SharedModule` (which carries `ConfirmDialog`/`ConfirmService`,
 * `FieldError` and `InputDirective` — promoted out of here so kitchen and waiter can reuse them too), plus
 * `ReactiveFormsModule`, `DragDropModule` and `RouterModule`.
 */
@NgModule({
  declarations: DECLARATIONS,
  imports: [SharedModule, ReactiveFormsModule, DragDropModule, RouterModule],
  exports: [...DECLARATIONS, SharedModule, ReactiveFormsModule, DragDropModule, RouterModule],
})
export class AdminSharedModule {}
