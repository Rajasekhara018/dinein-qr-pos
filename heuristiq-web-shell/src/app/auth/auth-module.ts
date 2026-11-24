import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared-module';
import { AuthRoutingModule } from './auth-routing-module';
import { Login } from './login/login';
import { ForgotPassword } from './forgot-password/forgot-password';
import { Register } from './register/register';
import { PointerTilt } from './login/pointer-tilt';

// ChangePassword is NOT declared here -- it moved to a lazy-loaded standalone route directly
// under Layout in app-routing-module.ts, so it renders inside the header/sidebar shell. It used
// to live alongside the pre-auth screens here, which meant a signed-in user clicking "Change
// Password" got kicked out of the app shell entirely even though the page requires auth.
@NgModule({
  declarations: [Login, ForgotPassword, Register, PointerTilt],
  imports: [SharedModule, AuthRoutingModule],
})
export class AuthModule {}
