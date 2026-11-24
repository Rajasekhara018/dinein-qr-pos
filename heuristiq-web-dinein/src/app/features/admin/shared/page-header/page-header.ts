import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Page title row: `<app-page-header title="Items" subtitle="…"><button appButton>Add</button></app-page-header>` */
@Component({
  selector: 'app-page-header',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'mb-4 flex flex-wrap items-end justify-between gap-3 sm:mb-6' },
  templateUrl: './page-header.html',
})
export class PageHeader {
  readonly title = input.required<string>();
  readonly subtitle = input('');
}
