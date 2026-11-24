import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HqChart } from './hq-chart';
import { HqChartCard } from './hq-chart-card';
import { HqDateRangePicker } from './hq-date-range';
import { HqDateRange, hqDateRangeFor } from './hq-date-range.helpers';
import { HqGalleryFamily, hqGallerySamples } from './hq-chart-gallery.samples';

@Component({
  selector: 'hq-chart-gallery',
  standalone: true,
  imports: [FormsModule, HqChart, HqChartCard, HqDateRangePicker],
  templateUrl: './hq-chart-gallery.html',
  styleUrl: './hq-chart-gallery.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HqChartGallery {
  range = hqDateRangeFor('last30Days');
  items = hqGallerySamples(this.range);
  search = '';
  family: 'all' | HqGalleryFamily = 'all';
  readonly filters = [
    { value: 'all', label: 'All charts' },
    { value: 'axis', label: 'Axis charts' },
    { value: 'nonAxis', label: 'Composition & hierarchy' },
  ] as const;

  get visibleItems() {
    const query = this.search.trim().toLowerCase();
    return this.items.filter(item => (this.family === 'all' || item.family === this.family)
      && (item.title + ' ' + item.type + ' ' + item.description).toLowerCase().includes(query));
  }

  changeRange(range: HqDateRange): void {
    this.range = range;
    this.items = hqGallerySamples(range);
  }
}
