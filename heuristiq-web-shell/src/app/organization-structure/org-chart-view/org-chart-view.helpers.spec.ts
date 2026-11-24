import { describe, expect, it } from 'vitest';
import { ChartPerson, DraftNode } from '../org-draft-tree.helpers';
import { chartCardHtml, chartNodeHeight, escapeHtml, toChartNodes } from './org-chart-view.helpers';

const node = (id: string, name: string, children: DraftNode[] = [], state: DraftNode['state'] = 'live'): DraftNode => ({
  id, parentId: null, code: name.toUpperCase(), name, state, ladderId: null, headEmployeeId: null, headcount: null, liveChildCount: 0, children, needsLoad: false,
});
const person = (name: string, isHead = false): ChartPerson => ({ employeeId: name, name, position: 'Lead', pending: false, isHead });
const labels = { stateLabel: (s: string) => s, moreLabel: (n: number) => `+${n} more`, headLabel: 'Head' };

describe('org chart view helpers', () => {
  it('escapes user text so it cannot inject HTML', () => {
    expect(escapeHtml('<img src=x onerror="alert(1)">')).toBe('&lt;img src=x onerror=&quot;alert(1)&quot;&gt;');
    const html = chartCardHtml({ id: 'a', parentId: null, name: '<b>Ops</b>', code: 'O&P', state: 'live', people: [], morePeople: 0 }, labels);
    expect(html).not.toContain('<b>');
    expect(html).toContain('&lt;b&gt;Ops&lt;/b&gt;');
  });

  it('hangs several top-level departments under one Organization box and skips archived ones', () => {
    const nodes = toChartNodes([node('a', 'A', [node('c', 'C', [], 'archived'), node('d', 'D')]), node('b', 'B')], new Map(), 'Organization');
    expect(nodes.map((n) => [n.id, n.parentId])).toEqual([['__organization__', null], ['a', '__organization__'], ['d', 'a'], ['b', '__organization__']]);
  });

  it('uses the single top-level department as the root', () => {
    const nodes = toChartNodes([node('a', 'A')], new Map(), 'Organization');
    expect(nodes.map((n) => [n.id, n.parentId])).toEqual([['a', null]]);
  });

  it('shows a few people and counts the rest, making room for them in the card', () => {
    const people = ['a', 'b', 'c', 'd', 'e', 'f'].map((name) => person(name));
    const [only] = toChartNodes([node('a', 'A')], new Map([['a', people]]), 'Organization');
    expect(only.people.length).toBe(4);
    expect(only.morePeople).toBe(2);
    expect(chartNodeHeight(only)).toBeGreaterThan(chartNodeHeight({ ...only, people: [], morePeople: 0 }));
    expect(chartCardHtml(only, labels)).toContain('+2 more');
  });

  it('marks the head of a department on its card, and only the head', () => {
    const [only] = toChartNodes([node('a', 'A')], new Map([['a', [person('asha', true), person('bala')]]]), 'Organization');
    const html = chartCardHtml(only, labels);

    expect(html).toContain('ocv-person--head');
    expect(html.match(/ocv-head"/g)?.length).toBe(1);
    expect(html).toContain('>Head<');
  });
});
