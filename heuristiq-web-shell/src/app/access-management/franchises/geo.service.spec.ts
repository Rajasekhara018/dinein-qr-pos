import { HttpClient } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { GeoService } from './geo.service';

describe('GeoService', () => {
  it('reads the states once and keeps them', () => {
    const http = { get: vi.fn(() => of({ IN: [['TG', 'Telangana']] })) };
    const geo = new GeoService(http as unknown as HttpClient);
    let first: unknown;
    let second: unknown;

    geo.subdivisions().subscribe((states) => (first = states));
    geo.subdivisions().subscribe((states) => (second = states));

    expect(first).toEqual({ IN: [['TG', 'Telangana']] });
    expect(second).toBe(first);
    expect(http.get).toHaveBeenCalledTimes(1);
    expect(http.get).toHaveBeenCalledWith('assets/geo/subdivisions.json');
  });

  it('reads the districts of a country by its upper-cased code, once per country', () => {
    const http = { get: vi.fn(() => of({ TG: [['536', 'Hyderabad']] })) };
    const geo = new GeoService(http as unknown as HttpClient);

    geo.districts('in').subscribe();
    geo.districts('IN').subscribe();
    geo.districts('AE').subscribe();

    expect(http.get).toHaveBeenCalledTimes(2);
    expect(http.get).toHaveBeenCalledWith('assets/geo/districts/IN.json');
    expect(http.get).toHaveBeenCalledWith('assets/geo/districts/AE.json');
  });

  it('answers with nothing, not an error, when a file cannot be read, so the form falls back to typing', () => {
    const http = { get: vi.fn(() => throwError(() => new Error('404'))) };
    const geo = new GeoService(http as unknown as HttpClient);
    let states: unknown = 'unset';
    let districts: unknown = 'unset';

    geo.subdivisions().subscribe((value) => (states = value));
    geo.districts('XX').subscribe((value) => (districts = value));

    expect(states).toEqual({});
    expect(districts).toEqual({});
  });
});
