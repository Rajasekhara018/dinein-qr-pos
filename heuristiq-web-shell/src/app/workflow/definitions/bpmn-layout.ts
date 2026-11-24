import { BpmnGraph } from '../workflow.models';

/**
 * Lays a BPMN graph out left to right for drawing. Flowable processes need no diagram data, so the
 * file usually has no coordinates; this places the nodes in columns by their distance from the
 * start (cycles are tolerated: an edge back to an earlier node is drawn underneath).
 */

export type NodeShape = 'event' | 'endEvent' | 'gateway' | 'task';

export interface LaidOutNode {
  id: string;
  name: string;
  type: string;
  shape: NodeShape;
  x: number;
  y: number;
  width: number;
  height: number;
  /** The name split over at most two lines. */
  lines: string[];
}

export interface LaidOutEdge {
  id: string;
  /** SVG path data. */
  path: string;
  label: string;
  labelX: number;
  labelY: number;
}

export interface GraphLayout {
  nodes: LaidOutNode[];
  edges: LaidOutEdge[];
  width: number;
  height: number;
}

const MARGIN = 32;
const GAP_X = 70;
const GAP_Y = 36;
const LABEL_CHARS = 16;

const SIZES: Record<NodeShape, { w: number; h: number }> = {
  event: { w: 40, h: 40 },
  endEvent: { w: 40, h: 40 },
  gateway: { w: 52, h: 52 },
  task: { w: 148, h: 64 },
};

export function shapeOf(type: string): NodeShape {
  if (type === 'startEvent') return 'event';
  if (type === 'endEvent') return 'endEvent';
  if (type.endsWith('Gateway')) return 'gateway';
  return 'task';
}

export function layoutGraph(graph: BpmnGraph): GraphLayout {
  const ids = graph.nodes.map((n) => n.id);
  const known = new Set(ids);
  const edges = graph.edges.filter((e) => known.has(e.source) && known.has(e.target));

  const out = new Map<string, string[]>();
  const incoming = new Map<string, number>();
  ids.forEach((id) => { out.set(id, []); incoming.set(id, 0); });
  edges.forEach((e) => { out.get(e.source)!.push(e.target); incoming.set(e.target, (incoming.get(e.target) ?? 0) + 1); });

  // Depth-first from the starts, dropping edges that point back at a node on the current path.
  const starts = ids.filter((id) => (incoming.get(id) ?? 0) === 0);
  const roots = starts.length > 0 ? starts : ids.slice(0, 1);
  const state = new Map<string, 'open' | 'done'>();
  const back = new Set<string>();
  const order: string[] = [];
  const visit = (id: string): void => {
    state.set(id, 'open');
    for (const next of out.get(id) ?? []) {
      if (state.get(next) === 'open') back.add(`${id}>${next}`);
      else if (!state.has(next)) visit(next);
    }
    state.set(id, 'done');
    order.push(id);
  };
  roots.forEach((id) => { if (!state.has(id)) visit(id); });
  ids.forEach((id) => { if (!state.has(id)) visit(id); });
  order.reverse(); // reverse post-order is a topological order of the acyclic part

  const layer = new Map<string, number>();
  order.forEach((id) => layer.set(id, 0));
  order.forEach((id) => {
    for (const next of out.get(id) ?? []) {
      if (back.has(`${id}>${next}`)) continue;
      layer.set(next, Math.max(layer.get(next) ?? 0, (layer.get(id) ?? 0) + 1));
    }
  });

  const columns = new Map<number, string[]>();
  order.forEach((id) => {
    const l = layer.get(id) ?? 0;
    if (!columns.has(l)) columns.set(l, []);
    columns.get(l)!.push(id);
  });

  const byId = new Map(graph.nodes.map((n) => [n.id, n]));
  const columnCount = columns.size === 0 ? 0 : Math.max(...columns.keys()) + 1;
  const columnHeights: number[] = [];
  const columnWidths: number[] = [];
  for (let c = 0; c < columnCount; c++) {
    const members = columns.get(c) ?? [];
    const shapes = members.map((id) => SIZES[shapeOf(byId.get(id)!.type)]);
    columnWidths[c] = shapes.length ? Math.max(...shapes.map((s) => s.w)) : 0;
    columnHeights[c] = shapes.reduce((sum, s) => sum + s.h, 0) + Math.max(0, shapes.length - 1) * GAP_Y;
  }
  const tallest = columnHeights.length ? Math.max(...columnHeights) : 0;

  const placed = new Map<string, LaidOutNode>();
  let x = MARGIN;
  for (let c = 0; c < columnCount; c++) {
    let y = MARGIN + (tallest - columnHeights[c]) / 2;
    for (const id of columns.get(c) ?? []) {
      const n = byId.get(id)!;
      const shape = shapeOf(n.type);
      const size = SIZES[shape];
      placed.set(id, {
        id, name: n.name, type: n.type, shape,
        x: x + (columnWidths[c] - size.w) / 2, y, width: size.w, height: size.h,
        lines: wrap(n.name),
      });
      y += size.h + GAP_Y;
    }
    x += columnWidths[c] + GAP_X;
  }

  const width = Math.max(x - GAP_X + MARGIN, MARGIN * 2);
  const baseHeight = tallest + MARGIN * 2;
  let height = baseHeight;

  const laidEdges: LaidOutEdge[] = edges.map((e) => {
    const a = placed.get(e.source)!;
    const b = placed.get(e.target)!;
    const label = (e.name || e.condition || '').trim();
    if (back.has(`${e.source}>${e.target}`)) {
      // Loop back: leave from the bottom of the source, run underneath, enter the bottom of the target.
      const ax = a.x + a.width / 2, ay = a.y + a.height;
      const bx = b.x + b.width / 2, by = b.y + b.height;
      const low = Math.max(ay, by) + 28;
      height = Math.max(height, low + MARGIN);
      return { id: e.id, path: `M${ax},${ay} L${ax},${low} L${bx},${low} L${bx},${by}`, label, labelX: (ax + bx) / 2, labelY: low - 6 };
    }
    const ax = a.x + a.width, ay = a.y + a.height / 2;
    const bx = b.x, by = b.y + b.height / 2;
    const mid = ax + (bx - ax) / 2;
    return { id: e.id, path: `M${ax},${ay} L${mid},${ay} L${mid},${by} L${bx},${by}`, label, labelX: mid, labelY: Math.min(ay, by) - 6 };
  });

  return { nodes: [...placed.values()], edges: laidEdges, width, height };
}

/** Splits a name over at most two lines of about LABEL_CHARS characters. */
function wrap(text: string): string[] {
  const words = (text || '').split(/\s+/).filter(Boolean);
  const lines: string[] = [];
  let line = '';
  for (const word of words) {
    if ((line + ' ' + word).trim().length > LABEL_CHARS && line) {
      lines.push(line);
      line = word;
    } else {
      line = (line + ' ' + word).trim();
    }
  }
  if (line) lines.push(line);
  if (lines.length > 2) {
    const rest = lines.slice(1).join(' ');
    return [lines[0], rest.length > LABEL_CHARS ? rest.slice(0, LABEL_CHARS - 1) + '…' : rest];
  }
  return lines;
}
