import { TestBed } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { BrandingService } from '../../shared/services/branding-service';
import { VendorRegister } from './vendor-register';

describe('VendorRegister', () => {
  const api = { post: vi.fn((_url: string, _body: unknown): Observable<unknown> => of({ vendor_id: 'ven1' })) };
  const vendorAuth = { register: vi.fn((_d: unknown): Observable<string> => of('uam1')) };
  const notify = { error: vi.fn(), success: vi.fn() };
  const router = { navigate: vi.fn() };
  const translate = { instant: vi.fn((key: string) => key) };

  function make(): VendorRegister {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [{ provide: BrandingService, useValue: { branding: { appName: 'X' } } }] });
    const c = TestBed.runInInjectionContext(() =>
      new VendorRegister(new FormBuilder(), api as never, vendorAuth as never, notify as never, router as never, translate as never));
    c.ngOnInit();
    return c;
  }

  const fillBusiness = (c: VendorRegister) =>
    c.businessForm.setValue({ business_name: 'Acme', owner_name: 'Asha', email: 'a@x.io', phone: '+919999999999', city: 'Pune' });
  const fillTax = (c: VendorRegister) => c.taxForm.setValue({ gst_number: '27ABCDE1234F1Z5', pan_number: 'abcde1234f' });
  const fillBank = (c: VendorRegister) => c.bankForm.setValue({ account_number: '123', ifsc: 'sbin0001234', account_name: 'Acme' });
  function fillAll(c: VendorRegister): void {
    fillBusiness(c);
    fillTax(c);
    c.fleetForm.setValue({ expected_fleet_size: 12 });
    c.toggleVehicleType('Bike');
    fillBank(c);
    c.currentStep = 3;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    api.post.mockReturnValue(of({ vendor_id: 'ven1' }));
    vendorAuth.register.mockReturnValue(of('uam1'));
  });

  it('exposes four titled steps and tracks progress through them', () => {
    const c = make();
    expect(c.steps.map((s) => s.id)).toEqual(['business', 'tax', 'fleet', 'bank']);
    expect(c.steps[0].title).toBe('vendorPortal.register.steps.business');
    expect(c.progressPercent).toBe(0);
    c.currentStep = 2;
    expect(c.progressPercent).toBeCloseTo(66.67, 1);
    expect([c.isStepActive(2), c.isStepActive(3), c.isStepCompleted(1), c.isStepCompleted(2)]).toEqual([true, false, true, false]);
    expect(c.fleetSize).toBe(10);
  });

  it('toggles vehicle types on and off', () => {
    const c = make();
    c.toggleVehicleType('Auto');
    c.toggleVehicleType('Bike');
    expect(c.isVehicleSelected('Auto')).toBe(true);
    c.toggleVehicleType('Auto');
    expect(c.selectedVehicleTypes).toEqual(['Bike']);
    expect(c.isVehicleSelected('Auto')).toBe(false);
  });

  describe('step validation', () => {
    it('blocks an incomplete business step with a message, and lets a complete one through', () => {
      const c = make();
      c.nextStep();
      expect(c.currentStep).toBe(0);
      expect(notify.error).toHaveBeenCalledWith('vendorPortal.register.messages.businessFieldsRequired');
      expect(c.businessForm.touched).toBe(true);
      fillBusiness(c);
      c.nextStep();
      expect(c.currentStep).toBe(1);
    });

    it.each([
      [{ gst_number: '', pan_number: '' }, 'gstPanRequired'],
      [{ gst_number: '27ABC', pan_number: 'ABCDE1234F' }, 'gstLength'],
      [{ gst_number: '27abcde1234f1z5'.replace('z', '!'), pan_number: 'ABCDE1234F' }, 'gstInvalid'],
      [{ gst_number: '27ABCDE1234F1Z5', pan_number: 'SHORT' }, 'panLength'],
    ])('explains an invalid tax step %#', (value, key) => {
      const c = make();
      c.currentStep = 1;
      c.taxForm.setValue(value);
      expect(c.validateCurrentStep()).toBe(false);
      expect(notify.error).toHaveBeenCalledWith('vendorPortal.register.messages.' + key);
    });

    it('accepts a valid GST and PAN', () => {
      const c = make();
      c.currentStep = 1;
      fillTax(c);
      expect(c.validateCurrentStep()).toBe(true);
    });

    it('needs a fleet size in range and at least one vehicle type', () => {
      const c = make();
      c.currentStep = 2;
      c.fleetForm.setValue({ expected_fleet_size: 5 });
      expect(c.validateCurrentStep()).toBe(false);
      expect(notify.error).toHaveBeenLastCalledWith('vendorPortal.register.messages.fleetSizeRange');
      c.fleetForm.setValue({ expected_fleet_size: 41 });
      expect(c.validateCurrentStep()).toBe(false);
      c.fleetForm.setValue({ expected_fleet_size: 20 });
      expect(c.fleetSize).toBe(20);
      expect(c.validateCurrentStep()).toBe(false);
      expect(notify.error).toHaveBeenLastCalledWith('vendorPortal.register.messages.vehicleTypeRequired');
      c.toggleVehicleType('Bike');
      expect(c.validateCurrentStep()).toBe(true);
    });

    it('needs complete bank details with an 11-character IFSC', () => {
      const c = make();
      c.currentStep = 3;
      expect(c.validateCurrentStep()).toBe(false);
      expect(notify.error).toHaveBeenLastCalledWith('vendorPortal.register.messages.bankFieldsRequired');
      c.bankForm.setValue({ account_number: '1', ifsc: 'SHORT', account_name: 'A' });
      expect(c.validateCurrentStep()).toBe(false);
      expect(notify.error).toHaveBeenLastCalledWith('vendorPortal.register.messages.ifscLength');
      fillBank(c);
      expect(c.validateCurrentStep()).toBe(true);
    });

    it('rejects an unknown step', () => {
      const c = make();
      c.currentStep = 9;
      expect(c.validateCurrentStep()).toBe(false);
    });

    it('goes back without going below the first step, clearing the last error', () => {
      const c = make();
      c.submitError = 'x';
      c.prevStep();
      expect(c.currentStep).toBe(0);
      expect(c.submitError).toBe('');
    });

    it('does not move past the last step', () => {
      const c = make();
      fillAll(c);
      c.nextStep();
      expect(c.currentStep).toBe(3);
    });
  });

  describe('submitting', () => {
    it('creates the login first, then registers the business with the login id and upper-cased codes', () => {
      const c = make();
      fillAll(c);
      c.onSubmit();
      expect(vendorAuth.register).toHaveBeenCalledWith({ name: 'Asha', mobile: '+919999999999', email: 'a@x.io', city: 'Pune' });
      expect(api.post).toHaveBeenCalledWith('/v1/admin/vendors/register', {
        business_name: 'Acme', owner_name: 'Asha', email: 'a@x.io', phone: '+919999999999', city: 'Pune',
        gst_number: '27ABCDE1234F1Z5', pan_number: 'ABCDE1234F', expected_fleet_size: 12, vehicle_types: ['Bike'], uam_user_id: 'uam1',
        bank_details: { account_number: '123', ifsc: 'SBIN0001234', account_name: 'Acme' },
      });
      expect(sessionStorage.getItem('railcab_vendor_application_id')).toBe('ven1');
      expect(notify.success).toHaveBeenCalledWith('vendorPortal.register.messages.registrationSuccess');
      expect(router.navigate).toHaveBeenCalledWith(['/vendor/documents'], { state: { vendor_id: 'ven1' } });
      expect(c.submitting).toBe(false);
    });

    it('accepts an id field instead of vendor_id', () => {
      api.post.mockReturnValue(of({ id: 'alt1' }));
      const c = make();
      fillAll(c);
      c.onSubmit();
      expect(router.navigate).toHaveBeenCalledWith(['/vendor/documents'], { state: { vendor_id: 'alt1' } });
    });

    it('reports a response without an id and stays on the page', () => {
      api.post.mockReturnValue(of({}));
      const c = make();
      fillAll(c);
      c.onSubmit();
      expect(c.submitError).toBe('vendorPortal.register.messages.vendorIdMissing');
      expect(notify.error).toHaveBeenCalledWith('vendorPortal.register.messages.vendorIdMissing');
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('shows the server detail when registration fails, else a generic message', () => {
      const c = make();
      fillAll(c);
      api.post.mockReturnValue(throwError(() => ({ error: { detail: 'GST already used' } })));
      c.onSubmit();
      expect(c.submitError).toBe('GST already used');
      vendorAuth.register.mockReturnValue(throwError(() => ({})));
      c.onSubmit();
      expect(c.submitError).toBe('vendorPortal.register.messages.registrationFailed');
      expect(c.submitting).toBe(false);
    });

    it('does not submit while invalid or already submitting', () => {
      const c = make();
      c.currentStep = 3;
      c.onSubmit();
      expect(vendorAuth.register).not.toHaveBeenCalled();
      fillAll(c);
      c.submitting = true;
      c.onSubmit();
      expect(vendorAuth.register).not.toHaveBeenCalled();
    });
  });
});
