import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqLegalProfile, HqPrivilegeAccessService, hqEmptyLegalProfile, HqProgress, HqSharedLibModule } from 'hq-shared-lib';
import { BehaviorSubject, EMPTY, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { FranchiseOnboarding } from '../franchise-onboarding/franchise-onboarding.models';
import { Franchise } from '../franchises/franchise.models';
import { FranchiseSetupPage } from './franchise-setup';

// The step pages are tested on their own; here they are stand-ins that report what the stepper reads from them.
@Component({ selector: 'app-franchise-form', standalone: false, template: '<p>details</p>' })
class StubDetails {
  @Input() embedded = false;
  @Output() readonly franchiseLoaded = new EventEmitter<Franchise>();
  pending = false;
  missing: string[] = [];
  hasPendingChanges(): boolean { return this.pending; }
  missingFields(): string[] { return this.missing; }
}

@Component({ selector: 'app-legal-profile-page', standalone: false, template: '<p>legal</p>' })
class StubLegal {
  @Input() embedded = false;
  @Input() ownerIdInput = '';
  @Output() readonly profileLoaded = new EventEmitter<HqLegalProfile>();
  @Output() readonly profileSaved = new EventEmitter<HqLegalProfile>();
  pending = false;
  missing: string[] = [];
  hasPendingChanges(): boolean { return this.pending; }
  missingFields(): string[] { return this.missing; }
}

@Component({ selector: 'app-franchise-onboarding-page', standalone: false, template: '<p>onboarding {{ section }}</p>' })
class StubOnboarding {
  @Input() embedded = false;
  @Input() section: string | null = null;
  @Output() readonly onboardingChanged = new EventEmitter<FranchiseOnboarding>();
  load = vi.fn();
}

const ADMIN = new Set(['FRANCHISE_LIST', 'FRANCHISE_LEGAL_PROFILE_READ', 'FRANCHISE_ONBOARDING_READ']);
let granted: Set<string>;
const access = { changes$: EMPTY, can: (codes: string | readonly string[]) => [codes].flat().some((c) => granted.has(c)) };
const dialogs = { confirm: vi.fn(() => of(true)) };
let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
let subject = 'franchise';
let routeId: string | null = 'f-1';

function onboarding(overrides: Partial<FranchiseOnboarding> = {}): FranchiseOnboarding {
  return {
    franchiseId: 'f-1', code: 'RBM-00008', onboardingStatus: 'ONBOARDING', activatedAt: null, activatedBy: null,
    checklist: { legalProfile: 'DONE', kyc: 'VERIFIED', bankAccount: 'SUBMITTED', agreement: 'MISSING' },
    canActivate: false, missing: ['bankAccount', 'agreement'], kycDocuments: [], bankAccount: null, agreement: null, ...overrides,
  };
}

function open(step: string | null) {
  params = new BehaviorSubject(convertToParamMap(step ? { id: 'f-1', step } : { id: 'f-1' }));
  const fixture: ComponentFixture<FranchiseSetupPage> = TestBed.createComponent(FranchiseSetupPage);
  fixture.detectChanges();
  return fixture;
}

const el = (f: ComponentFixture<FranchiseSetupPage>) => f.nativeElement as HTMLElement;
const stepIds = (f: ComponentFixture<FranchiseSetupPage>) =>
  Array.from(el(f).querySelectorAll<HTMLButtonElement>('.setup__step')).map((b) => b.dataset['testid']);
const goTo = (f: ComponentFixture<FranchiseSetupPage>, step: string) => {
  params.next(convertToParamMap({ id: 'f-1', step }));
  f.detectChanges();
};
const onboardingStub = (f: ComponentFixture<FranchiseSetupPage>) =>
  f.debugElement.query((d) => d.componentInstance instanceof StubOnboarding)?.componentInstance as StubOnboarding | undefined;

describe('FranchiseSetupPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    granted = new Set(ADMIN);
    subject = 'franchise';
    routeId = 'f-1';
    history.replaceState({ name: 'RBM-00008' }, '');
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [FranchiseSetupPage, StubDetails, StubLegal, StubOnboarding],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule, HqProgress],
      providers: [
        provideRouter([]),
        { provide: HqPrivilegeAccessService, useValue: access },
        { provide: AppDialogService, useValue: dialogs },
        {
          provide: ActivatedRoute,
          useFactory: () => ({
            snapshot: { get paramMap() { return convertToParamMap(routeId ? { id: routeId } : {}); }, get data() { return { subject }; } },
            get paramMap() { return params.asObservable(); },
          }),
        },
      ],
    });
  });

  it('shows the six steps of an administrator, in order, on the step the route names, with the franchise code', () => {
    const fixture = open('details');

    expect(stepIds(fixture)).toEqual(['setup-step-details', 'setup-step-legal', 'setup-step-kyc', 'setup-step-bank', 'setup-step-agreement', 'setup-step-activation']);
    expect(fixture.componentInstance.current).toBe('details');
    expect(el(fixture).querySelector('[data-testid="setup-franchise-code"]')?.textContent).toContain('RBM-00008');
    expect(el(fixture).querySelector('app-franchise-form')).not.toBeNull();
  });

  it('opens a new franchise on its first step, with the other steps locked until it exists', () => {
    routeId = null;
    history.replaceState(null, '');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    params = new BehaviorSubject(convertToParamMap({}));
    const fixture: ComponentFixture<FranchiseSetupPage> = TestBed.createComponent(FranchiseSetupPage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    expect(page.creating).toBe(true);
    expect(page.current).toBe('details');
    expect(page.titleKey).toBe('accessManagement.franchises.openTitle');
    expect(el(fixture).querySelector('app-franchise-form')).not.toBeNull();
    expect(onboardingStub(fixture)).toBeUndefined();
    expect(page.steps.map((step) => page.statusOf(step))).not.toContain('done');
    expect((el(fixture).querySelector('[data-testid="setup-step-legal"]') as HTMLButtonElement).disabled).toBe(true);
    expect((el(fixture).querySelector('[data-testid="setup-next"]') as HTMLButtonElement).disabled).toBe(true);
    expect(page.hint).toBe('accessManagement.franchises.setup.openFirst');

    page.go('kyc');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('gives an owner their legal details and onboarding, without the terms step, and goes back to their own list', () => {
    subject = 'myFranchise';
    granted = new Set(['FRANCHISE_MINE_LEGAL_PROFILE_READ', 'FRANCHISE_MINE_ONBOARDING_READ']);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = open('details');

    expect(stepIds(fixture)).toEqual(['setup-step-legal', 'setup-step-kyc', 'setup-step-bank', 'setup-step-agreement', 'setup-step-activation']);
    expect(fixture.componentInstance.current).toBe('legal');
    fixture.componentInstance.go('bank');
    expect(navigate).toHaveBeenCalledWith(['/access-management', 'my-franchises', 'f-1', 'setup', 'bank'], { state: { name: 'RBM-00008' } });
    fixture.componentInstance.finish();
    expect(navigate).toHaveBeenLastCalledWith(['/access-management', 'my-franchises']);
  });

  it('hides the steps whose read privilege is missing, and says so when none is left', () => {
    granted = new Set(['FRANCHISE_LIST']);
    expect(stepIds(open('kyc'))).toEqual(['setup-step-details']);

    granted = new Set();
    const none = open('details');
    expect(none.componentInstance.hasSteps).toBe(false);
    expect(el(none).querySelector('.setup__layout')).toBeNull();
  });

  it('reads the onboarding once, at once, and shows one of its sections per step', () => {
    const fixture = open('details');
    const stub = onboardingStub(fixture)!;
    expect(stub).toBeDefined();
    expect(stub.section).toBeNull();

    goTo(fixture, 'bank');
    expect(onboardingStub(fixture)).toBe(stub);
    expect(stub.section).toBe('bank');
    goTo(fixture, 'activation');
    expect(stub.section).toBe('activation');
  });

  it("marks the steps done that the franchise service reports, and the onboarding status in the heading", () => {
    const fixture = open('details');
    onboardingStub(fixture)!.onboardingChanged.emit(onboarding());
    fixture.detectChanges();
    const page = fixture.componentInstance;

    expect(page.steps.filter((step) => page.statusOf(step) === 'done').map((step) => step.id)).toEqual(['details', 'legal', 'kyc']);
    expect(page.percent).toBe(50);
    expect(el(fixture).querySelector('[data-testid="setup-onboarding-status"]')?.textContent).toContain('franchiseOnboarding.status.ONBOARDING');

    onboardingStub(fixture)!.onboardingChanged.emit(onboarding({
      onboardingStatus: 'ONBOARDED', checklist: { legalProfile: 'DONE', kyc: 'VERIFIED', bankAccount: 'VERIFIED', agreement: 'DONE' },
    }));
    expect(page.percent).toBe(100);
  });

  it('follows the legal step over the checklist, and reads the onboarding again after the legal details are saved', () => {
    const fixture = open('legal');
    const page = fixture.componentInstance;
    onboardingStub(fixture)!.onboardingChanged.emit(onboarding());
    const legal = page.steps.find((step) => step.id === 'legal')!;

    page.onLegal(hqEmptyLegalProfile());
    expect(page.statusOf(legal)).toBe('todo');

    page.onLegalSaved({
      ...hqEmptyLegalProfile(), legalName: 'Ravi Traders',
      addresses: [{ kind: 'REGISTERED', line1: '1 Main Rd', city: 'Guntur' }],
    } as HqLegalProfile);
    expect(page.statusOf(legal)).toBe('done');
    expect(onboardingStub(fixture)!.load).toHaveBeenCalled();
  });

  it('shows the franchise status from the details step', () => {
    const fixture = open('details');
    fixture.componentInstance.onFranchise({ code: 'RBM-00009', status: 'SUSPENDED' } as Franchise);

    expect(fixture.componentInstance.franchiseCode).toBe('RBM-00009');
    expect(fixture.componentInstance.franchiseStatusLabel).toBe('accessManagement.franchises.statuses.SUSPENDED');
  });

  it('lists the missing fields of the current form step in the action bar, or asks to save first', () => {
    const fixture = open('legal');
    const legal = fixture.debugElement.query((d) => d.componentInstance instanceof StubLegal).componentInstance as StubLegal;

    legal.missing = ['City'];
    expect(fixture.componentInstance.hint).toBe('accessManagement.organizations.setup.fillIn');
    legal.missing = [];
    legal.pending = true;
    expect(fixture.componentInstance.hint).toBe('accessManagement.organizations.setup.saveFirst');
    expect(fixture.componentInstance.statusOf(fixture.componentInstance.steps[1])).toBe('unsaved');

    goTo(fixture, 'kyc');
    expect(fixture.componentInstance.hint).toBe('');
  });

  it('moves with Previous and Next, and finishes back on the franchises list', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = open('legal');

    expect(fixture.componentInstance.previous).toBe('details');
    expect(fixture.componentInstance.next).toBe('kyc');
    (el(fixture).querySelector('[data-testid="setup-next"]') as HTMLButtonElement).click();
    expect(navigate).toHaveBeenCalledWith(['/access-management', 'franchises', 'f-1', 'setup', 'kyc'], { state: { name: 'RBM-00008' } });

    goTo(fixture, 'activation');
    expect(el(fixture).querySelector('[data-testid="setup-next"]')).toBeNull();
    (el(fixture).querySelector('[data-testid="setup-finish"]') as HTMLButtonElement).click();
    expect(navigate).toHaveBeenLastCalledWith(['/access-management', 'franchises']);

    navigate.mockClear();
    fixture.componentInstance.go('activation');
    fixture.componentInstance.go(null);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('asks before leaving with unsaved edits, and lets go at once without them', async () => {
    const fixture = open('details');
    const details = fixture.debugElement.query((d) => d.componentInstance instanceof StubDetails).componentInstance as StubDetails;

    let answer: boolean | undefined;
    fixture.componentInstance.confirmDiscardChanges().subscribe((ok) => (answer = ok));
    expect(answer).toBe(true);
    expect(dialogs.confirm).not.toHaveBeenCalled();

    details.pending = true;
    expect(fixture.componentInstance.hasPendingChanges()).toBe(true);
    fixture.componentInstance.confirmDiscardChanges().subscribe();
    expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ variant: 'danger' }));
  });
});
