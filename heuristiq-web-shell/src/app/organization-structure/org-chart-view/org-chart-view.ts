import {
  AfterViewInit, Component, ElementRef, Input, NgZone, OnChanges, OnDestroy, SimpleChanges, ViewChild, ViewEncapsulation,
} from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { OrgChart } from 'd3-org-chart';
import {
  CHART_CHILDREN_MARGIN, CHART_EXPORT_BACKGROUND, CHART_EXPORT_SCALE, CHART_NODE_WIDTH, CHART_SIBLINGS_MARGIN,
} from '../org-structure.constants';
import { ChartCardLabels, ChartNodeData, chartCardHtml, chartNodeHeight } from './org-chart-view.helpers';

/**
 * The organization drawn as a chart you can zoom (mouse wheel or the buttons), drag to move around, fold and unfold
 * by branch, switch between vertical and horizontal, and save as an image. Drawing is done by d3-org-chart, outside
 * Angular's change detection so panning and zooming stay smooth.
 */
@Component({
  selector: 'app-org-chart-view',
  standalone: false,
  templateUrl: './org-chart-view.html',
  styleUrl: './org-chart-view.scss',
  // The chart library inserts the cards' HTML itself, so the card styles must not be scoped to this component.
  encapsulation: ViewEncapsulation.None,
})
export class OrgChartView implements AfterViewInit, OnChanges, OnDestroy {
  @Input() nodes: ChartNodeData[] = [];
  /** Height of the drawing area in pixels. */
  @Input() height = 560;

  @ViewChild('host', { static: true }) host!: ElementRef<HTMLElement>;

  layout: 'top' | 'left' = 'top';

  private chart: OrgChart<ChartNodeData> | null = null;
  private resizeObserver: ResizeObserver | null = null;

  constructor(private zone: NgZone, private translate: TranslateService) {}

  ngAfterViewInit(): void {
    this.zone.runOutsideAngular(() => {
      this.draw(true);
      this.resizeObserver = new ResizeObserver(() => {
        if (this.chart && this.host.nativeElement.clientWidth) {
          this.chart.svgWidth(this.host.nativeElement.clientWidth).render();
        }
      });
      this.resizeObserver.observe(this.host.nativeElement);
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['nodes'] && this.chart) this.zone.runOutsideAngular(() => this.draw(false));
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    this.chart?.clear();
    this.chart = null;
  }

  zoomIn(): void { this.chart?.zoomIn(); }
  zoomOut(): void { this.chart?.zoomOut(); }
  fit(): void { this.chart?.fit(); }
  expandAll(): void { this.chart?.expandAll().fit(); }
  collapseAll(): void { this.chart?.collapseAll().fit(); }

  toggleLayout(): void {
    this.layout = this.layout === 'top' ? 'left' : 'top';
    this.zone.runOutsideAngular(() => this.chart?.layout(this.layout).render().fit());
  }

  exportImage(): void {
    this.chart?.exportImg({ full: true, scale: CHART_EXPORT_SCALE, backgroundColor: CHART_EXPORT_BACKGROUND });
  }

  private draw(fitToScreen: boolean): void {
    if (!this.nodes.length) {
      this.chart?.clear();
      this.chart = null;
      return;
    }
    const labels: ChartCardLabels = {
      stateLabel: (state) => this.translate.instant('orgStructure.tree.state.' + state),
      moreLabel: (count) => this.translate.instant('orgStructure.tree.morePeople', { count }),
      headLabel: this.translate.instant('orgStructure.chart.headBadge'),
    };
    const created = !this.chart;
    this.chart ??= new OrgChart<ChartNodeData>()
      .container(this.host.nativeElement)
      .nodeWidth(() => CHART_NODE_WIDTH)
      .nodeHeight((node) => chartNodeHeight(node.data))
      .childrenMargin(() => CHART_CHILDREN_MARGIN)
      .siblingsMargin(() => CHART_SIBLINGS_MARGIN)
      .compact(false)
      .layout(this.layout);
    this.chart
      .svgWidth(this.host.nativeElement.clientWidth || CHART_NODE_WIDTH)
      .svgHeight(this.height)
      .nodeContent((node) => chartCardHtml(node.data, labels))
      .data(this.nodes)
      .render();
    if (fitToScreen || created) this.chart.fit();
  }
}
