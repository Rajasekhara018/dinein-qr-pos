import { HqStepRail } from './hq-step-rail';
import { HqStepRailStep } from './hq-step-rail.model';

describe('HqStepRail', () => {
  const steps: HqStepRailStep[] = [
    { id: 'a', label: 'Personal', state: 'done' },
    { id: 'b', label: 'Documents' },
    { id: 'c', label: 'Review', state: 'locked' },
  ];

  function rail(current = 'b'): HqStepRail {
    const r = new HqStepRail();
    r.steps = steps;
    r.current = current;
    r.texts = { stepOf: 'Step 2 of 3', progress: '33% complete', ariaLabel: 'Steps', jumpTo: 'Go to step', done: 'Done',
      todo: 'Not started', locked: 'Locked' };
    return r;
  }

  it('finds the current step and numbers the steps', () => {
    const r = rail();
    expect(r.currentStep?.label).toBe('Documents');
    expect(r.numberOf(steps[2])).toBe(3);
  });

  it('opens a done or not-started step but never the current or a locked one', () => {
    const r = rail();
    const opened: string[] = [];
    r.stepSelect.subscribe((id) => opened.push(id));
    r.open(steps[0]);
    r.open(steps[1]);
    r.open(steps[2]);
    r.jump('a');
    r.jump('missing');
    expect(opened).toEqual(['a', 'a']);
  });

  it('names each state for screen readers and keeps the progress within 0-100', () => {
    const r = rail();
    expect(steps.map((s) => r.stateLabel(s))).toEqual(['Done', 'Not started', 'Locked']);
    r.percent = 140;
    expect(r.boundedPercent).toBe(100);
    r.percent = -5;
    expect(r.boundedPercent).toBe(0);
  });
});
