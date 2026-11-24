import { PAYLOAD_LABELS_KEY, UNIT_CODE_FALLBACK, UNIT_CODE_MAX_LENGTH, UNIT_CODE_SUFFIX_ROOM } from './org-structure.constants';
import { OrgChange, OrgPerson, OrgUnit } from './org-structure.models';

/** What a department in the tree has pending in the open draft. */
export type DraftNodeState = 'live' | 'new' | 'renamed' | 'moved' | 'archived';

export interface DraftNode {
  id: string;
  parentId: string | null;
  code: string;
  name: string;
  state: DraftNodeState;
  /** The job-title ladder people in this department hold positions from, once one is chosen. */
  ladderId: string | null;
  /** Direct sub-departments that exist live, whether or not they have been fetched yet. */
  liveChildCount: number;
  /** Who heads this department once the draft is published (one person at most); null when nobody does. */
  headEmployeeId: string | null;
  /** The head's name, for showing next to the department. */
  headName?: string | null;
  /** People in this department and its sub-departments as of the live chart; null for a department created in the draft. */
  headcount: number | null;
  /** Sub-departments of this one that are loaded or created in the draft. */
  children: DraftNode[];
  /** True while live sub-departments exist that have not been fetched. */
  needsLoad: boolean;
}

export interface DraftRow {
  node: DraftNode;
  depth: number;
}

function text(value: unknown): string {
  return value === null || value === undefined ? '' : String(value);
}

/**
 * The department tree as it will look once the draft is published: the live departments fetched so far
 * (keyed by parent, TREE_ROOT_KEY for the top level) with the draft's create, rename, move and archive
 * changes laid over them in order. Pure, so the screen can rebuild it after every change.
 */
export function buildDraftTree(live: ReadonlyMap<string, OrgUnit[]>, changes: readonly OrgChange[]): DraftNode[] {
  const nodes = new Map<string, DraftNode>();
  for (const units of live.values()) {
    for (const unit of units) {
      nodes.set(unit.id, {
        id: unit.id, parentId: unit.parentId, code: unit.code, name: unit.name, state: 'live', ladderId: unit.ladderId,
        headEmployeeId: unit.headEmployeeId ?? null, headName: unit.headName ?? null, headcount: unit.headcount, liveChildCount: unit.childCount, children: [], needsLoad: unit.childCount > 0 && !live.has(unit.id),
      });
    }
  }
  for (const change of [...changes].sort((a, b) => a.seq - b.seq)) {
    const p = change.payload;
    if (change.type === 'UNIT_CREATE' && p['id']) {
      nodes.set(text(p['id']), {
        id: text(p['id']), parentId: text(p['parentId']) || null, code: text(p['code']), name: text(p['name']),
        state: 'new', ladderId: text(p['ladderId']) || null, headEmployeeId: null, headcount: null, liveChildCount: 0, children: [], needsLoad: false,
      });
    }
    if (change.type === 'UNIT_SET_HEAD' || change.type === 'UNIT_CLEAR_HEAD' || change.type === 'UNASSIGN') {
      const unitNode = nodes.get(text(p['unitId']));
      if (unitNode) {
        if (change.type === 'UNIT_SET_HEAD') {
          unitNode.headEmployeeId = text(p['employeeId']) || null;
          const labels = p[PAYLOAD_LABELS_KEY];
          unitNode.headName = labels && typeof labels === 'object' ? (labels as Record<string, string>)['employeeId'] ?? null : null;
        } else if (change.type === 'UNIT_CLEAR_HEAD' || unitNode.headEmployeeId === text(p['employeeId'])) {
          // someone who leaves a department is no longer its head
          unitNode.headEmployeeId = null;
          unitNode.headName = null;
        }
      }
      continue;
    }
    if (change.type === 'UNIT_SET_LADDER') {
      const unitNode = nodes.get(text(p['unitId']));
      if (unitNode) unitNode.ladderId = text(p['ladderId']) || null;
      continue;
    }
    const target = nodes.get(text(p['id']));
    if (!target) continue;
    if (change.type === 'UNIT_RENAME') {
      target.name = text(p['name']);
      if (target.state === 'live') target.state = 'renamed';
    } else if (change.type === 'UNIT_MOVE') {
      target.parentId = text(p['newParentId']) || null;
      if (target.state === 'live' || target.state === 'renamed') target.state = 'moved';
    } else if (change.type === 'UNIT_ARCHIVE') {
      target.state = 'archived';
    }
  }
  const roots: DraftNode[] = [];
  for (const node of nodes.values()) {
    const parent = node.parentId ? nodes.get(node.parentId) : undefined;
    if (parent) parent.children.push(node);
    else if (!node.parentId) roots.push(node);
  }
  const sortAll = (list: DraftNode[]) => {
    list.sort((a, b) => a.name.localeCompare(b.name));
    list.forEach((n) => sortAll(n.children));
  };
  sortAll(roots);
  return roots;
}

/** The tree flattened in display order, descending only into the departments the user has opened. */
export function flattenTree(roots: readonly DraftNode[], expanded: ReadonlySet<string>): DraftRow[] {
  const rows: DraftRow[] = [];
  const walk = (list: readonly DraftNode[], depth: number) => {
    for (const node of list) {
      rows.push({ node, depth });
      if (expanded.has(node.id)) walk(node.children, depth + 1);
    }
  };
  walk(roots, 0);
  return rows;
}

/** Every department code known so far, live or created in the draft, upper-cased. */
export function takenCodes(live: ReadonlyMap<string, OrgUnit[]>, changes: readonly OrgChange[]): Set<string> {
  const codes = new Set<string>();
  for (const units of live.values()) units.forEach((u) => codes.add(u.code.toUpperCase()));
  changes.filter((c) => c.type === 'UNIT_CREATE').forEach((c) => codes.add(text(c.payload['code']).toUpperCase()));
  return codes;
}

/**
 * A code for a new department made from the first letter of each word of its name ("Technical Lead" is "TL"); a
 * one-word name is used whole. Letters and digits only, and unique among `taken` ("TL", then "TL_2").
 */
export function makeUnitCode(name: string, taken: ReadonlySet<string>): string {
  const words = name.trim().split(/[^A-Za-z0-9]+/).filter(Boolean);
  const initials = words.length > 1 ? words.map((word) => word[0]).join('') : (words[0] ?? '');
  const base = initials.toUpperCase().slice(0, UNIT_CODE_MAX_LENGTH - UNIT_CODE_SUFFIX_ROOM) || UNIT_CODE_FALLBACK;
  let code = base;
  for (let n = 2; taken.has(code); n++) code = `${base}_${n}`;
  return code;
}

/**
 * The draft changes to remove to undo what is pending on a department. A department created in the draft
 * takes everything under it with it; for any other department it is only the changes made to it.
 */
export function undoChangeIds(changes: readonly OrgChange[], node: DraftNode): string[] {
  if (node.state !== 'new') {
    return changes.filter((c) => text(c.payload['id']) === node.id || text(c.payload['unitId']) === node.id).map((c) => c.id);
  }
  const doomed = new Set<string>([node.id]);
  for (let grew = true; grew;) {
    grew = false;
    for (const c of changes) {
      const id = text(c.payload['id']);
      if (c.type === 'UNIT_CREATE' && id && !doomed.has(id) && doomed.has(text(c.payload['parentId']))) {
        doomed.add(id);
        grew = true;
      }
    }
  }
  const touches = (c: OrgChange) => ['id', 'unitId', 'parentId', 'newParentId'].some((key) => doomed.has(text(c.payload[key])));
  return changes.filter(touches).map((c) => c.id);
}

/** A person named on a chart card: someone already in the department, or someone added to it in the draft. */
export interface ChartPerson {
  employeeId: string;
  name: string;
  position: string;
  /** True for someone added in the draft, who is not in the department until it is published. */
  pending: boolean;
  /** True for the head of the department: shown first and marked on the card. */
  isHead: boolean;
  /** The person's user account when they already exist live, for opening their wallet. */
  userId?: string | null;
  /** The person's job in the department, kept when they are moved to another one. */
  positionId?: string | null;
}

/**
 * Who a department will have once the draft is published: the people already in it, plus the draft's assignments
 * to it, minus anyone the draft takes out of it. A person the draft assigns again is listed once, with the new job title.
 */
export function chartPeople(
  unitId: string, live: readonly OrgPerson[], changes: readonly OrgChange[], headEmployeeId: string | null = null,
): ChartPerson[] {
  const people = new Map<string, ChartPerson>();
  live.filter((p) => p.unitId === unitId).forEach((p) => {
    people.set(p.employeeId, { employeeId: p.employeeId, name: p.fullName, position: p.positionName, pending: false, isHead: false, userId: p.userId ?? null, positionId: p.positionId });
  });
  for (const change of [...changes].sort((a, b) => a.seq - b.seq)) {
    if (text(change.payload['unitId']) !== unitId) continue;
    const employeeId = text(change.payload['employeeId']);
    if (change.type === 'ASSIGN') {
      const labels = change.payload[PAYLOAD_LABELS_KEY];
      const named = labels && typeof labels === 'object' ? (labels as Record<string, string>) : {};
      people.set(employeeId, { employeeId, name: named['employeeId'] ?? employeeId, position: named['positionId'] ?? '', pending: true, isHead: false, positionId: text(change.payload['positionId']) || null });
    } else if (change.type === 'UNASSIGN') {
      people.delete(employeeId);
    }
  }
  // the head comes first, then everyone else by name
  return [...people.values()]
    .map((person) => ({ ...person, isHead: headEmployeeId !== null && person.employeeId === headEmployeeId }))
    .sort((a, b) => Number(b.isHead) - Number(a.isHead) || a.name.localeCompare(b.name));
}
