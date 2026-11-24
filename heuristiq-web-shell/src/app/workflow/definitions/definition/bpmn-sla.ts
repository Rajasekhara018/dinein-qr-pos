import { NOTIFY_CHANNELS, NotifyChannel, SLA_DEFAULT_UNIT, SLA_MINUTES_PER_UNIT, SlaUnit } from '../../workflow.constants';

/**
 * Reading and changing each step's deadline (SLA) and who it is for, in a process's BPMN XML.
 * - SLA: the `flowable:dueDate` attribute on the userTask, an ISO-8601 duration counted from when the
 *   task is created (PT8H = 8 hours, P2D = 2 days).
 * - Who: `flowable:assignee` (one UAM user id), `flowable:candidateUsers` (UAM user ids) and
 *   `flowable:candidateGroups` (UAM role codes), comma separated.
 * - Notifications: `rider:notifyChannels` (NMS channels, or NONE) and `rider:escalateTo` (UAM user ids told when the
 *   step goes overdue), in Workflow's own namespace.
 * Edits touch only those attributes, in place, so the rest of the XML (comments, layout) stays as written.
 */

const BPMN_NS = 'http://www.omg.org/spec/BPMN/20100524/MODEL';
const FLOWABLE_NS = 'http://flowable.org/bpmn';
const DEFAULT_PREFIX = 'flowable';
const RIDER_NS = 'http://rider.com/workflow/extensions';
const RIDER_PREFIX = 'rider';
const RIDER_ATTRIBUTES = ['notifyChannels', 'escalateTo', 'remindBefore', 'repeatEvery', 'reassignOnOverdue'];

export interface StepSla {
  id: string;
  name: string;
  /** The whole number of [unit]s, or null when the step has no SLA. */
  amount: number | null;
  unit: SlaUnit;
  /** The attribute holds something other than a plain duration (a date or ${variable}): shown, not editable. */
  custom: string | null;
  /** UAM user id the step is assigned to, or null. */
  assignee: string | null;
  /** UAM user ids the step is offered to. */
  candidateUsers: string[];
  /** UAM role codes whose holders the step is offered to. */
  candidateGroups: string[];
  /** The channels the step notifies on; null when it names none (the service's default applies), [] when switched off. */
  notifyChannels: NotifyChannel[] | null;
  /** UAM user ids also told when the step goes overdue. */
  escalateTo: string[];
  /** How long before the deadline a "due soon" reminder goes out, or null for none. */
  remindBefore: { amount: number; unit: SlaUnit } | null;
  /** How often the overdue notice repeats, or null for once. */
  repeatEvery: { amount: number; unit: SlaUnit } | null;
  /** UAM user id the task is handed to when it first goes overdue, or null. */
  reassignOnOverdue: string | null;
}

export type StepDuration = 'remindBefore' | 'repeatEvery';

export type StepWho = 'assignee' | 'candidateUsers' | 'candidateGroups' | 'escalateTo';

/** The user tasks of the process, in the order the flow reaches them. */
export function readSteps(xml: string): StepSla[] {
  const doc = new DOMParser().parseFromString(xml, 'application/xml');
  if (doc.getElementsByTagName('parsererror').length > 0) return [];

  const tasks = Array.from(doc.getElementsByTagNameNS(BPMN_NS, 'userTask'));
  const byId = new Map(tasks.map((t) => [t.getAttribute('id') ?? '', t]));
  const flows = Array.from(doc.getElementsByTagNameNS(BPMN_NS, 'sequenceFlow'));

  const ordered: Element[] = [];
  const seen = new Set<string>();
  const queue = Array.from(doc.getElementsByTagNameNS(BPMN_NS, 'startEvent')).map((e) => e.getAttribute('id') ?? '');
  while (queue.length > 0) {
    const node = queue.shift() as string;
    if (seen.has(node)) continue;
    seen.add(node);
    const task = byId.get(node);
    if (task) ordered.push(task);
    flows.filter((f) => f.getAttribute('sourceRef') === node).forEach((f) => queue.push(f.getAttribute('targetRef') ?? ''));
  }
  // Tasks the flow never reaches still get listed, after the reachable ones.
  tasks.filter((t) => !ordered.includes(t)).forEach((t) => ordered.push(t));

  return ordered.map((task) => {
    const id = task.getAttribute('id') ?? '';
    const due = task.getAttributeNS(FLOWABLE_NS, 'dueDate');
    const parsed = due ? fromIso(due) : null;
    return {
      id,
      name: task.getAttribute('name') || id,
      amount: parsed?.amount ?? null,
      unit: parsed?.unit ?? SLA_DEFAULT_UNIT,
      custom: due && !parsed ? due : null,
      assignee: task.getAttributeNS(FLOWABLE_NS, 'assignee') || null,
      candidateUsers: splitList(task.getAttributeNS(FLOWABLE_NS, 'candidateUsers')),
      candidateGroups: splitList(task.getAttributeNS(FLOWABLE_NS, 'candidateGroups')),
      notifyChannels: parseChannels(task.getAttributeNS(RIDER_NS, 'notifyChannels')),
      escalateTo: splitList(task.getAttributeNS(RIDER_NS, 'escalateTo')),
      remindBefore: fromIso(task.getAttributeNS(RIDER_NS, 'remindBefore') ?? ''),
      repeatEvery: fromIso(task.getAttributeNS(RIDER_NS, 'repeatEvery') ?? ''),
      reassignOnOverdue: task.getAttributeNS(RIDER_NS, 'reassignOnOverdue') || null,
    };
  });
}

/** The XML with step [stepId]'s SLA set to [amount] [unit], or removed when [amount] is empty. */
export function applySla(xml: string, stepId: string, amount: number | null, unit: SlaUnit): string {
  return setStepAttribute(xml, stepId, 'dueDate', amount !== null && amount > 0 ? toIso(amount, unit) : null);
}

/** The XML with who step [stepId] is for set: a single user id, or a list of user ids / role codes. */
export function applyWho(xml: string, stepId: string, field: StepWho, values: string[]): string {
  const clean = Array.from(new Set(values.map((v) => v.trim()).filter((v) => v.length > 0)));
  return setStepAttribute(xml, stepId, field, clean.length > 0 ? clean.join(',') : null);
}

/** The XML with step [stepId]'s notification channels set; null removes the attribute (the default applies), [] switches them off. */
export function applyNotify(xml: string, stepId: string, channels: readonly NotifyChannel[] | null): string {
  if (channels === null) return setStepAttribute(xml, stepId, 'notifyChannels', null);
  const ordered = NOTIFY_CHANNELS.filter((c) => channels.includes(c));
  return setStepAttribute(xml, stepId, 'notifyChannels', ordered.length > 0 ? ordered.join(',') : 'NONE');
}

/** The XML with step [stepId]'s reminder or repeat interval set, or removed when [amount] is empty. */
export function applyReminder(xml: string, stepId: string, which: StepDuration, amount: number | null, unit: SlaUnit): string {
  return setStepAttribute(xml, stepId, which, amount !== null && amount > 0 ? toIso(amount, unit) : null);
}

/** The XML with the person an overdue step is handed to; null removes it. */
export function applyReassign(xml: string, stepId: string, userId: string | null): string {
  return setStepAttribute(xml, stepId, 'reassignOnOverdue', userId && userId.trim() ? userId.trim() : null);
}

function parseChannels(value: string | null): NotifyChannel[] | null {
  if (!value || !value.trim()) return null;
  const requested = splitList(value).map((v) => v.toUpperCase());
  return NOTIFY_CHANNELS.filter((c) => requested.includes(c));
}

function splitList(value: string | null): string[] {
  return (value ?? '').split(',').map((v) => v.trim()).filter((v) => v.length > 0);
}

/** Sets (or, for null, removes) one flowable:* or rider:* attribute on the userTask [stepId], in place. */
function setStepAttribute(xml: string, stepId: string, name: string, value: string | null): string {
  const namespace = RIDER_ATTRIBUTES.includes(name) ? RIDER_NS : FLOWABLE_NS;
  const defaultPrefix = namespace === RIDER_NS ? RIDER_PREFIX : DEFAULT_PREFIX;
  const tag = new RegExp(`<((?:\\w+:)?userTask)\\b([^>]*?\\bid\\s*=\\s*"${escapeRegExp(stepId)}"[^>]*?)(/?)>`);
  const match = tag.exec(xml);
  if (!match) return xml;

  const prefix = prefixFor(xml, namespace);
  const attr = `${prefix ?? defaultPrefix}:${name}`;
  let attrs = match[2].replace(new RegExp(`\\s+\\w+:${name}\\s*=\\s*"[^"]*"`), '');
  if (value !== null) {
    attrs += ` ${attr}="${escapeAttr(value)}"`;
  }
  const rebuilt = `<${match[1]}${attrs}${match[3]}>`;
  let out = xml.slice(0, match.index) + rebuilt + xml.slice(match.index + match[0].length);
  if (value !== null && prefix === null) {
    out = out.replace(/<((?:\w+:)?definitions)\b/, `<$1 xmlns:${defaultPrefix}="${namespace}"`);
  }
  return out;
}

/** The prefix the XML binds to [namespace], or null when it declares none. */
function prefixFor(xml: string, namespace: string): string | null {
  const m = new RegExp(`xmlns:(\\w+)\\s*=\\s*"${escapeRegExp(namespace)}"`).exec(xml);
  return m ? m[1] : null;
}

/** 90 minutes -> "PT90M"; 8 hours -> "PT8H"; 2 days -> "P2D"; 2 weeks -> "P14D" (ISO durations here use days, not weeks). */
export function toIso(amount: number, unit: SlaUnit): string {
  switch (unit) {
    case 'WEEKS': return `P${amount * 7}D`;
    case 'DAYS': return `P${amount}D`;
    case 'HOURS': return `PT${amount}H`;
    default: return `PT${amount}M`;
  }
}

/** A plain ISO duration as the largest unit that divides it exactly; null for anything else. */
export function fromIso(value: string): { amount: number; unit: SlaUnit } | null {
  const m = /^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?)?$/.exec(value.trim());
  if (!m || (m[1] === undefined && m[2] === undefined && m[3] === undefined)) return null;
  const minutes = Number(m[1] ?? 0) * SLA_MINUTES_PER_UNIT.DAYS
    + Number(m[2] ?? 0) * SLA_MINUTES_PER_UNIT.HOURS
    + Number(m[3] ?? 0);
  if (minutes <= 0) return null;
  if (minutes % SLA_MINUTES_PER_UNIT.DAYS === 0) return { amount: minutes / SLA_MINUTES_PER_UNIT.DAYS, unit: 'DAYS' };
  if (minutes % SLA_MINUTES_PER_UNIT.HOURS === 0) return { amount: minutes / SLA_MINUTES_PER_UNIT.HOURS, unit: 'HOURS' };
  return { amount: minutes, unit: 'MINUTES' };
}

function escapeAttr(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;');
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
