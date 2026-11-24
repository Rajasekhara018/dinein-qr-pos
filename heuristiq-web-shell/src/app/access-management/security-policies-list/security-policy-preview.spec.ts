import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { UamService } from '../../shared/services/uam-service';
import { SecurityPolicyPreview } from './security-policy-preview';

describe('security policy preview', () => {
  const effective = { fingerprint: 'v1', settings: {}, policies: [] };
  const get = vi.fn();
  beforeEach(() => {
    get.mockReset().mockImplementation((url: string) => of(url.includes('/templates') ? [] : effective));
    TestBed.configureTestingModule({ imports: [SecurityPolicyPreview, TranslateModule.forRoot()], providers: [{ provide: UamService, useValue: { get } }] });
  });
  it('requests the selected organization and application and hides a preview after the scope changes', () => {
    const component = TestBed.createComponent(SecurityPolicyPreview).componentInstance;
    component.values = { tenantId: 'org', applicationId: 'app' };
    component.load();
    expect(get).toHaveBeenCalledWith('/security-policies/effective?tenantId=org&applicationId=app');
    expect(component.policy).toEqual(effective);
    expect(component.loadedScope).toBe(component.scopeKey);
    component.values = { tenantId: 'other' };
    expect(component.loadedScope).not.toBe(component.scopeKey);
  });
  it('shows a denied preview as an error and clears the loading state', () => {
    get.mockReturnValue(throwError(() => ({ error: { message: 'Access denied' } })));
    const component = TestBed.createComponent(SecurityPolicyPreview).componentInstance;
    component.load();
    expect(component.error).toContain('Access denied');
    expect(component.loading).toBe(false);
    expect(component.policy).toBeNull();
  });
});
