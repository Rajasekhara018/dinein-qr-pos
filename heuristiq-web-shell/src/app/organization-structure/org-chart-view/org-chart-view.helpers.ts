import { ChartPerson, DraftNode, DraftNodeState } from '../org-draft-tree.helpers';
import {
  CHART_MORE_ROW_HEIGHT, CHART_NODE_BASE_HEIGHT, CHART_PEOPLE_SHOWN, CHART_PERSON_ROW_HEIGHT, CHART_ROOT_ID,
} from '../org-structure.constants';

/** One box of the chart: a department (or the "Organization" box that joins several top-level departments). */
export interface ChartNodeData {
  id: string;
  parentId: string | null;
  name: string;
  code: string;
  state: DraftNodeState | 'root';
  /** People shown on the card, at most {@link CHART_PEOPLE_SHOWN}. */
  people: ChartPerson[];
  /** People beyond those shown. */
  morePeople: number;
}

/** Texts the card needs, already translated. */
export interface ChartCardLabels {
  stateLabel: (state: ChartNodeData['state']) => string;
  moreLabel: (count: number) => string;
  /** The badge on the department head. */
  headLabel: string;
}

const HTML_ESCAPES: Readonly<Record<string, string>> = {
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
};

/** Names and codes are typed by users and go into HTML the chart library inserts, so they must be escaped. */
export function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (char) => HTML_ESCAPES[char]);
}

function initial(name: string): string {
  return escapeHtml(Array.from(name.trim())[0]?.toUpperCase() ?? '');
}

/**
 * The chart's boxes from the department tree: archived departments are left out (they will be gone), and when
 * there are several top-level departments they hang under one "Organization" box, because the chart has one root.
 */
export function toChartNodes(
  roots: readonly DraftNode[],
  peopleByUnit: ReadonlyMap<string, ChartPerson[]>,
  rootName: string,
): ChartNodeData[] {
  const nodes: ChartNodeData[] = [];
  const visibleRoots = roots.filter((node) => node.state !== 'archived');
  const joined = visibleRoots.length > 1;
  if (joined) {
    nodes.push({ id: CHART_ROOT_ID, parentId: null, name: rootName, code: '', state: 'root', people: [], morePeople: 0 });
  }
  const add = (node: DraftNode, parentId: string | null) => {
    const people = peopleByUnit.get(node.id) ?? [];
    nodes.push({
      id: node.id, parentId, name: node.name, code: node.code, state: node.state,
      people: people.slice(0, CHART_PEOPLE_SHOWN), morePeople: Math.max(0, people.length - CHART_PEOPLE_SHOWN),
    });
    node.children.filter((child) => child.state !== 'archived').forEach((child) => add(child, node.id));
  };
  visibleRoots.forEach((node) => add(node, joined ? CHART_ROOT_ID : null));
  return nodes;
}

/** Height of a card: a fixed part plus a row per person shown and one for "+N more". */
export function chartNodeHeight(node: ChartNodeData): number {
  return CHART_NODE_BASE_HEIGHT + node.people.length * CHART_PERSON_ROW_HEIGHT + (node.morePeople > 0 ? CHART_MORE_ROW_HEIGHT : 0);
}

/** The HTML of one card (styled by org-chart-view.scss). */
export function chartCardHtml(node: ChartNodeData, labels: ChartCardLabels): string {
  const state = node.state === 'live' || node.state === 'root'
    ? ''
    : `<span class="ocv-state ocv-state--${node.state}">${escapeHtml(labels.stateLabel(node.state))}</span>`;
  const people = node.people.map((person) => `
      <div class="ocv-person${person.pending ? ' ocv-person--pending' : ''}${person.isHead ? ' ocv-person--head' : ''}">
        <span class="ocv-person-avatar">${initial(person.name)}</span>
        <span class="ocv-person-text">
          <span class="ocv-person-name">${escapeHtml(person.name)}${person.isHead ? `<span class="ocv-head">${escapeHtml(labels.headLabel)}</span>` : ''}</span>
          ${person.position ? `<span class="ocv-person-role">${escapeHtml(person.position)}</span>` : ''}
        </span>
      </div>`).join('');
  const more = node.morePeople > 0 ? `<div class="ocv-more">${escapeHtml(labels.moreLabel(node.morePeople))}</div>` : '';
  return `
    <div class="ocv-card ocv-card--${node.state}">
      <span class="ocv-avatar">${initial(node.name)}</span>
      <div class="ocv-name">${escapeHtml(node.name)}</div>
      ${node.code ? `<div class="ocv-code">${escapeHtml(node.code)}</div>` : ''}
      ${state}
      ${people || more ? `<div class="ocv-people">${people}${more}</div>` : ''}
    </div>`;
}
