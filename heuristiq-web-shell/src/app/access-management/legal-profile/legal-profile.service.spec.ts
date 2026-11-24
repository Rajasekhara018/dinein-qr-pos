import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { HqLegalProfile, hqEmptyLegalProfile } from 'hq-shared-lib';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { environment } from '../../../environments/environment';
import { legalProfileErrorKey, legalProfileSaveError } from './legal-profile.helpers';
import { LegalProfileService } from './legal-profile.service';
import { TranslateService } from '@ngx-translate/core';

describe('LegalProfileService', () => {
  let service: LegalProfileService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(LegalProfileService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it.each([
    ['organization', `${environment.uamApiUrl}/organizations/org%201/legal-profile`],
    ['franchise', `${environment.franchiseApiUrl}/org%201/legal-profile`],
    ['myFranchise', `${environment.franchiseApiUrl}/mine/org%201/legal-profile`],
  ] as const)('reads the %s profile from its own service', (subject, url) => {
    let result: HqLegalProfile | undefined;
    service.get(subject, 'org 1').subscribe((p) => (result = p));
    http.expectOne({ method: 'GET', url }).flush({ success: true, responseObject: { ...hqEmptyLegalProfile(), legalName: 'Acme' } });
    expect(result?.legalName).toBe('Acme');
  });

  it('saves the profile normalised, inside the request envelope', () => {
    service.save('franchise', 'f1', { ...hqEmptyLegalProfile(), legalName: ' Acme ', taxIds: [{ type: 'pan', value: 'abcde 1234f' }], version: 2 }).subscribe();
    const req = http.expectOne({ method: 'PUT', url: `${environment.franchiseApiUrl}/f1/legal-profile` });
    expect(req.request.body.requestObject).toMatchObject({ legalName: 'Acme', taxIds: [{ type: 'PAN', value: 'ABCDE1234F' }], version: 2 });
    req.flush({ success: true, responseObject: hqEmptyLegalProfile() });
  });

  it('turns a refused envelope into an error carrying the service message', () => {
    let error: { error?: { message?: string } } | undefined;
    service.get('organization', 'o1').subscribe({ error: (e) => (error = e) });
    http.expectOne(`${environment.uamApiUrl}/organizations/o1/legal-profile`).flush({ success: false, message: 'legalProfile.invalidCountry' });
    expect(error?.error?.message).toBe('legalProfile.invalidCountry');
  });

  it('maps service message keys to translation keys and explains a stale save', () => {
    const translate = TestBed.inject(TranslateService);
    expect(legalProfileErrorKey('legalProfile.invalidTaxId')).toBe('legalProfile.errors.invalidTaxId');
    expect(legalProfileSaveError({ status: 409 }, translate)).toBe('legalProfile.errors.staleVersion');
    expect(legalProfileSaveError({ status: 400, error: { message: 'legalProfile.invalidPostalCode' } }, translate)).toBe('legalProfile.errors.invalidPostalCode');
    expect(legalProfileSaveError({ status: 400, error: { message: 'Organization not found' } }, translate)).toBe('Organization not found');
  });
});
