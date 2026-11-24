import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Shown when the QR token is invalid/inactive or there is no guest session. */
@Component({
  selector: 'app-scan-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './scan-page.html',
})
export class ScanPage {}
