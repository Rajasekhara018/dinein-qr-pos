import { Injectable, inject } from '@angular/core';
import { HqLegalAddressLookup, HqLegalProfileOption, HqPrivilegeAccessService } from 'hq-shared-lib';
import { Observable, map, of, shareReplay, switchMap, throwError } from 'rxjs';
import { FranchiseApi } from '../franchises/franchise-api';
import { Location } from '../franchises/franchise.models';
import {
  LEGAL_PROFILE_DISTRICT_TYPE, LEGAL_PROFILE_LOCATION_COUNTRIES, LEGAL_PROFILE_LOCATION_PAGE_SIZE, LEGAL_PROFILE_LOCATION_PRIVILEGES,
  LEGAL_PROFILE_STATE_TYPE,
} from './legal-profile.constants';
import { locationOptions, normalizeCountry, stateIdByName } from './legal-profile-location.helpers';

/**
 * Where the legal profile's state and district choices come from: the franchise location table, read through the existing
 * place-option reads of the franchise service (the same Search API `getall` path the franchise forms use; no new endpoint,
 * no copy of the data). Only the countries in LEGAL_PROFILE_LOCATION_COUNTRIES are served, and only for a person who may read
 * place options; for everything else the form keeps plain text boxes. The state and district are stored by name (the profile
 * already holds free text), so the district list is found from the state's name.
 */
@Injectable({ providedIn: 'root' })
export class LegalProfileLocationLookup implements HqLegalAddressLookup {
  private readonly api = inject(FranchiseApi);
  private readonly access = inject(HqPrivilegeAccessService);
  /** The states read per country, kept for the district reads that need a state's id. */
  private readonly states$ = new Map<string, Observable<Location[]>>();

  supports(country: string): boolean {
    return LEGAL_PROFILE_LOCATION_COUNTRIES.includes(normalizeCountry(country))
      && this.access.can(LEGAL_PROFILE_LOCATION_PRIVILEGES.states) && this.access.can(LEGAL_PROFILE_LOCATION_PRIVILEGES.districts);
  }

  states(country: string): Observable<HqLegalProfileOption[]> {
    return this.stateRows(country).pipe(map(locationOptions));
  }

  districts(country: string, state: string): Observable<HqLegalProfileOption[]> {
    return this.stateRows(country).pipe(
      switchMap((rows) => {
        const id = stateIdByName(rows, state);
        if (!id) return of([] as Location[]);
        return this.api.placeOptionChildren(id, { type: LEGAL_PROFILE_DISTRICT_TYPE, size: LEGAL_PROFILE_LOCATION_PAGE_SIZE }).pipe(map((page) => page.items ?? []));
      }),
      map(locationOptions),
    );
  }

  /** The states of a country, read once and shared; a failed read is forgotten so the retry asks again. */
  private stateRows(country: string): Observable<Location[]> {
    const code = normalizeCountry(country);
    if (!this.supports(code)) return throwError(() => new Error(`No location data for ${code}`));
    let rows$ = this.states$.get(code);
    if (!rows$) {
      rows$ = this.api.placeOptions({ countryCode: code, type: LEGAL_PROFILE_STATE_TYPE, size: LEGAL_PROFILE_LOCATION_PAGE_SIZE }).pipe(
        map((page) => page.items ?? []),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
      this.states$.set(code, rows$);
      rows$.subscribe({ error: () => this.states$.delete(code) });
    }
    return rows$;
  }
}
