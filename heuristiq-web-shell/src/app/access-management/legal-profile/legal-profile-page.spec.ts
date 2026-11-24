import { TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { EMPTY, of, throwError } from 'rxjs';
import { HqLegalProfile, HqPrivilegeAccessService, HqSharedLibModule, hqEmptyLegalProfile } from 'hq-shared-lib';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { OrganizationContextService } from '../../shared/services/organization-context-service';
import { LegalProfilePage } from './legal-profile-page';
import { LegalProfileLocationLookup } from './legal-profile-location-lookup';
import { LegalProfileService } from './legal-profile.service';

const saved: HqLegalProfile = {
  ...hqEmptyLegalProfile(), entityType: 'PRIVATE_COMPANY', legalName: 'Acme Pvt Ltd', registrationCountry: 'IN',
  taxIds: [{ type: 'PAN', value: 'ABCDE1234F' }], version: 3,
};

const api = { get: vi.fn(), save: vi.fn() };
const lookup = { supports: vi.fn(() => false), states: vi.fn(() => of([])), districts: vi.fn(() => of([])) };
const notify = { success: vi.fn(), error: vi.fn() };
const dialogs = { confirm: vi.fn(() => of(true)) };
const organizations = { currentCountry: vi.fn(() => 'AE') };
let granted: Set<string> | null = null;
const access = { changes$: EMPTY, can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)) };
let routeData: Record<string, unknown> = {};

function create() {
  const fixture = TestBed.createComponent(LegalProfilePage);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
}

describe('LegalProfilePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    routeData = { subject: 'franchise' };
    api.get.mockReturnValue(of(saved));
    api.save.mockImplementation((_s: string, _id: string, p: HqLegalProfile) => of({ ...p, version: (p.version ?? 0) + 1 }));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [LegalProfilePage],
      imports: [ReactiveFormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      providers: [
        provideRouter([]),
        { provide: LegalProfileService, useValue: api },
        { provide: LegalProfileLocationLookup, useValue: lookup },
        { provide: NotificationService, useValue: notify },
        { provide: AppDialogService, useValue: dialogs },
        { provide: OrganizationContextService, useValue: organizations },
        { provide: HqPrivilegeAccessService, useValue: access },
        { provide: ActivatedRoute, useValue: { snapshot: { get data() { return routeData; }, paramMap: { get: () => 'f1' } } } },
      ],
    });
  });

  it('moves between franchise sections without losing unsaved edits', () => {
    const { component, fixture, el } = create();
    expect(component.sectionStepper).toBe(true);
    component.control.setValue({ ...saved, legalName: 'Edited franchise' });
    (el.querySelector('[data-testid="legal-step-tax"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(component.sectionIndex).toBe(1);
    expect(el.querySelector('[data-testid="legal-step-tax"]')?.getAttribute('aria-current')).toBe('step');
    component.goToSection(2);
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="save-legal-profile"]')).not.toBeNull();
    component.goToSection(0);
    expect(component.control.value.legalName).toBe('Edited franchise');
    expect(component.hasPendingChanges()).toBe(true);
  });

  it('opens the section containing the first validation error on save', () => {
    const { component } = create();
    component.goToSection(2);
    component.control.setValue({ ...saved, entityType: 'INVALID' });
    component.save();
    expect(component.showAllErrors).toBe(true);
    expect(component.sectionIndex).toBe(0);
    expect(api.save).not.toHaveBeenCalled();
  });

  it('keeps the organization form as one embedded section', () => {
    routeData = { subject: 'organization' };
    const { component, el } = create();
    expect(component.sectionStepper).toBe(false);
    expect(el.querySelector('.legal-steps')).toBeNull();
  });

  it("reads the subject's profile and shows it in the form", () => {
    const { component, el } = create();
    expect(api.get).toHaveBeenCalledWith('franchise', 'f1');
    expect(component.control.value.legalName).toBe('Acme Pvt Ltd');
    expect(component.defaultCountry).toBe('AE');
    expect(component.countries.length).toBeGreaterThan(200);
    expect(el.querySelector('hq-legal-profile-form')).not.toBeNull();
    expect(component.hasPendingChanges()).toBe(false);
  });

  it('saves the edited profile with the version it read, and shows the new version', () => {
    const { component } = create();
    component.control.setValue({ ...component.control.value, tradingName: 'Acme Rides' });
    expect(component.hasPendingChanges()).toBe(true);
    component.save();
    expect(api.save).toHaveBeenCalledWith('franchise', 'f1', expect.objectContaining({ tradingName: 'Acme Rides', version: 3 }));
    expect(notify.success).toHaveBeenCalledWith('legalProfile.messages.saved');
    expect(component.control.value.version).toBe(4);
    expect(component.hasPendingChanges()).toBe(false);
  });

  it('does not send an invalid profile and shows every error instead', () => {
    const { component } = create();
    component.control.setValue({ ...component.control.value, taxIds: [{ type: 'PAN', value: 'nope' }] });
    component.save();
    expect(api.save).not.toHaveBeenCalled();
    expect(component.showAllErrors).toBe(true);
    expect(notify.error).toHaveBeenCalledWith('legalProfile.messages.fixErrors');
  });

  it('tells the person when someone else changed the profile meanwhile, and a translated rule otherwise', () => {
    const { component } = create();
    api.save.mockReturnValueOnce(throwError(() => ({ status: 409 })));
    component.save();
    expect(notify.error).toHaveBeenCalledWith('legalProfile.errors.staleVersion');
    api.save.mockReturnValueOnce(throwError(() => ({ status: 400, error: { message: 'legalProfile.invalidTaxId' } })));
    component.save();
    expect(notify.error).toHaveBeenLastCalledWith('legalProfile.errors.invalidTaxId');
  });

  it('is read only without the update privilege: the form is disabled and there is no save button', () => {
    granted = new Set(['FRANCHISE_LEGAL_PROFILE_READ']);
    const { component, el } = create();
    expect(component.canEdit).toBe(false);
    expect(component.control.disabled).toBe(true);
    expect(el.querySelector('[data-testid="save-legal-profile"]')).toBeNull();
    component.save();
    expect(api.save).not.toHaveBeenCalled();
    expect(component.hasPendingChanges()).toBe(false);
  });

  it("uses the owner's privilege on the owner's page and goes back to My franchises", () => {
    routeData = { subject: 'myFranchise' };
    granted = new Set(['FRANCHISE_MINE_LEGAL_PROFILE_READ', 'FRANCHISE_MINE_LEGAL_PROFILE_UPDATE']);
    const { component } = create();
    expect(api.get).toHaveBeenCalledWith('myFranchise', 'f1');
    expect(component.canEdit).toBe(true);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    component.back();
    expect(navigate).toHaveBeenCalledWith(['/access-management', 'my-franchises']);
  });

  it('shows a retry when the profile cannot be read', () => {
    api.get.mockReturnValue(throwError(() => ({ status: 500 })));
    const { component, el } = create();
    expect(component.loadFailed).toBe(true);
    expect(el.textContent).toContain('legalProfile.messages.loadFailed');
    api.get.mockReturnValue(of(null));
    component.load();
    expect(component.loadFailed).toBe(false);
    expect(component.control.value.taxIds).toEqual([]);
  });

  it('hands the address lookup to the form', () => {
    const { fixture } = create();
    const form = fixture.nativeElement.querySelector('hq-legal-profile-form');
    expect(form).not.toBeNull();
    expect(fixture.componentInstance.addressLookup).toBe(lookup as unknown as LegalProfileLocationLookup);
  });

  describe('inside the organization setup stepper', () => {
    function createEmbedded() {
      const fixture = TestBed.createComponent(LegalProfilePage);
      fixture.componentInstance.embedded = true;
      fixture.componentInstance.ownerIdInput = 'org-7';
      const loaded: HqLegalProfile[] = [];
      const savedEvents: HqLegalProfile[] = [];
      fixture.componentInstance.profileLoaded.subscribe((p) => loaded.push(p));
      fixture.componentInstance.profileSaved.subscribe((p) => savedEvents.push(p));
      fixture.detectChanges();
      return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement, loaded, savedEvents };
    }

    beforeEach(() => {
      routeData = { subject: 'organization' };
    });

    it('reads the profile of the owner it is given, not the route id, and draws no heading, back link or Cancel', () => {
      const { el, loaded } = createEmbedded();
      expect(api.get).toHaveBeenCalledWith('organization', 'org-7');
      expect(loaded.length).toBe(1);
      expect(loaded[0].legalName).toBe('Acme Pvt Ltd');
      expect(el.querySelector('.ffp__header')).toBeNull();
      expect(el.textContent).not.toContain('legalProfile.cancel');
      expect(el.querySelector('[data-testid="save-legal-profile"]')).not.toBeNull();
    });

    it('saves through its own endpoint and reports the saved profile, staying where it is', () => {
      const { component, savedEvents } = createEmbedded();
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      component.control.setValue({ ...component.control.value, tradingName: 'Acme Rides' });
      component.save();
      expect(api.save).toHaveBeenCalledWith('organization', 'org-7', expect.objectContaining({ tradingName: 'Acme Rides' }));
      expect(savedEvents.length).toBe(1);
      expect(savedEvents[0].tradingName).toBe('Acme Rides');
      expect(navigate).not.toHaveBeenCalled();
      expect(component.hasPendingChanges()).toBe(false);
    });

    it('keeps the edits it holds when a save fails', () => {
      const { component, savedEvents } = createEmbedded();
      component.control.setValue({ ...component.control.value, tradingName: 'Acme Rides' });
      api.save.mockReturnValueOnce(throwError(() => ({ status: 500 })));
      component.save();
      expect(savedEvents).toEqual([]);
      expect(component.control.value.tradingName).toBe('Acme Rides');
      expect(component.hasPendingChanges()).toBe(true);
    });
  });

  it('asks before leaving with unsaved changes', () => {
    const { component } = create();
    let answer: boolean | undefined;
    component.confirmDiscardChanges().subscribe((a) => (answer = a));
    expect(dialogs.confirm).not.toHaveBeenCalled();
    expect(answer).toBe(true);
    component.control.setValue({ ...component.control.value, legalName: 'Changed' });
    component.confirmDiscardChanges().subscribe();
    expect(dialogs.confirm).toHaveBeenCalled();
  });
});
