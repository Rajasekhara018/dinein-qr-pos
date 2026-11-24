import type { HqChartNode } from './hq-chart.options';

/** One hierarchy node in the shape ApexCharts' partition charts (icicle, sunburst, nested treemap) read. */
export interface HqApexNode {
  x: string;
  y: number;
  color?: string;
  children?: HqApexNode[];
}

/**
 * A {@link HqChartNode} tree as ApexCharts reads it. Only the top-level branches get a colour (their categorical slot, or their
 * position). Use x/y with explicit totals so the non-axis data parser retains branch nodes before the partition renderer runs.
 * ApexCharts tints each deeper level from its parent, so a branch and everything under it read as one entity.
 */
export function hqChartNodesToApex(nodes: readonly HqChartNode[], colorOf: (slot: number | undefined, index: number) => string): HqApexNode[] {
  const copy = (node: HqChartNode): HqApexNode => ({
    x: node.name,
    y: hqChartNodeTotal(node),
    ...(node.children?.length ? { children: node.children.map(copy) } : {}),
  });
  return nodes.map((node, i) => ({ ...copy(node), color: colorOf(node.slot, i) }));
}

/** The total of a node: its own value, else the sum of its children. */
export function hqChartNodeTotal(node: HqChartNode): number {
  if (typeof node.value === 'number') {
    return node.value;
  }
  return (node.children ?? []).reduce((sum, child) => sum + hqChartNodeTotal(child), 0);
}

/** Every node of a tree with its depth and path, parents first: the table view of a hierarchy. */
export function hqChartNodeRows(nodes: readonly HqChartNode[], path: readonly string[] = []): { path: string; level: number; value: number }[] {
  return nodes.flatMap((node) => {
    const here = [...path, node.name];
    return [
      { path: here.join(' › '), level: here.length, value: hqChartNodeTotal(node) },
      ...hqChartNodeRows(node.children ?? [], here),
    ];
  });
}

/** One step of a waterfall as a screen describes it: a signed change, or a running total. */
export interface HqWaterfallStep {
  label: string;
  /** Signed change of the running total (omit for a total row). */
  delta?: number;
  /** 'subtotal' = sum since the previous total; 'total' = sum from zero. */
  total?: 'subtotal' | 'total';
}

/** Waterfall steps as the data of a `waterfall` series. */
export function hqWaterfallData(steps: readonly HqWaterfallStep[]): { x: string; y?: number; isSubtotal?: boolean; isTotal?: boolean }[] {
  return steps.map((step) =>
    step.total === 'total' ? { x: step.label, isTotal: true }
      : step.total === 'subtotal' ? { x: step.label, isSubtotal: true }
        : { x: step.label, y: step.delta ?? 0 });
}

/** The running total after each waterfall step (the table view of a waterfall). */
export function hqWaterfallRunning(steps: readonly HqWaterfallStep[]): { label: string; change: number | null; running: number }[] {
  let running = 0;
  let sinceCut = 0;
  return steps.map((step) => {
    if (step.total) {
      const value = step.total === 'total' ? running : sinceCut;
      sinceCut = 0;
      return { label: step.label, change: null, running: value };
    }
    running += step.delta ?? 0;
    sinceCut += step.delta ?? 0;
    return { label: step.label, change: step.delta ?? 0, running };
  });
}

/** Five-number summary (min, q1, median, q3, max) of raw observations, for boxPlot data and table views. */
export function hqFiveNumberSummary(values: readonly number[]): [number, number, number, number, number] | null {
  const sorted = values.filter((v) => Number.isFinite(v)).sort((a, b) => a - b);
  if (!sorted.length) {
    return null;
  }
  const at = (p: number): number => {
    const index = (sorted.length - 1) * p;
    const low = Math.floor(index);
    const high = Math.ceil(index);
    return sorted[low] + (sorted[high] - sorted[low]) * (index - low);
  };
  return [sorted[0], at(0.25), at(0.5), at(0.75), sorted[sorted.length - 1]];
}
