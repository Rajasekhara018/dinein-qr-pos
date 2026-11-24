import { TestBed } from '@angular/core/testing';
import { NEVER, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { UamService } from '../shared/services/uam-service';
import { MY_OPERATING_MODEL_PATH } from './nav-operating-model.constants';
import { SessionOperatingModelService } from './session-operating-model.service';

describe('SessionOperatingModelService', () => {
  const uam = { get: vi.fn() };
  let service: SessionOperatingModelService;

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({ providers: [{ provide: UamService, useValue: uam }] });
    service = TestBed.inject(SessionOperatingModelService);
  });

  it('starts loading with no model', () => {
    expect(service.status()).toBe('loading');
    expect(service.model()).toBeNull();
  });

  it('reads the session organization model from the self-service path and becomes ready', () => {
    uam.get.mockReturnValue(of({ organizationId: 'acme', date: '2026-11-18', model: 'HYBRID' }));

    service.ensureLoaded();

    expect(uam.get).toHaveBeenCalledWith(MY_OPERATING_MODEL_PATH);
    expect(service.status()).toBe('ready');
    expect(service.model()).toBe('HYBRID');
  });

  it('asks only once when the model is known', () => {
    uam.get.mockReturnValue(of({ organizationId: 'acme', date: '2026-11-18', model: 'DIRECT' }));

    service.ensureLoaded();
    service.ensureLoaded();

    expect(uam.get).toHaveBeenCalledTimes(1);
  });

  it('does not ask again while a lookup is outstanding', () => {
    uam.get.mockReturnValue(NEVER);

    service.ensureLoaded();
    service.ensureLoaded();

    expect(uam.get).toHaveBeenCalledTimes(1);
    expect(service.status()).toBe('loading');
  });

  it('fails closed on an error and retries on the next call', () => {
    uam.get.mockReturnValueOnce(throwError(() => new Error('503')))
      .mockReturnValueOnce(of({ organizationId: 'acme', date: '2026-11-18', model: 'FRANCHISE' }));

    service.ensureLoaded();
    expect(service.status()).toBe('failed');
    expect(service.model()).toBeNull();

    service.ensureLoaded();
    expect(service.status()).toBe('ready');
    expect(service.model()).toBe('FRANCHISE');
  });

  it('treats an answer without a model as failed', () => {
    uam.get.mockReturnValue(of(null));

    service.ensureLoaded();

    expect(service.status()).toBe('failed');
    expect(service.model()).toBeNull();
  });
});
