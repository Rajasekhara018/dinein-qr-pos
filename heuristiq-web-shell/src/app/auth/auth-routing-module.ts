import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { Login } from './login/login';
import { ForgotPassword } from './forgot-password/forgot-password';
import { Register } from './register/register';

// change-password lives under Layout's children in app-routing-module.ts instead (it requires
// auth and should render inside the header/sidebar shell, unlike these pre-auth screens).
const routes: Routes = [
  { path: 'login', component: Login },
  { path: 'register', component: Register },
  { path: 'forgot-password', component: ForgotPassword },
  { path: '', redirectTo: 'login', pathMatch: 'full' },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class AuthRoutingModule {}
