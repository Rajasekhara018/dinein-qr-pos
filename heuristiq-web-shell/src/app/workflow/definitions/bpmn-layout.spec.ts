import { describe, expect, it } from 'vitest';
import { BpmnGraph } from '../workflow.models';
import { BpmnGraphView } from './bpmn-graph-view/bpmn-graph-view';
import { layoutGraph, shapeOf } from './bpmn-layout';

const node = (id: string, type = 'userTask', name = id) => ({ id, name, type });
const edge = (id: string, source: string, target: string, extra: object = {}) => ({ id, source, target, ...extra });

describe('shapeOf', () => {
  it('maps BPMN element types onto drawing shapes', () => {
    expect(shapeOf('startEvent')).toBe('event');
    expect(shapeOf('endEvent')).toBe('endEvent');
    expect(shapeOf('exclusiveGateway')).toBe('gateway');
    expect(shapeOf('parallelGateway')).toBe('gateway');
    expect(shapeOf('userTask')).toBe('task');
    expect(shapeOf('serviceTask')).toBe('task');
  });
});

describe('layoutGraph', () => {
  const linear: BpmnGraph = {
    nodes: [node('s', 'startEvent'), node('t', 'userTask', 'Review the application'), node('e', 'endEvent')],
    edges: [edge('f1', 's', 't'), edge('f2', 't', 'e', { name: 'Done' })],
  };

  it('places nodes left to right in columns by distance from the start', () => {
    const layout = layoutGraph(linear);
    const byId = Object.fromEntries(layout.nodes.map((n) => [n.id, n]));
    expect(byId['s'].x).toBeLessThan(byId['t'].x);
    expect(byId['t'].x).toBeLessThan(byId['e'].x);
    expect(byId['t']).toMatchObject({ shape: 'task', width: 148, height: 64 });
    expect(byId['s']).toMatchObject({ shape: 'event', width: 40 });
    expect(layout.width).toBeGreaterThan(byId['e'].x + 40);
  });

  it('centres shorter columns vertically against the tallest', () => {
    const layout = layoutGraph({
      nodes: [node('s', 'startEvent'), node('a'), node('b'), node('e', 'endEvent')],
      edges: [edge('1', 's', 'a'), edge('2', 's', 'b'), edge('3', 'a', 'e'), edge('4', 'b', 'e')],
    });
    const byId = Object.fromEntries(layout.nodes.map((n) => [n.id, n]));
    expect(byId['a'].y).not.toBe(byId['b'].y);
    // the start sits at the vertical middle of the two parallel tasks
    const mid = (byId['a'].y + byId['b'].y + 64) / 2;
    expect(byId['s'].y + 20).toBeCloseTo(mid, 5);
  });

  it('routes forward edges through a midpoint and puts the label above them', () => {
    const layout = layoutGraph(linear);
    const f2 = layout.edges.find((e) => e.id === 'f2')!;
    expect(f2.path).toMatch(/^M[\d.]+,[\d.]+ L[\d.]+,[\d.]+ L[\d.]+,[\d.]+ L[\d.]+,[\d.]+$/);
    expect(f2.label).toBe('Done');
    expect(f2.labelY).toBeLessThan(100);
  });

  it('uses the condition as the label when the edge has no name, else an empty label', () => {
    const layout = layoutGraph({
      nodes: [node('a'), node('b'), node('c')],
      edges: [edge('1', 'a', 'b', { condition: '${x}' }), edge('2', 'b', 'c')],
    });
    expect(layout.edges.map((e) => e.label)).toEqual(['${x}', '']);
  });

  it('draws a loop-back edge underneath and grows the canvas to hold it', () => {
    const forward = layoutGraph({ nodes: [node('a'), node('b')], edges: [edge('1', 'a', 'b')] });
    const loop = layoutGraph({ nodes: [node('a'), node('b')], edges: [edge('1', 'a', 'b'), edge('2', 'b', 'a')] });
    const back = loop.edges.find((e) => e.id === '2')!;
    expect(back.path).toMatch(/^M[\d.]+,[\d.]+ L[\d.]+,[\d.]+ L[\d.]+,[\d.]+ L[\d.]+,[\d.]+$/);
    expect(loop.height).toBeGreaterThan(forward.height);
    const a = loop.nodes.find((n) => n.id === 'a')!;
    const b = loop.nodes.find((n) => n.id === 'b')!;
    expect(a.x).toBeLessThan(b.x);
  });

  it('ignores edges to unknown nodes', () => {
    const layout = layoutGraph({ nodes: [node('a')], edges: [edge('1', 'a', 'ghost'), edge('2', 'ghost', 'a')] });
    expect(layout.edges).toEqual([]);
    expect(layout.nodes).toHaveLength(1);
  });

  it('still places every node when the graph is one big cycle with no start', () => {
    const layout = layoutGraph({ nodes: [node('a'), node('b'), node('c')], edges: [edge('1', 'a', 'b'), edge('2', 'b', 'c'), edge('3', 'c', 'a')] });
    expect(layout.nodes).toHaveLength(3);
    expect(layout.edges).toHaveLength(3);
  });

  it('lays out disconnected nodes and an empty node list without failing', () => {
    expect(layoutGraph({ nodes: [node('a'), node('b')], edges: [] }).nodes).toHaveLength(2);
    const empty = layoutGraph({ nodes: [], edges: [] });
    expect(empty.nodes).toEqual([]);
    expect(empty.width).toBe(64);
  });

  it('wraps long names over at most two lines, shortening the second with an ellipsis', () => {
    const lines = (name: string) => layoutGraph({ nodes: [node('a', 'userTask', name)], edges: [] }).nodes[0].lines;
    expect(lines('Short')).toEqual(['Short']);
    expect(lines('Review the application')).toEqual(['Review the', 'application']);
    const long = lines('Verify every single document submitted by applicant thoroughly');
    expect(long).toHaveLength(2);
    expect(long[1].endsWith('…')).toBe(true);
    expect(long[1].length).toBeLessThanOrEqual(16);
    expect(lines('')).toEqual([]);
  });
});

describe('BpmnGraphView', () => {
  it('builds a layout only for a graph that has nodes', () => {
    const view = new BpmnGraphView();
    view.graph = null;
    view.ngOnChanges();
    expect(view.layout).toBeNull();
    view.graph = { nodes: [], edges: [] };
    view.ngOnChanges();
    expect(view.layout).toBeNull();
    view.graph = { nodes: [node('a')], edges: [] };
    view.ngOnChanges();
    expect(view.layout?.nodes).toHaveLength(1);
  });

  it('computes diamond points and gateway marks', () => {
    const view = new BpmnGraphView();
    expect(view.diamond(0, 0, 10, 20)).toBe('5,0 10,10 5,20 0,10');
    expect(view.gatewayMark('exclusiveGateway')).toBe('×');
    expect(view.gatewayMark('parallelGateway')).toBe('+');
    expect(view.gatewayMark('inclusiveGateway')).toBe('○');
  });
});
