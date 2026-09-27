import { NgModule } from '@angular/core';
import { CommonModule, NgOptimizedImage } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { A11yModule } from '@angular/cdk/a11y';
import { DialogModule } from '@angular/cdk/dialog';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { LayoutModule } from '@angular/cdk/layout';
import { OverlayModule } from '@angular/cdk/overlay';

import { AuthShell } from './components/auth-shell/auth-shell';
import { BottomSheet } from './components/bottom-sheet';
import { Card } from './components/card';
import { Chip } from './components/chip';
import { DataTable } from './components/data-table';
import { EmptyState } from './components/empty-state';
import { ErrorState } from './components/error-state';
import { ImageBox } from './components/image-box';
import { OrderStatusBadge } from './components/order-status-badge';
import { OrderTypeBadge } from './components/order-type-badge';
import { Price } from './components/price';
import { QuantityStepper } from './components/quantity-stepper';
import { ReconnectingBanner } from './components/reconnecting-banner';
import { Skeleton } from './components/skeleton';
import { Spinner } from './components/spinner';
import { VegMarker } from './components/veg-marker';
import { ButtonDirective, IconButtonDirective } from './directives/button';
import { APP_ICONS } from './icons';
import { InrPipe } from './pipes/inr-pipe';

const COMPONENTS = [
  AuthShell,
  BottomSheet,
  Card,
  Chip,
  DataTable,
  EmptyState,
  ErrorState,
  ImageBox,
  OrderStatusBadge,
  OrderTypeBadge,
  Price,
  QuantityStepper,
  ReconnectingBanner,
  Skeleton,
  Spinner,
  VegMarker,
  ButtonDirective,
  IconButtonDirective,
  InrPipe,
];

const REEXPORTS = [
  CommonModule,
  ReactiveFormsModule,
  NgOptimizedImage,
  A11yModule,
  DialogModule,
  DragDropModule,
  LayoutModule,
  OverlayModule,
  ...APP_ICONS,
];

/**
 * UI kit shared by the guest, kitchen, waiter and admin apps. Import it in every feature module. It declares/exports the
 * components, directives and pipes above and re-exports CommonModule, ReactiveFormsModule, NgOptimizedImage and the
 * CDK modules used across the apps.
 */
@NgModule({
  declarations: [...COMPONENTS],
  imports: [...REEXPORTS],
  exports: [...COMPONENTS, ...REEXPORTS],
})
export class SharedModule {}
