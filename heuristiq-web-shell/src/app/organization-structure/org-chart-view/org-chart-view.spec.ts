import { ElementRef, NgZone, SimpleChange } from '@angular/core';
import { vi } from 'vitest';
import { CHART_EXPORT_BACKGROUND, CHART_EXPORT_SCALE } from '../org-structure.constants';
import { ChartNodeData } from './org-chart-view.helpers';
import { OrgChartView } from './org-chart-view';

/** The slice of the library's chart the tests watch; the real chart runs against a real element. */
interface WatchedChart {
  zoomIn(): void; zoomOut(): void; fit(): unknown; expandAll(): unknown; collapseAll(): unknown;
  layout(direction: string): unknown; exportImg(options?: unknown): void; clear(): void; render(): unknown;
  svgWidth(width: number): unknown;
}

const node = (id: string, over: Partial<ChartNodeData> = {}): ChartNodeData =>
  ({ id, parentId: null, name: 'Dept ' + id, code: id.toUpperCase(), state: 'live', people: [], morePeople: 0, ...over }) as ChartNodeData;

describe('OrgChartView', () => {
  let observerCallback: () => void;
  let disconnect: ReturnType<typeof vi.fn>;
  let element: HTMLDivElement;

  function create(nodes: ChartNodeData[] = [node('a')], width = 800) {
    const zone = { runOutsideAngular: vi.fn(<T>(fn: () => T) => fn()) } as unknown as NgZone;
    const translate = { instant: vi.fn((key: string, params?: object) => key + (params ? JSON.stringify(params) : '')) };
    const view = new OrgChartView(zone, translate as never);
    element = document.createElement('div');
    Object.defineProperty(element, 'clientWidth', { configurable: true, get: () => width, set: (v: number) => (width = v) });
    document.body.appendChild(element);
    view.host = new ElementRef(element);
    view.nodes = nodes;
    return { view, zone, translate, setWidth: (w: number) => (width = w) };
  }

  const chartOf = (view: OrgChartView) => (view as unknown as { chart: WatchedChart | null }).chart;

  // jsdom has no SVG geometry; d3's animated fit/zoom reads these when its timers fire, so give it inert values.
  beforeAll(() => {
    Object.defineProperty(SVGElement.prototype, 'transform', { configurable: true, get: () => ({ baseVal: { consolidate: () => null } }) });
    Object.defineProperty(SVGElement.prototype, 'width', { configurable: true, get: () => ({ baseVal: { value: 800 } }) });
    Object.defineProperty(SVGElement.prototype, 'height', { configurable: true, get: () => ({ baseVal: { value: 560 } }) });
  });

  // Not removed afterwards: d3's animation timers fire after the last test, and each spec file has its own jsdom.

  beforeEach(() => {
    disconnect = vi.fn();
    vi.stubGlobal('ResizeObserver', class {
      constructor(cb: () => void) { observerCallback = cb; }
      observe = vi.fn();
      disconnect = disconnect;
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    element?.remove();
  });

  it('draws the chart outside Angular into the host, with translated card text', () => {
    const { view, zone } = create([node('a', { state: 'new', people: [{ employeeId: 'e', name: 'Asha', position: 'Lead', pending: false, isHead: true }] as never })]);
    view.ngAfterViewInit();
    expect(zone.runOutsideAngular).toHaveBeenCalled();
    expect(chartOf(view)).not.toBeNull();
    expect(element.querySelector('svg')).not.toBeNull();
    expect(element.innerHTML).toContain('Dept a');
    expect(element.innerHTML).toContain('Asha');
    expect(element.innerHTML).toContain('orgStructure.chart.headBadge');
    expect(element.innerHTML).toContain('orgStructure.tree.state.new');
  });

  it('shows a "more people" line from the translation service', () => {
    const { view } = create([node('a', { morePeople: 3 })]);
    view.ngAfterViewInit();
    expect(element.innerHTML).toContain('orgStructure.tree.morePeople{"count":3}');
  });

  it('does not draw an empty chart', () => {
    const { view } = create([]);
    view.ngAfterViewInit();
    expect(chartOf(view)).toBeNull();
    expect(element.querySelector('svg')).toBeNull();
  });

  it('works when the host has no width yet', () => {
    const { view } = create([node('a')], 0);
    view.ngAfterViewInit();
    expect(chartOf(view)).not.toBeNull();
  });

  it('redraws when the nodes change, keeping the same chart', () => {
    const { view } = create();
    view.ngAfterViewInit();
    const chart = chartOf(view);
    view.nodes = [node('a'), node('b', { parentId: 'a' })];
    view.ngOnChanges({ nodes: new SimpleChange([], view.nodes, false) });
    expect(chartOf(view)).toBe(chart);
    expect(element.innerHTML).toContain('Dept b');
  });

  it('ignores other input changes and changes before the first draw', () => {
    const { view } = create();
    view.ngOnChanges({ nodes: new SimpleChange(null, view.nodes, true) });
    expect(chartOf(view)).toBeNull();
    view.ngAfterViewInit();
    const chart = chartOf(view)!;
    const render = vi.spyOn(chart, 'render');
    view.ngOnChanges({ height: new SimpleChange(1, 2, false) });
    expect(render).not.toHaveBeenCalled();
  });

  it('drops the chart when the nodes become empty', () => {
    const { view } = create();
    view.ngAfterViewInit();
    const clear = vi.spyOn(chartOf(view)!, 'clear');
    view.nodes = [];
    view.ngOnChanges({ nodes: new SimpleChange([node('a')], [], false) });
    expect(clear).toHaveBeenCalled();
    expect(chartOf(view)).toBeNull();
  });

  it('re-renders to the new width when the host is resized', () => {
    const { view, setWidth } = create();
    view.ngAfterViewInit();
    const chart = chartOf(view)!;
    const svgWidth = vi.spyOn(chart, 'svgWidth');
    setWidth(1024);
    observerCallback();
    expect(svgWidth).toHaveBeenCalledWith(1024);
  });

  it('skips the resize render when the host has no width or the chart is gone', () => {
    const { view, setWidth } = create();
    view.ngAfterViewInit();
    const chart = chartOf(view)!;
    const svgWidth = vi.spyOn(chart, 'svgWidth');
    setWidth(0);
    observerCallback();
    expect(svgWidth).not.toHaveBeenCalled();
    view.ngOnDestroy();
    setWidth(500);
    observerCallback();
    expect(svgWidth).not.toHaveBeenCalled();
  });

  it('forwards zoom, fit, expand and collapse to the chart', () => {
    const { view } = create();
    view.ngAfterViewInit();
    const chart = chartOf(view)!;
    const zoomIn = vi.spyOn(chart, 'zoomIn').mockImplementation(() => undefined);
    const zoomOut = vi.spyOn(chart, 'zoomOut').mockImplementation(() => undefined);
    const fit = vi.spyOn(chart, 'fit');
    const expandAll = vi.spyOn(chart, 'expandAll');
    const collapseAll = vi.spyOn(chart, 'collapseAll');
    view.zoomIn();
    view.zoomOut();
    view.fit();
    view.expandAll();
    view.collapseAll();
    expect(zoomIn).toHaveBeenCalledTimes(1);
    expect(zoomOut).toHaveBeenCalledTimes(1);
    expect(expandAll).toHaveBeenCalledTimes(1);
    expect(collapseAll).toHaveBeenCalledTimes(1);
    expect(fit).toHaveBeenCalled();
  });

  it('toggles between vertical and horizontal layout', () => {
    const { view } = create();
    view.ngAfterViewInit();
    const layout = vi.spyOn(chartOf(view)!, 'layout');
    view.toggleLayout();
    expect(view.layout).toBe('left');
    expect(layout).toHaveBeenLastCalledWith('left');
    view.toggleLayout();
    expect(view.layout).toBe('top');
    expect(layout).toHaveBeenLastCalledWith('top');
  });

  it('exports the whole chart as an image', () => {
    const { view } = create();
    view.ngAfterViewInit();
    const exportImg = vi.spyOn(chartOf(view)!, 'exportImg').mockImplementation(() => undefined);
    view.exportImage();
    expect(exportImg).toHaveBeenCalledWith({ full: true, scale: CHART_EXPORT_SCALE, backgroundColor: CHART_EXPORT_BACKGROUND });
  });

  it('is safe to use the tools before a chart exists', () => {
    const { view } = create([]);
    view.ngAfterViewInit();
    expect(() => {
      view.zoomIn(); view.zoomOut(); view.fit(); view.expandAll(); view.collapseAll(); view.toggleLayout(); view.exportImage();
    }).not.toThrow();
    expect(view.layout).toBe('left');
  });

  it('disconnects the observer and clears the chart on destroy', () => {
    const { view } = create();
    view.ngAfterViewInit();
    const clear = vi.spyOn(chartOf(view)!, 'clear');
    view.ngOnDestroy();
    expect(disconnect).toHaveBeenCalled();
    expect(clear).toHaveBeenCalled();
    expect(chartOf(view)).toBeNull();
  });

  it('destroys cleanly when never initialised', () => {
    const { view } = create();
    expect(() => view.ngOnDestroy()).not.toThrow();
  });
});
