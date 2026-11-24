import { TestBed } from '@angular/core/testing';
import { HQ_CONFIG, HqSharedLibConfig } from './hq-common.config';
import { HqSharedLibModule } from './hq-shared-lib.module';

describe('HqSharedLibModule', () => {
  const config: HqSharedLibConfig = {
    gatewayUrl: 'http://gateway/api',
    adminApiUrl: 'http://admin/api/v1',
    currentUserId: () => 'user-1',
  };

  it('forRoot() returns the module with the app config provided under HQ_CONFIG', () => {
    const result = HqSharedLibModule.forRoot(config);
    expect(result.ngModule).toBe(HqSharedLibModule);
    expect(result.providers).toEqual([{ provide: HQ_CONFIG, useValue: config }]);
  });

  it('makes the config injectable when imported with forRoot()', () => {
    TestBed.configureTestingModule({ imports: [HqSharedLibModule.forRoot(config)] });
    const injected = TestBed.inject(HQ_CONFIG);
    expect(injected).toBe(config);
    expect(injected.currentUserId()).toBe('user-1');
  });
});
