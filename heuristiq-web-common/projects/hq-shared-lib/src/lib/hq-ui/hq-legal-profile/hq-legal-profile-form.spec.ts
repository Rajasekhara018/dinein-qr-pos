import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { Observable, Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { HqSharedLibModule } from '../../hq-shared-lib.module';
import { HqLegalProfileForm } from './hq-legal-profile-form';
import { HqLegalAddressLookup, HqLegalProfile, HqLegalProfileOption } from './hq-legal-profile.model';
import { hqEmptyLegalProfile } from './hq-legal-profile.rules';
import { HQ_LEGAL_PROFILE_TEXTS, HQ_LEGAL_PROFILE_DEFAULT_TEXTS } from './hq-legal-profile.texts';

@Component({
  imports: [HqSharedLibModule, ReactiveFormsModule],
  template: `<hq-legal-profile-form [formControl]="control" [countries]="countries" [showAllErrors]="showAll" defaultCountry="IN" [addressLookup]="lookup"></hq-legal-profile-form>`,
})
class Host {
  control = new FormControl<HqLegalProfile | null>(null);
  countries = [{ value: 'IN', label: 'India (IN)' }, { value: 'AE', label: 'United Arab Emirates (AE)' }];
  showAll = false;
  lookup: HqLegalAddressLookup | null = null;
}

type FakeLookup = HqLegalAddressLookup & { states: ReturnType<typeof vi.fn>; districts: ReturnType<typeof vi.fn> };

/** A lookup that supports India only; its lists are the test's own so the calls can be counted. */
function fakeLookup(): FakeLookup {
  const states = vi.fn((): Observable<HqLegalProfileOption[]> => of([{ value: 'Telangana', label: 'Telangana' }, { value: 'Karnataka', label: 'Karnataka' }]));
  const districts = vi.fn((_country: string, state: string): Observable<HqLegalProfileOption[]> =>
    of(state === 'Telangana' ? [{ value: 'Hyderabad', label: 'Hyderabad' }] : [{ value: 'Bengaluru Urban', label: 'Bengaluru Urban' }]));
  return { supports: (country: string) => country === 'IN', states, districts };
}

describe('HqLegalProfileForm', () => {
  let fixture: ComponentFixture<Host>;
  let host: Host;
  let form: HqLegalProfileForm;
  let lookup: FakeLookup;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Host],
      providers: [{ provide: HQ_LEGAL_PROFILE_TEXTS, useValue: { ...HQ_LEGAL_PROFILE_DEFAULT_TEXTS, error: (key: string) => `T:${key}` } }],
    }).compileComponents();
    fixture = TestBed.createComponent(Host);
    host = fixture.componentInstance;
    lookup = fakeLookup();
    host.lookup = lookup;
    fixture.detectChanges();
    form = fixture.debugElement.query(By.directive(HqLegalProfileForm)).componentInstance;
  });

  function render(): void {
    fixture.componentRef.changeDetectorRef.markForCheck();
    fixture.detectChanges();
  }

  it('shows the saved profile and reports edits to the control', () => {
    host.control.setValue({ ...hqEmptyLegalProfile(), legalName: 'Acme Pvt Ltd', taxIds: [{ type: 'PAN', value: 'ABCDE1234F' }], version: 2 });
    render();
    const inputs = fixture.nativeElement.querySelectorAll('input.legal-profile__control[type=text]') as NodeListOf<HTMLInputElement>;
    expect(inputs[0].value).toBe('Acme Pvt Ltd');
    form.setField('tradingName', 'Acme Rides');
    expect(host.control.value?.tradingName).toBe('Acme Rides');
    expect(host.control.value?.version).toBe(2);
    expect(host.control.valid).toBe(true);
  });

  it('is invalid with a wrong tax id and shows the translated message once the field is left', () => {
    form.setField('registrationCountry', 'IN');
    form.addTaxId();
    expect(host.control.value?.taxIds[0].type).toBe('PAN');
    form.setTaxId(0, 'value', 'nope');
    render();
    expect(host.control.invalid).toBe(true);
    expect(form.fieldError('taxIds.0.value')).toBe('');
    form.touch('taxIds.0.value');
    render();
    expect(fixture.nativeElement.textContent).toContain('T:legalProfile.invalidTaxId');
    form.removeTaxId(0);
    expect(host.control.valid).toBe(true);
  });

  it('adds the next unused tax type each time', () => {
    form.setField('registrationCountry', 'IN');
    form.addTaxId();
    form.addTaxId();
    expect(host.control.value?.taxIds.map((t) => t.type)).toEqual(['PAN', 'GSTIN']);
  });

  it('keeps a registered address only once something is typed, starting in the default country', () => {
    form.setAddress('REGISTERED', 'line1', '12 MG Road');
    expect(host.control.value?.addresses).toEqual([
      { kind: 'REGISTERED', line1: '12 MG Road', line2: null, city: '', region: null, postalCode: null, country: 'IN' },
    ]);
    form.setAddress('REGISTERED', 'line1', '');
    expect(host.control.value?.addresses).toEqual([]);
  });

  it('adds and drops the operating address, and clears the postal code for a country without them', () => {
    form.setOperatingDifferent(true);
    expect(form.hasOperatingAddress).toBe(true);
    form.setAddress('OPERATING', 'postalCode', '12345');
    form.setAddress('OPERATING', 'country', 'AE');
    expect(form.address('OPERATING')?.postalCode).toBeNull();
    expect(form.noPostalCodes('OPERATING')).toBe(true);
    form.setOperatingDifferent(false);
    expect(host.control.value?.addresses).toEqual([]);
  });

  it('shows every error after a save attempt', () => {
    form.setAddress('REGISTERED', 'line1', 'x');
    host.showAll = true;
    render();
    expect(form.fieldError('addresses.REGISTERED.city')).toBe('T:legalProfile.addressRequired');
    expect(host.control.errors?.['legalProfile'].length).toBeGreaterThan(0);
  });

  it('can be disabled by the form', async () => {
    host.control.disable();
    await fixture.whenStable();
    render();
    expect(form.disabled).toBe(true);
    expect((fixture.nativeElement.querySelector('hq-searchable-select input') as HTMLInputElement).disabled).toBe(true);
  });

  it('draws every list as the shared searchable select, never a native select', () => {
    form.setTaxRegistered(true);
    form.addTaxId();
    render();
    expect(fixture.nativeElement.querySelector('select')).toBeNull();
    for (const id of ['entity-type', 'registration-country', 'tax-type', 'country-REGISTERED', 'state-REGISTERED', 'district-REGISTERED']) {
      expect(fixture.nativeElement.querySelector(`hq-searchable-select[data-testid="${id}"]`), id).not.toBeNull();
    }
  });

  describe('state and district lists', () => {
    it('reads the states of a supported country once, when its address block shows', () => {
      expect(lookup.states).toHaveBeenCalledTimes(1);
      expect(lookup.states).toHaveBeenCalledWith('IN');
      expect(form.statesStatus('REGISTERED')).toBe('ready');
      expect(form.stateItems('REGISTERED').map((o) => o.label)).toEqual(['Telangana', 'Karnataka']);
      form.setOperatingDifferent(true);
      expect(lookup.states).toHaveBeenCalledTimes(1);
      expect(form.stateItems('OPERATING').map((o) => o.value)).toEqual(['Telangana', 'Karnataka']);
    });

    it('reads the districts of the chosen state and blocks the district picker until a state is chosen', () => {
      expect(form.districtBlockedHint('REGISTERED')).toBe('Choose the state first.');
      expect(lookup.districts).not.toHaveBeenCalled();
      form.setAddress('REGISTERED', 'region', 'Telangana');
      expect(lookup.districts).toHaveBeenCalledWith('IN', 'Telangana');
      expect(form.districtBlockedHint('REGISTERED')).toBeNull();
      expect(form.districtItems('REGISTERED').map((o) => o.label)).toEqual(['Hyderabad']);
    });

    it('clears the district when the state changes, and the state and district when the country changes', () => {
      form.setAddress('REGISTERED', 'region', 'Telangana');
      form.setAddress('REGISTERED', 'city', 'Hyderabad');
      expect(host.control.value?.addresses[0]).toMatchObject({ region: 'Telangana', city: 'Hyderabad' });

      form.setAddress('REGISTERED', 'region', 'Karnataka');
      expect(host.control.value?.addresses[0]).toMatchObject({ region: 'Karnataka', city: '' });
      expect(form.districtItems('REGISTERED').map((o) => o.label)).toEqual(['Bengaluru Urban']);

      form.setAddress('REGISTERED', 'city', 'Bengaluru Urban');
      form.setAddress('REGISTERED', 'country', 'AE');
      expect(form.addressView('REGISTERED')).toMatchObject({ country: 'AE', region: null, city: '' });
      expect(host.control.value?.addresses).toEqual([]);
    });

    it('keeps the district when the same state is chosen again', () => {
      form.setAddress('REGISTERED', 'region', 'Telangana');
      form.setAddress('REGISTERED', 'city', 'Hyderabad');
      form.setAddress('REGISTERED', 'region', 'Telangana');
      expect(host.control.value?.addresses[0].city).toBe('Hyderabad');
    });

    it('keeps plain text boxes, and asks for no list, in a country the lookup does not support', () => {
      form.setAddress('REGISTERED', 'country', 'AE');
      render();
      expect(form.listsActive('REGISTERED')).toBe(false);
      expect(form.cityLabel('REGISTERED')).toBe('City');
      expect(fixture.nativeElement.querySelector('hq-searchable-select[data-testid="state-REGISTERED"]')).toBeNull();
      expect(fixture.nativeElement.querySelector('hq-searchable-select[data-testid="district-REGISTERED"]')).toBeNull();
      form.setAddress('REGISTERED', 'region', 'Dubai');
      form.setAddress('REGISTERED', 'city', 'Deira');
      expect(host.control.value?.addresses[0]).toMatchObject({ country: 'AE', region: 'Dubai', city: 'Deira' });
      expect(lookup.states).toHaveBeenCalledTimes(1);
      expect(lookup.districts).not.toHaveBeenCalled();
    });

    it('shows a loading state while a list is on its way and the choices when it arrives', () => {
      const pending = new Subject<HqLegalProfileOption[]>();
      lookup.states.mockReturnValueOnce(pending);
      form.retryStates('REGISTERED');
      expect(form.statesStatus('REGISTERED')).toBe('loading');
      expect(form.stateItems('REGISTERED')).toEqual([]);
      pending.next([{ value: 'Kerala', label: 'Kerala' }]);
      expect(form.statesStatus('REGISTERED')).toBe('ready');
      expect(form.stateItems('REGISTERED').map((o) => o.label)).toEqual(['Kerala']);
    });

    it('marks a list that could not be read as failed, does not repeat it on every edit, and reads again on retry', () => {
      lookup.states.mockReset();
      lookup.states.mockReturnValueOnce(throwError(() => new Error('down')));
      form.retryStates('REGISTERED');
      expect(form.statesStatus('REGISTERED')).toBe('failed');
      expect(lookup.states).toHaveBeenCalledTimes(1);
      form.setAddress('REGISTERED', 'line1', 'typing');
      expect(lookup.states).toHaveBeenCalledTimes(1);
      lookup.states.mockReturnValueOnce(of([{ value: 'Goa', label: 'Goa' }]));
      form.retryStates('REGISTERED');
      expect(lookup.states).toHaveBeenCalledTimes(2);
      expect(form.statesStatus('REGISTERED')).toBe('ready');
      expect(form.stateItems('REGISTERED').map((o) => o.label)).toEqual(['Goa']);
    });

    it('uses no lists at all when the app gives no lookup', () => {
      host.lookup = null;
      render();
      expect(form.listsActive('REGISTERED')).toBe(false);
      expect(fixture.nativeElement.querySelector('hq-searchable-select[data-testid="state-REGISTERED"]')).toBeNull();
    });
  });
});
