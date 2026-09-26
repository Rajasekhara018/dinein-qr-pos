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
    path: 'admin',
    loadChildren: () => import('./features/admin/admin-module').then((m) => m.AdminModule),
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
