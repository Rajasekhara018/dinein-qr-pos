import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-not-found',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './not-found.html',
})
export class NotFound {}
