import { of } from 'rxjs';
import { vi } from 'vitest';
import { UamService } from '../../shared/services/uam-service';
import { OrganizationOperatingModelService } from './organization-operating-model.service';
import { OperatingModelView } from './organization-operating-model.types';

describe('OrganizationOperatingModelService', () => {
  const uam = { get: vi.fn(), update: vi.fn() };
  const service = new OrganizationOperatingModelService(uam as unknown as UamService);
  const view: OperatingModelView = {
    organizationId: 'org1',
    today: '2026-11-08',
    currentModel: 'FRANCHISE',
    schedule: [
      { model: 'FRANCHISE', effectiveFrom: '2026-10-01', inForce: true },
      { model: 'DIRECT', effectiveFrom: '2027-01-01', inForce: false },
      { model: 'FRANCHISE', effectiveFrom: '2027-06-01', inForce: false },
    ],
  };

  beforeEach(() => {
    vi.clearAllMocks();
    uam.get.mockReturnValue(of(view));
    uam.update.mockReturnValue(of(view));
  });

  it('reads the operating model of the organization', () => {
    service.load('org1').subscribe();

    expect(uam.get).toHaveBeenCalledWith('/organizations/org1/operating-model');
  });

  it('schedules a switch by sending the future changes plus the new one, never the changes in force', () => {
    service.schedule('org1', view, 'DIRECT', '2027-09-01').subscribe();

    expect(uam.update).toHaveBeenCalledWith('/organizations/org1', 'operating-model', {
      scheduledChanges: [
        { model: 'DIRECT', effectiveFrom: '2027-01-01' },
        { model: 'FRANCHISE', effectiveFrom: '2027-06-01' },
        { model: 'DIRECT', effectiveFrom: '2027-09-01' },
      ],
    });
  });

  it('cancels a switch by sending the future changes without it', () => {
    service.cancel('org1', view, view.schedule[2]).subscribe();

    expect(uam.update).toHaveBeenCalledWith('/organizations/org1', 'operating-model', {
      scheduledChanges: [{ model: 'DIRECT', effectiveFrom: '2027-01-01' }],
    });
  });
});
