import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HqSharedLibModule } from 'hq-shared-lib';
import { Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ServiceRegistry } from './service-registry';
import { ServiceRegistryService } from './service-registry-service';
import { REGISTRY_REFRESH_MS, REGISTRY_STATUSES } from './service-registry.constants';
import { RegistryApp } from './service-registry.models';

describe('ServiceRegistry', () => {
  const registry = { applications: vi.fn() };
  const apps: RegistryApp[] = [
    {
      name: 'UAM',
      instances: [
        { instanceId: 'uam-1', host: 'uam-a', ip: '10.0.0.1', port: 8080, status: 'UP', healthCheckUrl: 'http://uam-a/health', lastRenewedAt: '2026-03-04T10:00:00Z' },
        { instanceId: 'uam-2', host: 'uam-b', ip: 'uam-b', status: 'DOWN' },
      ],
    },
    { name: 'Gateway', instances: [{ instanceId: 'gw-1', ip: '10.0.0.9', status: 'STARTING' }] },
  ];
  let fixture: ComponentFixture<ServiceRegistry>;

  function create(): ServiceRegistry {
    TestBed.configureTestingModule({
      declarations: [ServiceRegistry],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      providers: [{ provide: ServiceRegistryService, useValue: registry }],
    });
    fixture = TestBed.createComponent(ServiceRegistry);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  function settle(ms = 1): void {
    vi.advanceTimersByTime(ms);
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
  }

  const text = (): string => fixture.nativeElement.textContent as string;
  const appNames = (): string[] => Array.from(fixture.nativeElement.querySelectorAll('.reg-app__head strong')).map((e) => (e as HTMLElement).textContent ?? '');

  beforeEach(() => {
    vi.useFakeTimers();
    registry.applications.mockReset();
    registry.applications.mockReturnValue(of(apps));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('reads the registry on open and shows services, instances, statuses and the up/total count', () => {
    const component = create();
    settle();

    expect(registry.applications).toHaveBeenCalledTimes(1);
    expect(appNames()).toEqual(['UAM', 'Gateway']);
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(3);
    expect(text()).toContain('uam-1');
    expect(text()).toContain('uam-a');
    expect(text()).toContain(':8080');
    expect(text()).toContain('serviceRegistry.upOfTotal');
    expect(component.rows[0].up).toBe(1);
    expect(component.rows[0].total).toBe(2);
    expect(component.lastUpdated).not.toBeNull();
    expect(component.hasError).toBe(false);
  });

  it('builds the summary cards from the registry', () => {
    const component = create();
    settle();

    expect(component.stats.map((s) => [s.label, s.value])).toEqual([
      ['serviceRegistry.stats.services', 2],
      ['serviceRegistry.stats.instances', 3],
      ['serviceRegistry.stats.up', 1],
      ['serviceRegistry.stats.notUp', 2],
    ]);
  });

  it('shows a health-check link only for instances that publish one', () => {
    create();
    settle();

    const links = fixture.nativeElement.querySelectorAll('.reg-links a') as NodeListOf<HTMLAnchorElement>;
    expect(links.length).toBe(1);
    expect(links[0].href).toBe('http://uam-a/health');
    expect(links[0].rel).toContain('noopener');
  });

  it('shows the loading message while the first read is outstanding', () => {
    const pending = new Subject<RegistryApp[]>();
    registry.applications.mockReturnValue(pending);
    const component = create();
    settle();

    expect(component.isLoading).toBe(true);
    expect(text()).toContain('serviceRegistry.loading');

    pending.next(apps);
    pending.complete();
    settle();
    expect(component.isLoading).toBe(false);
    expect(text()).not.toContain('serviceRegistry.loading');
  });

  it('shows the error message and no rows when the registry cannot be read, then recovers on refresh', () => {
    registry.applications.mockReturnValue(throwError(() => ({ status: 503 })));
    const component = create();
    settle();

    expect(component.hasError).toBe(true);
    expect(component.apps).toEqual([]);
    expect(text()).toContain('serviceRegistry.loadFailed');
    expect(text()).not.toContain('serviceRegistry.empty');

    registry.applications.mockReturnValue(of(apps));
    component.refresh();
    settle();
    expect(component.hasError).toBe(false);
    expect(appNames()).toEqual(['UAM', 'Gateway']);
  });

  it('shows the empty message when no service is registered', () => {
    registry.applications.mockReturnValue(of([]));
    create();
    settle();

    expect(text()).toContain('serviceRegistry.empty');
  });

  it('filters by status and by free text across name, host, ip and instance id, and clears the filters', () => {
    const component = create();
    settle();

    component.statusFilter = 'DOWN';
    component.applyFilters();
    expect(component.rows.map((r) => [r.name, r.total])).toEqual([['UAM', 1]]);

    component.statusFilter = '';
    component.query = ' GW-1 ';
    component.applyFilters();
    expect(component.rows.map((r) => r.name)).toEqual(['Gateway']);

    component.query = '10.0.0.1';
    component.applyFilters();
    expect(component.rows.map((r) => r.name)).toEqual(['UAM']);

    component.query = 'uam-b';
    component.applyFilters();
    expect(component.rows[0].instances.map((i) => i.instanceId)).toEqual(['uam-2']);

    component.query = 'zzz';
    component.applyFilters();
    expect(component.rows).toEqual([]);

    component.statusFilter = 'UP';
    component.clearFilters();
    expect(component.query).toBe('');
    expect(component.statusFilter).toBe('');
    expect(component.rows.length).toBe(2);
  });

  it('re-reads every interval while auto-refresh is on, and stops when switched off', () => {
    const component = create();
    settle();
    expect(registry.applications).toHaveBeenCalledTimes(1);

    settle(REGISTRY_REFRESH_MS);
    expect(registry.applications).toHaveBeenCalledTimes(2);

    component.autoRefresh = false;
    component.onAutoRefreshChange();
    expect(registry.applications).toHaveBeenCalledTimes(3);
    settle(REGISTRY_REFRESH_MS * 3);
    expect(registry.applications).toHaveBeenCalledTimes(3);
  });

  it('stops polling when destroyed', () => {
    create();
    settle();

    fixture.destroy();
    vi.advanceTimersByTime(REGISTRY_REFRESH_MS * 2);

    expect(registry.applications).toHaveBeenCalledTimes(1);
  });

  it('labels statuses through translations and falls back to the raw code', () => {
    const component = create();
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { 'serviceRegistry.statuses.UP': 'Running' });
    translate.use('en');

    expect(component.statusLabel('UP')).toBe('Running');
    expect(component.statusLabel('OUT_OF_SERVICE')).toBe('OUT_OF_SERVICE');
  });

  it('maps statuses to pill tones, defaulting to neutral', () => {
    const component = create();

    expect(component.statusTone('UP')).toBe('success');
    expect(component.statusTone('DOWN')).toBe('danger');
    expect(component.statusTone('STARTING')).toBe('warning');
    expect(component.statusTone('mystery')).toBe('neutral');
    expect(REGISTRY_STATUSES).toContain('OUT_OF_SERVICE');
  });

  it('colours the count of a service that has an instance not up and tracks rows by name and id', () => {
    const component = create();
    settle();

    const counts = fixture.nativeElement.querySelectorAll('.reg-count') as NodeListOf<HTMLElement>;
    expect(counts[0].classList).toContain('reg-count--bad');
    expect(component.trackByApp(0, component.rows[1])).toBe('Gateway');
    expect(component.trackByInstance(0, apps[0].instances[1])).toBe('uam-2');
    const pills = Array.from(fixture.nativeElement.querySelectorAll('.reg-pill')) as HTMLElement[];
    expect(pills.map((p) => p.classList.contains('reg-pill--danger'))).toContain(true);
  });

  it('applies the typed filters from the screen controls', () => {
    create();
    settle();
    const input = fixture.nativeElement.querySelector('input[type="text"]') as HTMLInputElement;

    input.value = 'gateway';
    input.dispatchEvent(new Event('input'));
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();

    expect(appNames()).toEqual(['Gateway']);
  });
});
