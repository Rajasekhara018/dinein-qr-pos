import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * `/admin/orders/new`: a counter order placed by an owner or manager. The same staff ordering flow as the waiter
 * screen (`StaffOrderingModule`), placed with `POST /api/v1/admin/orders`.
 */
@Component({
  selector: 'app-admin-new-order-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './new-order-page.html',
})
export class AdminNewOrderPage {}
