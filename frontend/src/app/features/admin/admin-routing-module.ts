import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import {
  adminAuthGuard,
  adminGuestOnlyGuard,
  ownerGuard,
  passwordChangeGuard,
} from '../../core/auth/auth.guards';
import { ChangePasswordPage } from './auth/change-password-page';
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
      { path: '', pathMatch: 'full', component: DashboardPage, title: 'Dashboard · DineIn admin' },
      {
        path: 'orders',
        loadChildren: () => import('./orders/orders-module').then((m) => m.OrdersModule),
      },
      {
        path: 'menu',
        loadChildren: () => import('./catalog/catalog-module').then((m) => m.CatalogModule),
      },
      {
        path: 'tables',
        loadChildren: () => import('./tables/tables-module').then((m) => m.TablesModule),
      },
      {
        path: 'reports',
        canActivate: [ownerGuard],
        loadChildren: () => import('./reports/reports-module').then((m) => m.ReportsModule),
      },
      {
        path: 'settings',
        canActivate: [ownerGuard],
        loadChildren: () => import('./settings/settings-module').then((m) => m.SettingsModule),
      },
      {
        path: 'staff',
        canActivate: [ownerGuard],
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
