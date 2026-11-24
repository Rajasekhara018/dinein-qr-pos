import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { NotFound } from './pages/not-found/not-found';

/** Each app is a lazily loaded feature module, so it ships only its own code. */
const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'menu' },
  {
    path: 'menu',
    loadChildren: () => import('./features/guest/guest-module').then((m) => m.GuestModule),
  },
  {
    path: 'kitchen',
    loadChildren: () => import('./features/kitchen/kitchen-module').then((m) => m.KitchenModule),
  },
  {
    path: 'waiter',
    loadChildren: () => import('./features/waiter/waiter-module').then((m) => m.WaiterModule),
  },
  {
    path: 'admin',
    loadChildren: () => import('./features/admin/admin-module').then((m) => m.AdminModule),
  },
  {
    // Customer-facing "order ready" screen for the dining area; public, no login (see DisplayPage).
    path: 'display',
    loadChildren: () => import('./features/display/display-module').then((m) => m.DisplayModule),
  },
  {
    // Internal restaurant onboarding + platform-wide restaurant list; key-authenticated, not staff login
    // (see PlatformApi/PlatformPrefs). Lazy like every other feature module.
    path: 'platform',
    loadChildren: () => import('./features/platform/platform-module').then((m) => m.PlatformModule),
  },
  {
    // Dev-only UI kit showcase; guarded by devOnlyGuard (see design-system/dev-only.guard.ts) and lazy, so it
    // never reaches the production initial bundle.
    path: 'design-system',
    loadChildren: () =>
      import('./features/design-system/design-system-module').then((m) => m.DesignSystemModule),
  },
  { path: '**', component: NotFound, title: 'Page not found' },
];

@NgModule({
  imports: [
    RouterModule.forRoot(routes, {
      bindToComponentInputs: true,
      scrollPositionRestoration: 'enabled',
      anchorScrolling: 'enabled',
    }),
  ],
  exports: [RouterModule],
})
export class AppRoutingModule {}
