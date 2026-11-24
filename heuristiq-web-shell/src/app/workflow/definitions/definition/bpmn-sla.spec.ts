import { describe, expect, it } from 'vitest';
import { PROCESS_KEY_PATTERN, WORKFLOW_TEMPLATES } from '../workflow-templates';
import { applyNotify, applyReassign, applyReminder, applySla, applyWho, fromIso, readSteps, toIso } from './bpmn-sla';

const XML = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="x">
  <process id="p" isExecutable="true">
    <startEvent id="start"/>
    <userTask id="first" name="First review" flowable:dueDate="PT8H" flowable:assignee="u1" flowable:candidateUsers="a, b" flowable:candidateGroups="R1,R2"/>
    <userTask id="second" name="Second" flowable:dueDate="P2D"></userTask>
    <userTask id="orphan"/>
    <endEvent id="end"/>
    <sequenceFlow id="f1" sourceRef="start" targetRef="first"/>
    <sequenceFlow id="f2" sourceRef="first" targetRef="second"/>
    <sequenceFlow id="f3" sourceRef="second" targetRef="end"/>
  </process>
</definitions>`;

describe('toIso / fromIso', () => {
  it('writes each unit as an ISO duration, weeks as days', () => {
    expect(toIso(90, 'MINUTES')).toBe('PT90M');
    expect(toIso(8, 'HOURS')).toBe('PT8H');
    expect(toIso(2, 'DAYS')).toBe('P2D');
    expect(toIso(2, 'WEEKS')).toBe('P14D');
  });

  it('reads back the largest unit that divides the duration exactly', () => {
    expect(fromIso('PT8H')).toEqual({ amount: 8, unit: 'HOURS' });
    expect(fromIso('P2D')).toEqual({ amount: 2, unit: 'DAYS' });
    expect(fromIso('P14D')).toEqual({ amount: 14, unit: 'DAYS' });
    expect(fromIso('PT90M')).toEqual({ amount: 90, unit: 'MINUTES' });
    expect(fromIso('PT1H30M')).toEqual({ amount: 90, unit: 'MINUTES' });
    expect(fromIso('P1DT12H')).toEqual({ amount: 36, unit: 'HOURS' });
  });

  it('refuses anything that is not a plain, positive duration', () => {
    for (const bad of ['', 'P', 'PT', 'PT0M', '${due}', '2026-01-01', 'P1W', 'PT5S']) expect(fromIso(bad)).toBeNull();
  });
});

describe('readSteps', () => {
  it('lists user tasks in flow order, then unreachable ones, with their settings', () => {
    const steps = readSteps(XML);
    expect(steps.map((s) => s.id)).toEqual(['first', 'second', 'orphan']);
    expect(steps[0]).toMatchObject({
      name: 'First review', amount: 8, unit: 'HOURS', custom: null, assignee: 'u1',
      candidateUsers: ['a', 'b'], candidateGroups: ['R1', 'R2'], notifyChannels: null, escalateTo: [],
      remindBefore: null, repeatEvery: null, reassignOnOverdue: null,
    });
    expect(steps[1]).toMatchObject({ amount: 2, unit: 'DAYS' });
    expect(steps[2]).toMatchObject({ name: 'orphan', amount: null, unit: 'HOURS', assignee: null, candidateUsers: [] });
  });

  it('keeps a non-duration deadline as custom text rather than editing it', () => {
    const xml = XML.replace('flowable:dueDate="PT8H"', 'flowable:dueDate="${dueSoon}"');
    const first = readSteps(xml)[0];
    expect(first.custom).toBe('${dueSoon}');
    expect(first.amount).toBeNull();
  });

  it('returns nothing for XML that does not parse', () => {
    expect(readSteps('<definitions><oops></definitions>')).toEqual([]);
    expect(readSteps('')).toEqual([]);
  });
});

describe('applySla', () => {
  it('replaces the deadline in place and leaves the rest of the XML untouched', () => {
    const out = applySla(XML, 'first', 3, 'DAYS');
    expect(out).toContain('flowable:dueDate="P3D"');
    expect(out).not.toContain('PT8H');
    const changed = (xml: string) => xml.split(/\r?\n/).filter((line) => !line.includes('id="first"'));
    expect(changed(out)).toEqual(changed(XML));
    expect(readSteps(out)[0]).toMatchObject({ amount: 3, unit: 'DAYS', assignee: 'u1' });
  });

  it('removes the deadline when the amount is empty or not positive', () => {
    expect(readSteps(applySla(XML, 'first', null, 'HOURS'))[0].amount).toBeNull();
    expect(readSteps(applySla(XML, 'first', 0, 'HOURS'))[0].amount).toBeNull();
  });

  it('adds the flowable namespace when the file does not declare it, and ignores an unknown step', () => {
    const bare = '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><process id="p"><userTask id="t"/></process></definitions>';
    const out = applySla(bare, 't', 1, 'HOURS');
    expect(out).toContain('xmlns:flowable="http://flowable.org/bpmn"');
    expect(readSteps(out)[0]).toMatchObject({ amount: 1, unit: 'HOURS' });
    expect(applySla(XML, 'nope', 1, 'HOURS')).toBe(XML);
  });

  it('works on a self-closing task and a prefixed userTask element', () => {
    expect(readSteps(applySla(XML, 'orphan', 5, 'MINUTES'))[2]).toMatchObject({ amount: 5, unit: 'MINUTES' });
    const prefixed = `<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn"><bpmn:process id="p"><bpmn:userTask id="t"/></bpmn:process></bpmn:definitions>`;
    expect(readSteps(applySla(prefixed, 't', 2, 'DAYS'))[0]).toMatchObject({ amount: 2, unit: 'DAYS' });
  });
});

describe('applyWho', () => {
  it('sets de-duplicated, trimmed values and removes the attribute when empty', () => {
    const set = applyWho(XML, 'second', 'candidateUsers', [' x ', 'y', 'x', '']);
    expect(readSteps(set)[1].candidateUsers).toEqual(['x', 'y']);
    const cleared = applyWho(set, 'second', 'candidateUsers', []);
    expect(readSteps(cleared)[1].candidateUsers).toEqual([]);
    expect(cleared).not.toContain('candidateUsers="x');
  });

  it('sets the assignee and role groups', () => {
    const out = applyWho(applyWho(XML, 'second', 'assignee', ['u9']), 'second', 'candidateGroups', ['R9']);
    expect(readSteps(out)[1]).toMatchObject({ assignee: 'u9', candidateGroups: ['R9'] });
  });

  it('escapes special characters in attribute values', () => {
    const out = applyWho(XML, 'second', 'assignee', ['a"b<c&d']);
    expect(out).toContain('flowable:assignee="a&quot;b&lt;c&amp;d"');
    expect(readSteps(out)[1].assignee).toBe('a"b<c&d');
  });

  it('writes escalation people in the rider namespace, declaring it once', () => {
    const out = applyWho(XML, 'first', 'escalateTo', ['boss']);
    expect(out).toContain('xmlns:rider="http://rider.com/workflow/extensions"');
    expect(out).toContain('rider:escalateTo="boss"');
    expect(readSteps(out)[0].escalateTo).toEqual(['boss']);
    const again = applyWho(out, 'second', 'escalateTo', ['x']);
    expect(again.match(/xmlns:rider=/g)).toHaveLength(1);
  });
});

describe('applyNotify', () => {
  it('orders channels canonically, writes NONE for an empty list and removes the attribute for null', () => {
    const set = applyNotify(XML, 'first', ['SMS', 'EMAIL', 'IN_APP']);
    expect(readSteps(set)[0].notifyChannels).toEqual(['IN_APP', 'EMAIL', 'SMS']);
    expect(set).toContain('rider:notifyChannels="IN_APP,EMAIL,SMS"');
    const off = applyNotify(XML, 'first', []);
    expect(off).toContain('rider:notifyChannels="NONE"');
    expect(readSteps(off)[0].notifyChannels).toEqual([]);
    const removed = applyNotify(set, 'first', null);
    expect(readSteps(removed)[0].notifyChannels).toBeNull();
  });

  it('ignores unknown channel names when reading', () => {
    const out = applyWho(XML, 'first', 'escalateTo', []).replace('<userTask id="first"', '<userTask id="first" xmlns:rider="http://rider.com/workflow/extensions" rider:notifyChannels="sms,PIGEON"');
    expect(readSteps(out)[0].notifyChannels).toEqual(['SMS']);
  });
});

describe('applyReminder / applyReassign', () => {
  it('sets, reads and removes reminder and repeat intervals independently', () => {
    let xml = applyReminder(XML, 'first', 'remindBefore', 2, 'HOURS');
    xml = applyReminder(xml, 'first', 'repeatEvery', 1, 'DAYS');
    expect(readSteps(xml)[0]).toMatchObject({ remindBefore: { amount: 2, unit: 'HOURS' }, repeatEvery: { amount: 1, unit: 'DAYS' } });
    xml = applyReminder(xml, 'first', 'remindBefore', null, 'HOURS');
    expect(readSteps(xml)[0]).toMatchObject({ remindBefore: null, repeatEvery: { amount: 1, unit: 'DAYS' } });
  });

  it('sets the overdue hand-over person, trimmed, and removes it for blank or null', () => {
    const set = applyReassign(XML, 'first', ' u7 ');
    expect(readSteps(set)[0].reassignOnOverdue).toBe('u7');
    expect(readSteps(applyReassign(set, 'first', '  '))[0].reassignOnOverdue).toBeNull();
    expect(readSteps(applyReassign(set, 'first', null))[0].reassignOnOverdue).toBeNull();
  });
});

describe('workflow templates', () => {
  it('offers templates with unique ids and translation keys under their id', () => {
    const ids = WORKFLOW_TEMPLATES.map((t) => t.id);
    expect(new Set(ids).size).toBe(ids.length);
    for (const t of WORKFLOW_TEMPLATES) {
      expect(t.titleKey).toBe(`workflow.definitions.templates.${t.id}.title`);
      expect(t.descriptionKey).toBe(`workflow.definitions.templates.${t.id}.description`);
    }
  });

  it.each(WORKFLOW_TEMPLATES.map((t) => [t.id, t] as const))('%s builds well-formed BPMN with the key, an escaped name and user tasks', (_id, template) => {
    const xml = template.build('my-flow', 'Fish & <Chips>');
    const doc = new DOMParser().parseFromString(xml, 'application/xml');
    expect(doc.getElementsByTagName('parsererror')).toHaveLength(0);
    const process = doc.getElementsByTagName('process')[0];
    expect(process.getAttribute('id')).toBe('my-flow');
    expect(process.getAttribute('name')).toBe('Fish & <Chips>');
    expect(readSteps(xml).length).toBeGreaterThan(0);
    expect(doc.getElementsByTagName('endEvent').length).toBeGreaterThan(0);
  });

  it('accepts only process keys that start with a letter and use safe characters', () => {
    for (const ok of ['a', 'generic-review', 'A_b.c-9']) expect(PROCESS_KEY_PATTERN.test(ok)).toBe(true);
    for (const bad of ['', '9abc', 'has space', 'x'.repeat(65), 'a/b']) expect(PROCESS_KEY_PATTERN.test(bad)).toBe(false);
  });
});
