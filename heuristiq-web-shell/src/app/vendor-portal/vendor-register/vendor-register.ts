import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../shared/services/api-service';
import { NotificationService } from '../../shared/services/notification-service';
import { VendorAuthService } from '../services/vendor-auth-service';
import { finalize, switchMap } from 'rxjs/operators';

import { BrandingService } from '../../shared/services/branding-service';
import { TranslateService } from '@ngx-translate/core';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';
import { VENDOR_FIELD_ERROR_KEYS } from './vendor-register.constants';
const VEHICLE_TYPES = ['Bike', 'Auto', 'Cab Economy', 'Cab Premium', 'Battery Cab'];

const GST_REGEX = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$/i;

@Component({
  selector: 'app-vendor-register',
  templateUrl: './vendor-register.html',
  styleUrl: './vendor-register.scss',
  standalone: false,
})
export class VendorRegister implements OnInit {
  /** Per-deployment branding (app name, logo, tagline), loaded at runtime by BrandingService. */
  readonly branding = inject(BrandingService).branding;
  currentStep = 0;
  submitting = false;
  submitError = '';
  vehicleTypes = VEHICLE_TYPES;
  selectedVehicleTypes: string[] = [];

  get steps(): { id: string; title: string; icon: string }[] {
    const t = (key: string) => this.translate.instant(key);
    return [
      { id: 'business', title: t('vendorPortal.register.steps.business'), icon: 'bi-building' },
      { id: 'tax',      title: t('vendorPortal.register.steps.tax'),      icon: 'bi-file-text' },
      { id: 'fleet',    title: t('vendorPortal.register.steps.fleet'),    icon: 'bi-truck' },
      { id: 'bank',     title: t('vendorPortal.register.steps.bank'),     icon: 'bi-credit-card' },
    ];
  }

  businessForm!: FormGroup;
  taxForm!: FormGroup;
  fleetForm!: FormGroup;
  bankForm!: FormGroup;

  constructor(
    private fb: FormBuilder,
    private api: ApiService,
    private vendorAuth: VendorAuthService,
    private notify: NotificationService,
    private router: Router,
    private translate: TranslateService,
  ) {}

  ngOnInit(): void {
    this.businessForm = this.fb.group({
      business_name: ['', Validators.required],
      owner_name:    ['', Validators.required],
      email:         ['', [Validators.required, Validators.email]],
      // Validation itself is provided by hq-phone-number-input's own NG_VALIDATORS (required +
      // valid international number); the control value is the E.164 string.
      phone:         [''],
      city:          ['', Validators.required],
    });

    this.taxForm = this.fb.group({
      gst_number: ['', [Validators.required, Validators.minLength(15), Validators.maxLength(15), Validators.pattern(GST_REGEX)]],
      pan_number: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(10)]],
    });

    this.fleetForm = this.fb.group({
      expected_fleet_size: [10, [Validators.required, Validators.min(10), Validators.max(40)]],
    });

    this.bankForm = this.fb.group({
      account_number: ['', Validators.required],
      ifsc:           ['', [Validators.required, Validators.minLength(11), Validators.maxLength(11)]],
      account_name:   ['', Validators.required],
    });
  }

  get fleetSize(): number {
    return this.fleetForm.get('expected_fleet_size')?.value ?? 10;
  }

  get progressPercent(): number {
    return (this.currentStep / (this.steps.length - 1)) * 100;
  }

  isStepActive(index: number): boolean {
    return index <= this.currentStep;
  }

  isStepCompleted(index: number): boolean {
    return index < this.currentStep;
  }

  toggleVehicleType(type: string): void {
    const idx = this.selectedVehicleTypes.indexOf(type);
    if (idx > -1) {
      this.selectedVehicleTypes = this.selectedVehicleTypes.filter(t => t !== type);
    } else {
      this.selectedVehicleTypes = [...this.selectedVehicleTypes, type];
    }
  }

  /** Translation key of the message a touched field shows (the first that applies), or null when it shows none. */
  fieldErrorKey(form: FormGroup, name: string): string | null {
    const control = form.get(name);
    if (!control?.touched || !control.errors) return null;
    const errors = control.errors;
    const match = (VENDOR_FIELD_ERROR_KEYS[name] ?? []).find(([names]) => names.some((n) => n === '*' || errors[n]));
    return match ? match[1] : null;
  }

  isVehicleSelected(type: string): boolean {
    return this.selectedVehicleTypes.includes(type);
  }

  validateCurrentStep(): boolean {
    switch (this.currentStep) {
      case 0:
        if (this.businessForm.invalid) {
          this.businessForm.markAllAsTouched();
          this.notify.error(this.translate.instant('vendorPortal.register.messages.businessFieldsRequired'));
          return false;
        }
        return true;

      case 1:
        if (this.taxForm.invalid) {
          this.taxForm.markAllAsTouched();
          const gstCtrl = this.taxForm.get('gst_number');
          const panCtrl = this.taxForm.get('pan_number');
          if (gstCtrl?.errors?.['required'] || panCtrl?.errors?.['required']) {
            this.notify.error(this.translate.instant('vendorPortal.register.messages.gstPanRequired'));
          } else if (gstCtrl?.errors?.['minlength'] || gstCtrl?.errors?.['maxlength']) {
            this.notify.error(this.translate.instant('vendorPortal.register.messages.gstLength'));
          } else if (gstCtrl?.errors?.['pattern']) {
            this.notify.error(this.translate.instant('vendorPortal.register.messages.gstInvalid'));
          } else if (panCtrl?.errors?.['minlength'] || panCtrl?.errors?.['maxlength']) {
            this.notify.error(this.translate.instant('vendorPortal.register.messages.panLength'));
          } else {
            this.notify.error(this.translate.instant('vendorPortal.register.messages.gstPanCheck'));
          }
          return false;
        }
        return true;

      case 2:
        if (this.fleetForm.invalid) {
          this.fleetForm.markAllAsTouched();
          this.notify.error(this.translate.instant('vendorPortal.register.messages.fleetSizeRange'));
          return false;
        }
        if (this.selectedVehicleTypes.length === 0) {
          this.notify.error(this.translate.instant('vendorPortal.register.messages.vehicleTypeRequired'));
          return false;
        }
        return true;

      case 3:
        if (this.bankForm.invalid) {
          this.bankForm.markAllAsTouched();
          const ifscCtrl = this.bankForm.get('ifsc');
          if (ifscCtrl?.errors?.['minlength'] || ifscCtrl?.errors?.['maxlength']) {
            this.notify.error(this.translate.instant('vendorPortal.register.messages.ifscLength'));
          } else {
            this.notify.error(this.translate.instant('vendorPortal.register.messages.bankFieldsRequired'));
          }
          return false;
        }
        return true;

      default:
        return false;
    }
  }

  nextStep(): void {
    if (this.validateCurrentStep()) {
      this.submitError = '';
      this.currentStep = Math.min(this.currentStep + 1, this.steps.length - 1);
    }
  }

  prevStep(): void {
    this.submitError = '';
    this.currentStep = Math.max(this.currentStep - 1, 0);
  }

  onSubmit(): void {
    if (!this.validateCurrentStep() || this.submitting) return;

    this.submitting = true;
    this.submitError = '';
    const bv = this.businessForm.value;
    const tv = this.taxForm.value;
    const fv = this.fleetForm.value;
    const bankv = this.bankForm.value;

    // Create the vendor's UAM login identity first, then submit the business/KYC application
    // carrying that id -- so the AdminAPI Vendor record is linked to its login identity from
    // the moment it's created, rather than resolved by phone lookup later (as done for drivers).
    this.vendorAuth.register({ name: bv.owner_name, mobile: bv.phone, email: bv.email, city: bv.city })
      .pipe(
        switchMap((uamUserId) => {
          const payload = {
            business_name:        bv.business_name,
            owner_name:           bv.owner_name,
            email:                bv.email,
            phone:                bv.phone,
            city:                 bv.city,
            gst_number:           (tv.gst_number as string).toUpperCase(),
            pan_number:           (tv.pan_number as string).toUpperCase(),
            expected_fleet_size:  parseInt(fv.expected_fleet_size, 10),
            vehicle_types:        this.selectedVehicleTypes,
            uam_user_id:          uamUserId,
            bank_details: {
              account_number: bankv.account_number,
              ifsc:           (bankv.ifsc as string).toUpperCase(),
              account_name:   bankv.account_name,
            },
          };
          return this.api.post<any>('/v1/admin/vendors/register', payload);
        }),
        finalize(() => {
          this.submitting = false;
        }),
      )
      .subscribe({
      next: (res) => {
        const vendorId = res?.vendor_id || res?.id;
        if (!vendorId) {
          this.submitError = this.translate.instant('vendorPortal.register.messages.vendorIdMissing');
          this.notify.error(this.submitError);
          return;
        }
        sessionStorage.setItem('railcab_vendor_application_id', vendorId);
        this.notify.success(this.translate.instant('vendorPortal.register.messages.registrationSuccess'));
        this.router.navigate(['/vendor/documents'], { state: { vendor_id: vendorId } });
      },
      error: (err) => {
        this.submitError = apiErrorMessage(err, this.translate.instant('vendorPortal.register.messages.registrationFailed'));
        this.notify.error(this.submitError);
      },
    });
  }
}
