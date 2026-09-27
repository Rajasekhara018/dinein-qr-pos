import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { waiterAuthGuard, waiterGuestOnlyGuard } from '../../core/auth/auth.guards';
import { ActivePage } from './active/active-page';
import { WaiterLogin } from './login/waiter-login';
import { WaiterNewOrderPage } from './new-order/new-order-page';
import { WaiterOrderPage } from './order/waiter-order-page';
import { ReadyPage } from './ready/ready-page';
import { WaiterShell } from './shell/waiter-shell';
import { TablesPage } from './tables/tables-page';

/**
 * `/waiter/**` (see `WAITER_PATHS`). The login is outside the shell; every tab needs a WAITER / MANAGER / OWNER
 * session (`waiterAuthGuard`).
 */
export const WAITER_ROUTES: Routes = [
  {
    path: 'login',
    component: WaiterLogin,
    canActivate: [waiterGuestOnlyGuard],
    title: 'Waiter sign-in · DineIn',
  },
  {
    path: '',
    component: WaiterShell,
    canActivate: [waiterAuthGuard],
    canActivateChild: [waiterAuthGuard],
    children: [
      { path: '', pathMatch: 'full', component: ReadyPage, title: 'Ready · Waiter' },
      { path: 'active', component: ActivePage, title: 'Active orders · Waiter' },
      { path: 'tables', component: TablesPage, title: 'Tables · Waiter' },
      { path: 'new', component: WaiterNewOrderPage, title: 'New order · Waiter' },
      { path: 'orders/:id', component: WaiterOrderPage, title: 'Order · Waiter' },
      { path: '**', redirectTo: '' },
    ],
  },
];

@NgModule({
  imports: [RouterModule.forChild(WAITER_ROUTES)],
  exports: [RouterModule],
})
export class WaiterRoutingModule {}
