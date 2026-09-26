import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { RouterModule } from '@angular/router';
import { SharedModule } from '../../../shared/shared-module';
import { ConfirmDialog } from './confirm-dialog/confirm-dialog';
import { FieldError } from './field-error/field-error';
import { ImageUpload } from './image-upload/image-upload';
import { InputDirective } from './input.directive';
import { OrderList } from './order-list/order-list';
import { PageHeader } from './page-header/page-header';
import { StatCard } from './stat-card/stat-card';
import { ToggleSwitch } from './toggle-switch/toggle-switch';

const DECLARATIONS = [
  ConfirmDialog,
  FieldError,
  ImageUpload,
  InputDirective,
  OrderList,
  PageHeader,
  StatCard,
  ToggleSwitch,
];

/**
 * Admin-only UI (image upload, switch, confirm dialog, form helpers). Imported by `AdminModule` and by every lazily
 * loaded admin section module; re-exports `SharedModule`, `ReactiveFormsModule`, `DragDropModule` and `RouterModule`.
 */
@NgModule({
  declarations: DECLARATIONS,
  imports: [SharedModule, ReactiveFormsModule, DragDropModule, RouterModule],
  exports: [...DECLARATIONS, SharedModule, ReactiveFormsModule, DragDropModule, RouterModule],
})
export class AdminSharedModule {}
