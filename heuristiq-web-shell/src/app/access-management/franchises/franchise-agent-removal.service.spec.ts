import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { firstValueFrom, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { FranchiseAgentRemoval } from './franchise-agent-removal.service';
import { FranchiseApi } from './franchise-api';

const api = { removeAgent: vi.fn(), adminRemoveAgent: vi.fn() };
const dialogs = { confirm: vi.fn() };
const notify = { success: vi.fn(), error: vi.fn() };

describe('FranchiseAgentRemoval', () => {
  let service: FranchiseAgentRemoval;

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [
        { provide: FranchiseApi, useValue: api },
        { provide: AppDialogService, useValue: dialogs },
        { provide: NotificationService, useValue: notify },
      ],
    });
    service = TestBed.inject(FranchiseAgentRemoval);
  });

  it('removes the agent once confirmed and says so', async () => {
    dialogs.confirm.mockReturnValue(of(true));
    api.removeAgent.mockReturnValue(of(null));

    await expect(firstValueFrom(service.confirmAndRemove('f1', 'm1', 'Asha'))).resolves.toBe(true);

    expect(api.removeAgent).toHaveBeenCalledWith('f1', 'm1');
    expect(notify.success).toHaveBeenCalled();
  });

  it("removes through the administrator's call when asked to, never the owner's", async () => {
    dialogs.confirm.mockReturnValue(of(true));
    api.adminRemoveAgent.mockReturnValue(of(null));

    await expect(firstValueFrom(service.confirmAndRemove('f9', 'm1', 'Asha', true))).resolves.toBe(true);

    expect(api.adminRemoveAgent).toHaveBeenCalledWith('f9', 'm1');
    expect(api.removeAgent).not.toHaveBeenCalled();
    expect(notify.success).toHaveBeenCalled();
  });

  it('does nothing when the person declines', async () => {
    dialogs.confirm.mockReturnValue(of(false));

    await expect(firstValueFrom(service.confirmAndRemove('f1', 'm1', 'Asha'))).resolves.toBe(false);

    expect(api.removeAgent).not.toHaveBeenCalled();
    expect(notify.success).not.toHaveBeenCalled();
  });

  it('shows the server message and reports false when the removal is refused', async () => {
    dialogs.confirm.mockReturnValue(of(true));
    api.removeAgent.mockReturnValue(throwError(() => ({ error: { detail: 'Not your agent' } })));

    await expect(firstValueFrom(service.confirmAndRemove('f1', 'm1', 'Asha'))).resolves.toBe(false);

    expect(notify.error).toHaveBeenCalledWith('Not your agent');
  });
});
