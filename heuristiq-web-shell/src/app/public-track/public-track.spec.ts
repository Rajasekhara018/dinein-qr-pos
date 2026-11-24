import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Subject } from 'rxjs';
import { vi } from 'vitest';
import { LanguageService, SUPPORTED_LANGUAGES } from '../shared/services/language-service';
import { PublicTrack } from './public-track';
import { PublicTrackMapFactory } from './public-track-map';
import { PublicTrackMap, PublicTrackResponse, PublicTrackUpdate } from './public-track.models';
import { PublicTrackService } from './public-track.service';

const TOKEN = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJ_-0123';
const LIVE: PublicTrackResponse = {
  status: 'DRIVER_ARRIVING',
  driverFirstName: 'Ravi',
  driverPhotoUrl: 'https://cdn.example/ravi.jpg',
  vehicleModel: 'Swift Dzire',
  vehicleNumber: 'KA01AB1234',
  vehicleType: 'SEDAN',
  lat: 12.97,
  lng: 77.59,
  heading: 10,
  locationUpdatedAt: '2026-11-04T10:15:30Z',
  pickup: { lat: 12.96, lng: 77.58, label: 'MG Road Metro' },
  drop: { lat: 13.0, lng: 77.6, label: 'Airport T1' },
  etaMinutes: 4,
};

describe('PublicTrack', () => {
  let updates: Subject<PublicTrackUpdate>;
  let watch: ReturnType<typeof vi.fn>;
  let map: { [K in keyof PublicTrackMap]: ReturnType<typeof vi.fn> };
  let create: ReturnType<typeof vi.fn>;
  let fragment: string | null;
  const language = {
    languages: SUPPORTED_LANGUAGES,
    currentLanguage: 'en',
    useBrowserLanguage: vi.fn(),
    setLanguage: vi.fn(),
  };

  beforeEach(async () => {
    updates = new Subject<PublicTrackUpdate>();
    watch = vi.fn(() => updates.asObservable());
    map = { update: vi.fn(), invalidateSize: vi.fn(), destroy: vi.fn() };
    create = vi.fn(() => Promise.resolve(map));
    fragment = TOKEN;
    language.useBrowserLanguage.mockClear();
    language.setLanguage.mockClear();
    await TestBed.configureTestingModule({
      imports: [PublicTrack, TranslateModule.forRoot()],
      providers: [
        { provide: PublicTrackService, useValue: { watch } },
        { provide: PublicTrackMapFactory, useValue: { create } },
        { provide: LanguageService, useValue: language },
        { provide: ActivatedRoute, useValue: { snapshot: { get fragment() { return fragment; } } } },
      ],
    }).compileComponents();
  });

  function render(): ComponentFixture<PublicTrack> {
    const fixture = TestBed.createComponent(PublicTrack);
    fixture.detectChanges();
    return fixture;
  }

  const text = (f: ComponentFixture<PublicTrack>): string => (f.nativeElement as HTMLElement).textContent ?? '';
  const flush = () => new Promise<void>((resolve) => setTimeout(resolve, 0));

  it('starts in the loading state, polls with the fragment token and sets noindex and no-referrer', () => {
    const f = render();
    expect(f.componentInstance.state).toBe('loading');
    expect(watch).toHaveBeenCalledWith(TOKEN);
    expect(language.useBrowserLanguage).toHaveBeenCalled();
    const meta = TestBed.inject(Meta);
    expect(meta.getTag('name="robots"')?.content).toContain('noindex');
    expect(meta.getTag('name="referrer"')?.content).toBe('no-referrer');
    expect(TestBed.inject(Title).getTitle()).toBe('publicTrack.pageTitle');
    expect(text(f)).toContain('publicTrack.loading');
    expect(create).toHaveBeenCalledTimes(1);
  });

  it('shows the link-not-valid state without any request when the fragment has no token', () => {
    fragment = null;
    const f = render();
    expect(f.componentInstance.state).toBe('invalid');
    expect(watch).not.toHaveBeenCalled();
    expect(create).not.toHaveBeenCalled();
    expect(text(f)).toContain('publicTrack.invalid.title');
  });

  it('shows the live trip: driver, vehicle, ETA, route, last update, and draws the map once fitted', async () => {
    const f = render();
    updates.next({ kind: 'live', data: LIVE });
    await flush();
    f.detectChanges();
    const c = f.componentInstance;
    expect(c.state).toBe('live');
    const page = text(f);
    expect(page).toContain('Ravi');
    expect(page).toContain('Swift Dzire');
    expect(page).toContain('KA01AB1234');
    expect(page).toContain('MG Road Metro');
    expect(page).toContain('Airport T1');
    expect(page).toContain('publicTrack.status.driverArriving');
    expect(page).toContain('publicTrack.eta.toPickup');
    expect(page).toContain('publicTrack.lastUpdated');
    const img = (f.nativeElement as HTMLElement).querySelector('img.public-track__photo') as HTMLImageElement;
    expect(img.getAttribute('referrerpolicy')).toBe('no-referrer');
    expect(map.update).toHaveBeenCalledWith(expect.objectContaining({ car: [12.97, 77.59] }), true);

    updates.next({ kind: 'live', data: { ...LIVE, status: 'RIDE_STARTED', etaMinutes: 12 } });
    await flush();
    f.detectChanges();
    expect(map.update).toHaveBeenLastCalledWith(expect.anything(), false);
    expect(c.etaText).toBe('publicTrack.eta.toDrop');
    c.recenter();
    expect(map.update).toHaveBeenLastCalledWith(expect.anything(), true);
  });

  it('keeps the last details marked as not current when a read fails, and shows the outage state with no data', async () => {
    const f = render();
    updates.next({ kind: 'unavailable', retryInMs: 10000, rateLimited: true });
    f.detectChanges();
    expect(f.componentInstance.state).toBe('unavailable');
    expect(text(f)).toContain('publicTrack.unavailable.title');

    updates.next({ kind: 'live', data: LIVE });
    await flush();
    updates.next({ kind: 'unavailable', retryInMs: 20000, rateLimited: false });
    f.detectChanges();
    expect(f.componentInstance.state).toBe('live');
    expect(f.componentInstance.stale).toBe(true);
    expect(text(f)).toContain('publicTrack.stale');
  });

  it('shows the trip-ended and link-expired states from the poller', () => {
    const f = render();
    updates.next({ kind: 'ended' });
    f.detectChanges();
    expect(text(f)).toContain('publicTrack.ended.title');
    updates.next({ kind: 'invalid' });
    f.detectChanges();
    expect(text(f)).toContain('publicTrack.invalid.title');
    expect(f.componentInstance.data).toBeNull();
  });

  it('falls back to text details when the map cannot load, and to a placeholder when the photo fails', async () => {
    create.mockReturnValue(Promise.reject(new Error('chunk')));
    const f = render();
    updates.next({ kind: 'live', data: { ...LIVE, lat: null, lng: null, etaMinutes: 0, driverFirstName: null } });
    await flush();
    f.detectChanges();
    const c = f.componentInstance;
    expect(c.mapFailed).toBe(true);
    expect(c.showMap).toBe(false);
    expect(text(f)).toContain('publicTrack.mapFailed');
    expect(text(f)).toContain('publicTrack.noLocation');
    expect(text(f)).toContain('publicTrack.driverFallback');
    expect(c.etaText).toBe('publicTrack.eta.now');
    const img = (f.nativeElement as HTMLElement).querySelector('img.public-track__photo') as HTMLImageElement;
    img.dispatchEvent(new Event('error'));
    f.detectChanges();
    expect(c.photoFailed).toBe(true);
    expect((f.nativeElement as HTMLElement).querySelector('img.public-track__photo')).toBeNull();
  });

  it('shows no ETA when the API gives none and the raw code for an unknown status', async () => {
    const f = render();
    updates.next({ kind: 'live', data: { status: 'SOMETHING_NEW', etaMinutes: null } });
    await flush();
    expect(f.componentInstance.etaText).toBe('');
    expect(f.componentInstance.statusLabel).toBe('SOMETHING_NEW');
  });

  it('switches language on request', () => {
    const f = render();
    f.componentInstance.onLanguageChange('ta');
    expect(language.setLanguage).toHaveBeenCalledWith('ta');
    expect(f.componentInstance.currentLanguage).toBe('en');
  });

  it('stops polling, destroys the map and removes its meta tags when left', async () => {
    const f = render();
    await flush();
    f.destroy();
    expect(updates.observed).toBe(false);
    expect(map.destroy).toHaveBeenCalled();
    const meta = TestBed.inject(Meta);
    expect(meta.getTag('name="robots"')).toBeNull();
    expect(meta.getTag('name="referrer"')).toBeNull();
  });

  it('destroys a map that finishes loading after the page was left', async () => {
    let resolveMap: (m: PublicTrackMap) => void = () => undefined;
    create.mockReturnValue(new Promise<PublicTrackMap>((resolve) => (resolveMap = resolve)));
    const f = render();
    f.destroy();
    resolveMap(map as unknown as PublicTrackMap);
    await flush();
    expect(map.destroy).toHaveBeenCalled();
  });
});
