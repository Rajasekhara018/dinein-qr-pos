import { ChangeDetectionStrategy, Component } from '@angular/core';
import { HqChartGallery } from 'hq-shared-lib';

@Component({
  selector: 'app-chart-gallery',
  standalone: true,
  imports: [HqChartGallery],
  template: '<hq-chart-gallery />',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChartGalleryPage {}
