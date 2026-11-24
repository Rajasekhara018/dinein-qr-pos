import { ChangeDetectorRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router, RouterModule, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { EMPTY, firstValueFrom, of, throwError } from 'rxjs';
import { HQ_CONFIG, HqDocUpload, HqDocumentService, HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { OrganizationContextService } from '../../shared/services/organization-context-service';
import { FranchiseOnboardingPage } from './franchise-onboarding-page';
import { FranchiseOnboarding } from './franchise-onboarding.models';
import { FranchiseOnboardingService } from './franchise-onboarding.service';
import { ONBOARDING_UPLOAD_ACCEPT } from './franchise-onboarding-upload.constants';

function onboarding(overrides: Partial<FranchiseOnboarding> = {}): FranchiseOnboarding {
  return {
    franchiseId: 'f1', code: 'RBM-1', onboardingStatus: 'ONBOARDING', activatedAt: null, activatedBy: null,
    checklist: { legalProfile: 'DONE', kyc: 'SUBMITTED', bankAccount: 'MISSING', agreement: 'MISSING' },
    canActivate: false, missing: ['kyc', 'bankAccount', 'agreement'],
    kycDocuments: [{
      id: 'd1', type: 'IDENTITY_PROOF', number: 'XXXX234F', dmsDocumentId: 'dms-1', fileName: 'pan.pdf', contentType: 'application/pdf',
      status: 'SUBMITTED', rejectReason: null, submittedBy: 'u1', submittedAt: null, reviewedBy: null, reviewedAt: null,
    }],
    bankAccount: null, agreement: null, ...overrides,
  };
}

const api = {
  get: vi.fn(), addDocument: vi.fn(), withdrawDocument: vi.fn(), reviewDocument: vi.fn(), saveBankAccount: vi.fn(),
  reviewBankAccount: vi.fn(), saveAgreement: vi.fn(), activate: vi.fn(),
};
const notify = { success: vi.fn(), error: vi.fn() };
const dialogs = { confirm: vi.fn(() => of(true)), prompt: vi.fn(() => of('Blurred scan')) };
const documents = { getMetadata: vi.fn(), fetchFile: vi.fn(), upload: vi.fn(), list: vi.fn(() => of([])) };
const matDialog = { open: vi.fn() };
let granted: Set<string> | null = null;
const access = { changes$: EMPTY, can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)) };
let routeData: Record<string, unknown> = {};

function create() {
  const fixture = TestBed.createComponent(FranchiseOnboardingPage);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
}

/** Renders again after the test changed the component's state directly. */
function render(fixture: ComponentFixture<FranchiseOnboardingPage>): void {
  fixture.componentRef.injector.get(ChangeDetectorRef).markForCheck();
  fixture.detectChanges();
}

describe('FranchiseOnboardingPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    routeData = { subject: 'franchise' };
    api.get.mockReturnValue(of(onboarding()));
    for (const call of [api.addDocument, api.withdrawDocument, api.reviewDocument, api.saveBankAccount, api.reviewBankAccount, api.saveAgreement, api.activate]) {
      call.mockReturnValue(of(onboarding()));
    }
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [FranchiseOnboardingPage],
      imports: [FormsModule, RouterModule, TranslateModule.forRoot(), HqSharedLibModule],
      providers: [
        provideRouter([]),
        { provide: FranchiseOnboardingService, useValue: api },
        { provide: NotificationService, useValue: notify },
        { provide: AppDialogService, useValue: dialogs },
        { provide: HqDocumentService, useValue: documents },
        { provide: HQ_CONFIG, useValue: { currentUserId: () => null } },
        { provide: MatDialog, useValue: matDialog },
        { provide: OrganizationContextService, useValue: { currentCountry: () => 'IN', currentCurrency: () => 'INR' } },
        { provide: HqPrivilegeAccessService, useValue: access },
        { provide: ActivatedRoute, useValue: { snapshot: { get data() { return routeData; }, paramMap: { get: () => 'f1' } } } },
      ],
    });
  });

  it('shows the checklist, the documents and an empty bank form in the organization country', () => {
    const { component, el } = create();
    expect(api.get).toHaveBeenCalledWith('franchise', 'f1');
    expect(el.querySelectorAll('.ob-checklist__item')).toHaveLength(4);
    expect(el.querySelectorAll('[data-testid="kyc-document"]')).toHaveLength(1);
    expect(component.bankEditing).toBe(true);
    expect(component.bankForm).toMatchObject({ country: 'IN', currency: 'INR' });
    expect(component.routingType).toBe('IFSC');
    expect((el.querySelector('[data-testid="activate"]') as HTMLButtonElement).disabled).toBe(true);
  });

  describe('inside the franchise setup stepper', () => {
    function embedded(section: 'kyc' | 'bank' | 'agreement' | 'activation' | null) {
      const fixture = TestBed.createComponent(FranchiseOnboardingPage);
      const seen: FranchiseOnboarding[] = [];
      fixture.componentInstance.onboardingChanged.subscribe((value) => seen.push(value));
      fixture.componentInstance.embedded = true;
      fixture.componentInstance.section = section;
      fixture.detectChanges();
      return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement, seen };
    }

    it('drops its own heading and shows only the section of the current step', () => {
      const { el, fixture, component } = embedded('kyc');

      expect(el.querySelector('.ffp__header')).toBeNull();
      expect(el.querySelectorAll('[data-testid="kyc-document"]')).toHaveLength(1);
      expect(el.querySelector('.ob-checklist')).toBeNull();
      expect(el.querySelector('[data-testid="save-bank"]')).toBeNull();

      component.section = 'activation';
      render(fixture);
      expect(el.querySelectorAll('.ob-checklist__item')).toHaveLength(4);
      expect(el.querySelector('[data-testid="kyc-document"]')).toBeNull();

      component.section = 'bank';
      render(fixture);
      expect(el.querySelector('[data-testid="save-bank"]')).not.toBeNull();
      expect(el.querySelector('.ob-checklist')).toBeNull();
    });

    it('reports the onboarding it read and every change, so the stepper follows the checklist', () => {
      api.activate.mockReturnValue(of(onboarding({ onboardingStatus: 'ONBOARDED' })));
      api.get.mockReturnValue(of(onboarding({ canActivate: true, missing: [] })));
      const { component, seen } = embedded('activation');

      component.activate();

      expect(seen.map((value) => value.onboardingStatus)).toEqual(['ONBOARDING', 'ONBOARDED']);
    });

    it('links the legal details item to the legal step of the setup', () => {
      expect(embedded('activation').component.legalProfileLink).toEqual(['/access-management', 'franchises', 'f1', 'setup', 'legal']);
      routeData = { subject: 'myFranchise' };
      expect(embedded('activation').component.legalProfileLink).toEqual(['/access-management', 'my-franchises', 'f1', 'setup', 'legal']);
    });

    it('shows every section when no step is chosen (the full page)', () => {
      const { component } = embedded(null);

      expect(['kyc', 'bank', 'agreement', 'activation'].every((section) => component.shows(section as 'kyc'))).toBe(true);
    });
  });

  it('activates a franchise whose checklist is complete, after asking', () => {
    api.get.mockReturnValue(of(onboarding({ canActivate: true, missing: [] })));
    api.activate.mockReturnValue(of(onboarding({ onboardingStatus: 'ONBOARDED', activatedAt: '2026-11-08T10:00:00Z' })));
    const { component } = create();
    component.activate();
    expect(dialogs.confirm).toHaveBeenCalled();
    expect(api.activate).toHaveBeenCalledWith('f1');
    expect(component.data?.onboardingStatus).toBe('ONBOARDED');
    expect(notify.success).toHaveBeenCalledWith('franchiseOnboarding.messages.activated');
  });

  it('verifies a document after confirming and rejects one with the reason typed', () => {
    const { component } = create();
    const doc = component.data!.kycDocuments[0];
    component.reviewDocument(doc, 'VERIFY');
    expect(api.reviewDocument).toHaveBeenCalledWith('f1', 'd1', { decision: 'VERIFY', reason: null });
    component.reviewDocument(doc, 'REJECT');
    expect(api.reviewDocument).toHaveBeenLastCalledWith('f1', 'd1', { decision: 'REJECT', reason: 'Blurred scan' });
  });

  it('does not reject without a reason', () => {
    dialogs.prompt.mockReturnValueOnce(of('  '));
    const { component } = create();
    component.reviewDocument(component.data!.kycDocuments[0], 'REJECT');
    expect(api.reviewDocument).not.toHaveBeenCalled();
  });

  it("uploads each file as its franchise document type, so that type's rules apply", () => {
    const { component } = create();
    expect(component.kycDocumentType).toBe('FRANCHISE_ID_PROOF');
    component.documentForm.type = 'TAX_CERTIFICATE';
    expect(component.kycDocumentType).toBe('FRANCHISE_TAX_CERTIFICATE');
    component.documentForm.type = 'OTHER';
    expect(component.kycDocumentType).toBeNull();
    expect(component.bankProofDocumentType).toBe('FRANCHISE_BANK_PROOF');
    expect(component.agreementDocumentType).toBe('FRANCHISE_AGREEMENT');
    expect(component.dmsCategory).toBe('franchise-onboarding');
  });

  it('gives the KYC, bank proof and agreement uploads the same PDF/JPG/JPEG/PNG and 5 MB rules', async () => {
    const { fixture } = create();
    const uploads = fixture.debugElement.queryAll(By.directive(HqDocUpload));
    expect(uploads.map((u) => (u.componentInstance as HqDocUpload).inputId)).toEqual(['kyc-file', 'bank-proof', 'agreement-copy']);
    for (const upload of uploads) {
      const doc = upload.componentInstance as HqDocUpload;
      expect(doc.accept).toBe(ONBOARDING_UPLOAD_ACCEPT);
      expect(doc.maxSizeMB).toBe(5);
      expect(doc.hints).toBe('franchiseOnboarding.upload.hints');
    }

    // Each section uploads through the page's DMS client: a JPEG the browser sent without a type goes as image/jpeg.
    documents.upload.mockReturnValue(of({ id: 'dms-5' }));
    for (const upload of uploads) {
      const client = upload.injector.get(HqDocumentService);
      await firstValueFrom(client.upload(new File(['x'], 'SCAN.JPEG', { type: '' }), 'franchise-onboarding', 'f1'));
    }
    expect(documents.upload).toHaveBeenCalledTimes(3);
    for (const [sent] of documents.upload.mock.calls) expect((sent as File).type).toBe('image/jpeg');
  });

  it('submits a KYC document once its file is uploaded to DMS', () => {
    const { component } = create();
    component.addDocument();
    expect(api.addDocument).not.toHaveBeenCalled();
    expect(notify.error).toHaveBeenCalledWith('franchiseOnboarding.validation.fixErrors');
    expect(component.documentIssues['file']?.key).toBe('franchiseOnboarding.validation.fileRequired');
    component.onDocumentUploaded({ id: 'dms-9', originalFileName: 'aadhaar.pdf', contentType: 'application/pdf' } as never);
    component.documentForm.number = ' 1234 ';
    component.addDocument();
    expect(api.addDocument).toHaveBeenCalledWith('franchise', 'f1', {
      type: 'IDENTITY_PROOF', number: '1234', dmsDocumentId: 'dms-9', fileName: 'aadhaar.pdf', contentType: 'application/pdf',
    });
  });

  it('checks the bank account before sending it, then sends it compact', () => {
    const { component } = create();
    component.saveBank();
    expect(api.saveBankAccount).not.toHaveBeenCalled();
    component.bankForm = { ...component.bankForm, holderName: 'Acme', accountNumber: '5010 0012 3456', routingCode: 'hdfc0001234' };
    component.onBankProofUploaded({ id: 'dms-3', originalFileName: 'cheque.jpg' } as never);
    component.saveBank();
    expect(api.saveBankAccount).toHaveBeenCalledWith('franchise', 'f1', expect.objectContaining({
      accountNumber: '501000123456', routingCode: 'HDFC0001234', proofDmsDocumentId: 'dms-3',
    }));
  });

  it('marks the IFSC as required for an Indian account and shows its message under the field, not only as a toast', () => {
    const { component, fixture, el } = create();
    expect(component.routingRequired).toBe(true);
    expect(el.querySelector('label[for="bank-routing"] .ffp__req')).not.toBeNull();
    expect(el.querySelector('label[for="bank-name"] .ffp__req')).toBeNull();
    component.bankForm = { ...component.bankForm, holderName: 'Acme', accountNumber: '50100012345678' };
    component.onBankProofUploaded({ id: 'dms-3', originalFileName: 'cheque.jpg' } as never);
    component.saveBank();
    render(fixture);
    expect(api.saveBankAccount).not.toHaveBeenCalled();
    expect(notify.error).toHaveBeenCalledWith('franchiseOnboarding.validation.fixErrors');
    expect(Object.keys(component.bankIssues)).toEqual(['routingCode']);
    expect(el.querySelector('#bank-routing')?.closest('hq-field')?.textContent).toContain('franchiseOnboarding.validation.routing.IFSC.required');
    component.bankForm = { ...component.bankForm, routingCode: 'sbin1001234' };
    expect(component.bankIssues['routingCode']?.key).toBe('franchiseOnboarding.validation.routing.IFSC.format');
  });

  it('offers no IBAN field for an Indian account and asks only for the IBAN in the UAE', () => {
    const { component, fixture, el } = create();
    expect(el.querySelector('#bank-iban')).toBeNull();
    component.bankForm = { ...component.bankForm, country: 'AE', currency: 'AED' };
    render(fixture);
    expect(el.querySelector('#bank-iban')).not.toBeNull();
    expect(el.querySelector('#bank-account')).toBeNull();
  });

  it('shows a field error the service reports under that field until the field changes', () => {
    api.saveBankAccount.mockReturnValue(throwError(() => ({
      status: 400,
      error: { message: 'franchiseOnboarding.invalidAccountNumber', responseObject: [{ field: 'accountNumber', message: 'franchiseOnboarding.invalidAccountNumber' }] },
    })));
    const { component } = create();
    component.bankForm = { ...component.bankForm, holderName: 'Acme', accountNumber: '50100012345678', routingCode: 'HDFC0001234' };
    component.onBankProofUploaded({ id: 'dms-3', originalFileName: 'cheque.jpg' } as never);
    component.saveBank();
    expect(api.saveBankAccount).toHaveBeenCalled();
    expect(component.bankIssues['accountNumber']?.key).toBe('franchiseOnboarding.errors.invalidAccountNumber');
    expect(notify.error).toHaveBeenCalledWith('franchiseOnboarding.errors.invalidAccountNumber');
    component.bankForm = { ...component.bankForm, accountNumber: '50100012345679' };
    expect(component.bankIssues['accountNumber']).toBeUndefined();
  });

  it('checks the agreement dates inline before sending it', () => {
    const { component } = create();
    component.agreementForm = {
      ...component.agreementForm, agreementNumber: 'FA-1', signedOn: '2999-01-01', startsOn: '2026-02-01', endsOn: '2026-01-01',
      signedCopyDmsDocumentId: 'dms-4', signedCopyFileName: 'agreement.pdf',
    };
    component.saveAgreement();
    expect(api.saveAgreement).not.toHaveBeenCalled();
    expect(component.agreementIssues['signedOn']?.key).toBe('franchiseOnboarding.validation.signedInFuture');
    expect(component.agreementIssues['endsOn']?.key).toBe('franchiseOnboarding.validation.endBeforeStart');
  });

  it('records an agreement and shows a translated service refusal', () => {
    api.saveAgreement.mockReturnValue(throwError(() => ({ status: 409, error: { message: 'franchiseOnboarding.staleVersion' } })));
    const { component } = create();
    component.agreementForm = {
      ...component.agreementForm, agreementNumber: 'FA-1', signedOn: '2026-01-01', startsOn: '2026-01-01',
      signedCopyDmsDocumentId: 'dms-4', signedCopyFileName: 'agreement.pdf',
    };
    component.saveAgreement();
    expect(api.saveAgreement).toHaveBeenCalledWith('f1', expect.objectContaining({ agreementNumber: 'FA-1', securityDeposit: null }));
    expect(notify.error).toHaveBeenCalledWith('franchiseOnboarding.errors.staleVersion');
  });

  it('gives the owner their own actions only: no review, agreement or activation', () => {
    routeData = { subject: 'myFranchise' };
    granted = new Set(['FRANCHISE_MINE_ONBOARDING_READ', 'FRANCHISE_MINE_KYC_DOCUMENT_CREATE', 'FRANCHISE_MINE_KYC_DOCUMENT_DELETE', 'FRANCHISE_MINE_BANK_ACCOUNT_UPDATE']);
    const { component, el } = create();
    expect(api.get).toHaveBeenCalledWith('myFranchise', 'f1');
    expect(component.can).toEqual({ addDocument: true, withdrawDocument: true, reviewDocument: false, saveBank: true, reviewBank: false, saveAgreement: false, activate: false });
    expect(el.querySelector('[data-testid="verify-document"]')).toBeNull();
    expect(el.querySelector('[data-testid="activate"]')).toBeNull();
    component.withdraw(component.data!.kycDocuments[0]);
    expect(api.withdrawDocument).toHaveBeenCalledWith('f1', 'd1');
  });

  it("does not send back the owner's masked account number when they edit it", () => {
    routeData = { subject: 'myFranchise' };
    api.get.mockReturnValue(of(onboarding({
      bankAccount: {
        holderName: 'Acme', country: 'IN', currency: 'INR', bankName: null, accountNumber: 'XXXXXXXXXX5678', iban: null, routingCode: 'HDFC0001234',
        routingType: 'IFSC', proofDmsDocumentId: 'dms-3', proofFileName: 'cheque.jpg', status: 'VERIFIED', rejectReason: null,
        reviewedBy: 'a1', reviewedAt: null, version: 2, updatedAt: null, updatedBy: null,
      },
    })));
    const { component } = create();
    expect(component.bankEditing).toBe(false);
    expect(component.bankForm.accountNumber).toBeNull();
    expect(component.bankForm.version).toBe(2);
  });

  it('a reader without change privileges sees no forms', () => {
    granted = new Set(['FRANCHISE_ONBOARDING_READ']);
    const { el } = create();
    expect(el.querySelector('[data-testid="add-document"]')).toBeNull();
    expect(el.querySelector('[data-testid="save-bank"]')).toBeNull();
    expect(el.querySelector('[data-testid="save-agreement"]')).toBeNull();
    expect(el.querySelector('[data-testid="activate"]')).toBeNull();
  });

  it('previews a stored file and goes back to the list', () => {
    documents.getMetadata.mockReturnValue(of({ id: 'dms-1', contentType: 'application/pdf' }));
    documents.fetchFile.mockReturnValue(of(new Blob(['x'], { type: 'application/pdf' })));
    const { component } = create();
    component.viewFile('dms-1', 'pan.pdf');
    expect(matDialog.open).toHaveBeenCalled();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    component.back();
    expect(navigate).toHaveBeenCalledWith(['/access-management', 'franchises']);
  });

  it('shows a retry when the onboarding cannot be read', () => {
    api.get.mockReturnValue(throwError(() => ({ status: 500 })));
    const { component } = create();
    expect(component.loadFailed).toBe(true);
  });
});
