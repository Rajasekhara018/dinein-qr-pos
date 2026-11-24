/** The part of d3-org-chart (which ships no types) that the org chart preview uses. */
declare module 'd3-org-chart' {
  /** A node as the library hands it to the callbacks: our data under `data`. */
  interface OrgChartNode<T> {
    data: T;
  }

  export class OrgChart<T> {
    container(selector: string | HTMLElement): this;
    data(rows: T[]): this;
    svgWidth(width: number): this;
    svgHeight(height: number): this;
    nodeWidth(fn: (node: OrgChartNode<T>) => number): this;
    nodeHeight(fn: (node: OrgChartNode<T>) => number): this;
    childrenMargin(fn: (node: OrgChartNode<T>) => number): this;
    siblingsMargin(fn: (node: OrgChartNode<T>) => number): this;
    compact(value: boolean): this;
    layout(direction: 'top' | 'left' | 'right' | 'bottom'): this;
    nodeContent(fn: (node: OrgChartNode<T>) => string): this;
    render(): this;
    fit(): this;
    zoomIn(): void;
    zoomOut(): void;
    expandAll(): this;
    collapseAll(): this;
    exportImg(options?: { full?: boolean; scale?: number; backgroundColor?: string }): void;
    clear(): void;
  }
}
