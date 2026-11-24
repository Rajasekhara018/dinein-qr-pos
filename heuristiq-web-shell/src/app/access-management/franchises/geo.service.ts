import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, catchError, of, shareReplay } from 'rxjs';
import { Districts, Subdivisions } from './franchise.models';
import { GEO_DISTRICTS_URL, GEO_SUBDIVISIONS_URL } from './franchises.constants';

/** Reads the states of every country, and the districts of a country, from files shipped with the app, once each. */
@Injectable({ providedIn: 'root' })
export class GeoService {
  private states?: Observable<Subdivisions>;
  private readonly districtsByCountry = new Map<string, Observable<Districts>>();

  constructor(private http: HttpClient) {}

  /** The states by country; empty when the file cannot be read, so the form falls back to typing a state. */
  subdivisions(): Observable<Subdivisions> {
    this.states ??= this.http.get<Subdivisions>(GEO_SUBDIVISIONS_URL).pipe(catchError(() => of({} as Subdivisions)), shareReplay(1));
    return this.states;
  }

  /** The districts of a country by state code; empty for a country without a list (the district is then typed). */
  districts(country: string): Observable<Districts> {
    const code = country.toUpperCase();
    let found = this.districtsByCountry.get(code);
    if (!found) {
      found = this.http.get<Districts>(`${GEO_DISTRICTS_URL}/${code}.json`).pipe(catchError(() => of({} as Districts)), shareReplay(1));
      this.districtsByCountry.set(code, found);
    }
    return found;
  }
}
