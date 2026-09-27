import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { SharedModule } from '../../shared/shared-module';
import { AdminRoutingModule } from './admin-routing-module';
import { ChangePasswordPage } from './auth/change-password-page';
import { ForgotPasswordPage } from './auth/forgot-password-page';
import { LoginPage } from './auth/login-page';
import { DashboardPage } from './dashboard/dashboard-page';
import { AdminSharedModule } from './shared/admin-shared-module';
import { CommandPalette } from './shell/command-palette/command-palette';
import { AdminShell } from './shell/admin-shell';
import { NotificationBell } from './shell/notification-bell';

/**
 * Admin app (`/admin/**`), lazily loaded: shell, sign-in pages and dashboard. Sections (menu, orders, tables,
 * reports, settings, staff) are lazy child modules that import `AdminSharedModule`.
 */
@NgModule({
  declarations: [
    AdminShell,
    NotificationBell,
    CommandPalette,
    LoginPage,
    ChangePasswordPage,
    ForgotPasswordPage,
    DashboardPage,
  ],
  imports: [SharedModule, ReactiveFormsModule, DragDropModule, AdminSharedModule, AdminRoutingModule],
})
export class AdminModule {}
