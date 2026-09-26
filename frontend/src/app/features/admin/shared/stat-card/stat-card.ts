import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** KPI tile: label, big value, optional hint. */
@Component({
  selector: 'app-stat-card',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'block min-w-0 rounded-card border border-line bg-surface p-4 shadow-card',
  },
  templateUrl: './stat-card.html',
})
export class StatCard {
  readonly label = input.required<string>();
  readonly value = input.required<string | number>();
  readonly hint = input('');
  readonly testId = input<string | null>(null);
}
