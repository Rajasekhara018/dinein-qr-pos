import { TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, RouterModule, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { EMPTY, firstValueFrom, of } from 'rxjs';
import { HQ_CONFIG, HqDocUpload, HqDocumentService, HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { AgentKycPage } from './agent-kyc-page';
import { AgentKycService } from './agent-kyc.service';
import { ONBOARDING_UPLOAD_ACCEPT } from './franchise-onboarding-upload.constants';

const api = { get: vi.fn(() => of({ memberId: 'm1', status: 'NONE', documents: [] })), add: vi.fn(), review: vi.fn() };
const documents = { upload: vi.fn(), getMetadata: vi.fn(), fetchFile: vi.fn(), list: vi.fn(() => of([])) };
let addGranted = true;
const access = { changes$: EMPTY, can: () => addGranted };

describe('AgentKycPage uploads', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    addGranted = true;
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [AgentKycPage],
      imports: [FormsModule, RouterModule, TranslateModule.forRoot(), HqSharedLibModule],
      providers: [
        provideRouter([]),
        { provide: AgentKycService, useValue: api },
        { provide: NotificationService, useValue: { success: vi.fn(), error: vi.fn() } },
        { provide: AppDialogService, useValue: { confirm: vi.fn(), prompt: vi.fn() } },
        { provide: HqDocumentService, useValue: documents },
        { provide: HQ_CONFIG, useValue: { currentUserId: () => null } },
        { provide: MatDialog, useValue: { open: vi.fn() } },
        { provide: HqPrivilegeAccessService, useValue: access },
        { provide: ActivatedRoute, useValue: { snapshot: { data: { subject: 'franchise' }, paramMap: { get: (k: string) => (k === 'id' ? 'f1' : 'm1') } } } },
      ],
    });
  });

  it('uses the shared onboarding file rules and sends a .JPG without a browser type as image/jpeg', async () => {
    const fixture = TestBed.createComponent(AgentKycPage);
    fixture.detectChanges();
    const upload = fixture.debugElement.query(By.directive(HqDocUpload));
    const doc = upload.componentInstance as HqDocUpload;
    expect(doc.accept).toBe(ONBOARDING_UPLOAD_ACCEPT);
    expect(doc.maxSizeMB).toBe(5);
    expect(doc.hints).toBe('franchiseOnboarding.upload.hints');

    documents.upload.mockReturnValue(of({ id: 'dms-7' }));
    await firstValueFrom(upload.injector.get(HqDocumentService).upload(new File(['x'], 'ID.JPG', { type: '' }), 'franchise-onboarding', 'm1'));
    expect((documents.upload.mock.calls[0][0] as File).type).toBe('image/jpeg');
  });

  it('shows no upload to someone who may not add documents', () => {
    addGranted = false;
    const fixture = TestBed.createComponent(AgentKycPage);
    fixture.detectChanges();
    expect(fixture.debugElement.query(By.directive(HqDocUpload))).toBeNull();
  });
});
