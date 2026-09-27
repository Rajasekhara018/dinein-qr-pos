import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Static "forgot password" guidance page. There is no self-service reset flow in the backend today (owner/manager
 * accounts are created and reset by another owner/staff admin, see `docs/ux-backend-requests.md`) — this page
 * matches the login/change-password shell and tells the admin who to contact, rather than inventing a client-only
 * reset request that the backend can't honour.
 */
@Component({
  selector: 'app-admin-forgot-password-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './forgot-password-page.html',
})
export class ForgotPasswordPage {}
