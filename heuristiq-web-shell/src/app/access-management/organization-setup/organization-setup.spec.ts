import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqAppDialogFormValues, HqLegalProfile, HqPrivilegeAccessService, HqProgress, HqSharedLibModule, hqEmptyLegalProfile } from 'hq-shared-lib';
import { BehaviorSubject, EMPTY, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { OrganizationSetupPage } from './organization-setup';

// The step pages are tested on their own; here they are stand-ins that report what the stepper reads from them.
@Component({ selector: 'app-admin-form-page', standalone: false, template: '<p>details</p>' })
class StubDetails {
  @Input() embedded = false;
  @Input() embeddedPath = '';
  @Input() embeddedId: string | null = null;
  @Output() readonly entityLoaded = new EventEmitter<HqAppDialogFormValues>();
  @Output() readonly entitySaved = new EventEmitter<HqAppDialogFormValues>();
  pending = false;
  hasPendingChanges(): boolean { return this.pending; }
  missingFields(): string[] { return []; }
}

@Component({ selector: 'app-legal-profile-page', standalone: false, template: '<p>legal</p>' })
class StubLegal {
  @Input() embedded = false;
  @Input() ownerIdInput = '';
  @Output() readonly profileLoaded = new EventEmitter<HqLegalProfile>();
  @Output() readonly profileSaved = new EventEmitter<HqLegalProfile>();
  pending = false;
  hasPendingChanges(): boolean { return this.pending; }
  missingFields(): string[] { return []; }
}

@Component({ selector: 'app-organization-operating-model', standalone: false, template: '<p>operating</p>' })
class StubOperating { @Input() tenantId = ''; }
@Component({ selector: 'app-organization-applications', standalone: false, template: '<p>applications</p>' })
class StubApplications { @Input() tenantId = ''; }
@Component({ selector: 'app-organization-members', standalone: false, template: '<p>members</p>' })
class StubMembers { @Input() tenantId = ''; }

const ALL = new Set(['ORGANIZATION_READ', 'ORGANIZATION_LEGAL_PROFILE_READ', 'ORGANIZATION_OPERATING_MODEL_READ', 'ORGANIZATION_APPLICATION_LIST', 'ORGANIZATION_MEMBER_LIST']);
let granted: Set<string>;
const access = { changes$: EMPTY, can: (codes: string | readonly string[]) => [codes].flat().some((c) => granted.has(c)) };
const dialogs = { confirm: vi.fn(() => of(true)) };
let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

function open(step: string | null) {
  params = new BehaviorSubject(convertToParamMap(step ? { id: 'org-1', step } : { id: 'org-1' }));
  const fixture: ComponentFixture<OrganizationSetupPage> = TestBed.createComponent(OrganizationSetupPage);
  fixture.detectChanges();
  return fixture;
}

const el = (f: ComponentFixture<OrganizationSetupPage>) => f.nativeElement as HTMLElement;
const stepButtons = (f: ComponentFixture<OrganizationSetupPage>) => Array.from(el(f).querySelectorAll<HTMLButtonElement>('.setup__step'));
const goTo = (f: ComponentFixture<OrganizationSetupPage>, step: string) => {
  params.next(convertToParamMap({ id: 'org-1', step }));
  f.detectChanges();
};

describe('OrganizationSetupPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    granted = new Set(ALL);
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [OrganizationSetupPage, StubDetails, StubLegal, StubOperating, StubApplications, StubMembers],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule, HqProgress],
      providers: [
        provideRouter([]),
        { provide: HqPrivilegeAccessService, useValue: access },
        { provide: AppDialogService, useValue: dialogs },
        {
          provide: ActivatedRoute,
          useFactory: () => ({ snapshot: { paramMap: convertToParamMap({ id: 'org-1' }) }, get paramMap() { return params.asObservable(); } }),
        },
      ],
    });
  });

  it('shows the five steps, numbered in order, for someone who holds every read privilege', () => {
    const fixture = open('details');
    expect(stepButtons(fixture).map((b) => b.getAttribute('data-testid'))).toEqual([
      'setup-step-details', 'setup-step-legal', 'setup-step-operating-model', 'setup-step-applications', 'setup-step-members',
    ]);
    expect(stepButtons(fixture).map((b) => b.querySelector('.setup__num')?.textContent?.trim())).toEqual(['1', '2', '3', '4', '5']);
  });

  it('shows only the steps the person may open, and says so when there are none', () => {
    granted = new Set(['ORGANIZATION_LEGAL_PROFILE_READ', 'ORGANIZATION_MEMBER_LIST']);
    const fixture = open('legal');
    expect(stepButtons(fixture).map((b) => b.getAttribute('data-testid'))).toEqual(['setup-step-legal', 'setup-step-members']);
    expect(el(fixture).querySelector('app-admin-form-page')).toBeNull();

    granted = new Set();
    const none = open('details');
    expect(none.componentInstance.hasSteps).toBe(false);
    expect(el(none).textContent).toContain('accessManagement.organizations.setup.noAccess');
    expect(el(none).querySelector('.setup__steps')).toBeNull();
  });

  it('opens the step the route names, and the first allowed step when it names one that is not allowed', () => {
    expect(open('members').componentInstance.current).toBe('members');
    granted = new Set(['ORGANIZATION_APPLICATION_LIST', 'ORGANIZATION_MEMBER_LIST']);
    expect(open('details').componentInstance.current).toBe('applications');
    expect(open(null).componentInstance.current).toBe('applications');
  });

  it('gives each step the organization of the route', () => {
    const fixture = open('details');
    const details = fixture.debugElement.nativeElement.querySelector('app-admin-form-page');
    expect(details).not.toBeNull();
    goTo(fixture, 'operating-model');
    goTo(fixture, 'members');
    const members = fixture.debugElement.query((d) => d.name === 'app-organization-members').componentInstance as StubMembers;
    expect(members.tenantId).toBe('org-1');
  });

  it('creates a step when it is first opened and keeps it, hidden, so nothing typed in it is lost', () => {
    const fixture = open('details');
    expect(el(fixture).querySelector('app-legal-profile-page')).toBeNull();
    goTo(fixture, 'legal');
    const legalHost = el(fixture).querySelector('app-legal-profile-page') as HTMLElement;
    expect(legalHost).not.toBeNull();
    goTo(fixture, 'details');
    expect(el(fixture).querySelector('app-legal-profile-page')).toBe(legalHost);
    expect((legalHost.parentElement as HTMLElement).hidden).toBe(true);
    expect(((el(fixture).querySelector('app-admin-form-page') as HTMLElement).parentElement as HTMLElement).hidden).toBe(false);
  });

  it('marks a step with unsaved edits and counts the whole screen as changed while any step is', () => {
    const fixture = open('details');
    goTo(fixture, 'legal');
    const legal = fixture.debugElement.query((d) => d.name === 'app-legal-profile-page').componentInstance as StubLegal;
    expect(fixture.componentInstance.hasPendingChanges()).toBe(false);
    legal.pending = true;
    fixture.detectChanges();
    const legalButton = stepButtons(fixture)[1];
    expect(legalButton.classList.contains('is-unsaved')).toBe(true);
    expect(legalButton.querySelector('.setup__dot')).not.toBeNull();
    expect(stepButtons(fixture)[0].classList.contains('is-unsaved')).toBe(false);
    expect(fixture.componentInstance.hasPendingChanges()).toBe(true);
  });

  it('asks before leaving with unsaved edits and not otherwise', () => {
    const fixture = open('details');
    let answer: boolean | undefined;
    fixture.componentInstance.confirmDiscardChanges().subscribe((a) => (answer = a));
    expect(answer).toBe(true);
    expect(dialogs.confirm).not.toHaveBeenCalled();
    const details = fixture.debugElement.query((d) => d.name === 'app-admin-form-page').componentInstance as StubDetails;
    details.pending = true;
    fixture.componentInstance.confirmDiscardChanges().subscribe();
    expect(dialogs.confirm).toHaveBeenCalledTimes(1);
  });

  it('marks a step done once it was opened and holds what it needs, and keeps the other steps as they were', () => {
    const fixture = open('details');
    const details = fixture.debugElement.query((d) => d.name === 'app-admin-form-page').componentInstance as StubDetails;
    details.entityLoaded.emit({ name: 'Realboom Franchise' });
    fixture.detectChanges();
    expect(stepButtons(fixture)[0].classList.contains('is-done')).toBe(true);
    expect(el(fixture).querySelector('h1')?.textContent).toContain('Realboom Franchise');

    goTo(fixture, 'legal');
    const legal = fixture.debugElement.query((d) => d.name === 'app-legal-profile-page').componentInstance as StubLegal;
    legal.profileLoaded.emit(hqEmptyLegalProfile());
    fixture.detectChanges();
    expect(stepButtons(fixture)[1].classList.contains('is-done')).toBe(false);
    legal.profileSaved.emit({ ...hqEmptyLegalProfile(), legalName: 'Acme', addresses: [{ kind: 'REGISTERED', line1: '1 Road', city: 'Hyderabad', country: 'IN' }] });
    fixture.detectChanges();
    expect(stepButtons(fixture)[1].classList.contains('is-done')).toBe(true);
    expect(stepButtons(fixture)[0].classList.contains('is-done')).toBe(true);
  });

  it('moves with Previous and Next among the allowed steps and ends with Finish back to the list', () => {
    granted = new Set(['ORGANIZATION_READ', 'ORGANIZATION_MEMBER_LIST']);
    const fixture = open('details');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const previous = el(fixture).querySelector<HTMLButtonElement>('[data-testid="setup-previous"]') as HTMLButtonElement;
    expect(previous.disabled).toBe(true);
    (el(fixture).querySelector('[data-testid="setup-next"]') as HTMLButtonElement).click();
    expect(navigate).toHaveBeenCalledWith(['/access-management', 'organizations', 'org-1', 'setup', 'members'], { state: { name: '' } });

    goTo(fixture, 'members');
    expect(el(fixture).querySelector('[data-testid="setup-next"]')).toBeNull();
    (el(fixture).querySelector('[data-testid="setup-finish"]') as HTMLButtonElement).click();
    expect(navigate).toHaveBeenLastCalledWith(['/access-management', 'organizations']);
  });

  it('does not navigate to the step it is already on', () => {
    const fixture = open('details');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.componentInstance.go('details');
    fixture.componentInstance.go(null);
    expect(navigate).not.toHaveBeenCalled();
  });
});
