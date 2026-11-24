import { earliestStart, futureChanges, lastModel, switchOptions } from './organization-operating-model.helpers';
import { OperatingModelView } from './organization-operating-model.types';

describe('organization operating model helpers', () => {
  const view = (overrides: Partial<OperatingModelView> = {}): OperatingModelView => ({
    organizationId: 'org1',
    today: '2026-11-08',
    currentModel: 'DIRECT',
    schedule: [],
    ...overrides,
  });

  it('keeps only the changes that have not started, in the shape UAM takes', () => {
    const v = view({
      currentModel: 'FRANCHISE',
      schedule: [
        { model: 'FRANCHISE', effectiveFrom: '2026-10-01', inForce: true },
        { model: 'DIRECT', effectiveFrom: '2027-01-01', inForce: false },
      ],
    });

    expect(futureChanges(v)).toEqual([{ model: 'DIRECT', effectiveFrom: '2027-01-01' }]);
  });

  it('takes the model of the last scheduled change, or today\'s when nothing is scheduled', () => {
    expect(lastModel(view())).toBe('DIRECT');
    expect(lastModel(view({ currentModel: 'HYBRID' }))).toBe('HYBRID');
    expect(lastModel(view({ schedule: [{ model: 'FRANCHISE', effectiveFrom: '2026-12-01', inForce: false }] }))).toBe('FRANCHISE');
  });

  it('offers every model except the one in force before the new change, hybrid included', () => {
    expect(switchOptions(view())).toEqual(['FRANCHISE', 'HYBRID']);
    expect(switchOptions(view({ currentModel: 'FRANCHISE' }))).toEqual(['DIRECT', 'HYBRID']);
    expect(switchOptions(view({ currentModel: 'HYBRID' }))).toEqual(['DIRECT', 'FRANCHISE']);
    expect(switchOptions(view({ schedule: [{ model: 'HYBRID', effectiveFrom: '2026-12-01', inForce: false }] })))
      .toEqual(['DIRECT', 'FRANCHISE']);
  });

  it('offers today, or the day after the last scheduled change when that is later', () => {
    expect(earliestStart(view())).toBe('2026-11-08');
    expect(earliestStart(view({ schedule: [{ model: 'FRANCHISE', effectiveFrom: '2026-01-01', inForce: true }] }))).toBe('2026-11-08');
    expect(earliestStart(view({ schedule: [{ model: 'FRANCHISE', effectiveFrom: '2026-12-31', inForce: false }] }))).toBe('2027-01-01');
    expect(earliestStart(view({ schedule: [{ model: 'FRANCHISE', effectiveFrom: '2026-11-08', inForce: true }] }))).toBe('2026-11-09');
  });
});
