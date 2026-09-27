import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import {
  adminAuthGuard,
  adminGuestOnlyGuard,
  ownerGuard,
  passwordChangeGuard,
} from '../../core/auth/auth.guards';
import { ChangePasswordPage } from './auth/change-password-page';
import { ForgotPasswordPage } from './auth/forgot-password-page';
import { LoginPage } from './auth/login-page';
import { DashboardPage } from './dashboard/dashboard-page';
import { AdminShell } from './shell/admin-shell';

/**
 * `/admin/**`. Login and change-password are outside the shell; everything else requires a session and a changed
 * bootstrap password. Sections are lazy child modules; owner-only ones are guarded by `ownerGuard` (and hidden from
 * managers' navigation).
 */
export const ADMIN_ROUTES: Routes = [
  {
    path: 'login',
    component: LoginPage,
    canActivate: [adminGuestOnlyGuard],
    title: 'Sign in · DineIn admin',
  },
  {
    path: 'forgot-password',
    component: ForgotPasswordPage,
    canActivate: [adminGuestOnlyGuard],
    title: 'Forgot password · DineIn admin',
  },
  {
    path: 'change-password',
    component: ChangePasswordPage,
    canActivate: [adminAuthGuard],
    title: 'Change password · DineIn admin',
  },
  {
    path: '',
    component: AdminShell,
    canActivate: [adminAuthGuard, passwordChangeGuard],
    canActivateChild: [adminAuthGuard, passwordChangeGuard],
    children: [
      {
        path: '',
        pathMatch: 'full',
        component: DashboardPage,
        title: 'Dashboard · DineIn admin',
        data: { breadcrumb: 'Dashboard' },
      },
      {
        path: 'orders',
        data: { breadcrumb: 'Operations' },
        loadChildren: () => import('./orders/orders-module').then((m) => m.OrdersModule),
      },
      {
        path: 'menu',
        data: { breadcrumb: 'Menu' },
        loadChildren: () => import('./catalog/catalog-module').then((m) => m.CatalogModule),
      },
      {
        path: 'tables',
        data: { breadcrumb: 'Operations' },
        loadChildren: () => import('./tables/tables-module').then((m) => m.TablesModule),
      },
      {
        path: 'reports',
        canActivate: [ownerGuard],
        data: { breadcrumb: 'Business' },
        loadChildren: () => import('./reports/reports-module').then((m) => m.ReportsModule),
      },
      {
        path: 'settings',
        canActivate: [ownerGuard],
        data: { breadcrumb: 'Business' },
        loadChildren: () => import('./settings/settings-module').then((m) => m.SettingsModule),
      },
      {
        path: 'staff',
        canActivate: [ownerGuard],
        data: { breadcrumb: 'Business' },
        loadChildren: () => import('./staff/staff-module').then((m) => m.StaffModule),
      },
      { path: '**', redirectTo: '' },
    ],
  },
];

@NgModule({
  imports: [RouterModule.forChild(ADMIN_ROUTES)],
  exports: [RouterModule],
})
export class AdminRoutingModule {}
