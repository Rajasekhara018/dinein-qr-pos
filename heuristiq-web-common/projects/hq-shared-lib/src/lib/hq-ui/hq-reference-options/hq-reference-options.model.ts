import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';

/** One value of a reference list (religion, state, ...) as the matrimony API returns it. */
export interface HqReferenceOption {
  value: string;
  /** The country a state sits under, or the state a city sits under; empty for a flat list. */
  parentValue?: string | null;
  /** False for a value Ops switched off. */
  active?: boolean;
}

/** One page of a reference list. */
export interface HqReferencePage {
  content: HqReferenceOption[];
}

/**
 * Reads one reference list from the app's API: its category (RELIGION, COUNTRY, STATE, ...) and, for a state or city, the
 * parent value to list the children of. The app provides it (HQ_REFERENCE_FETCHER) because only the app knows the base URL,
 * the endpoint it may call (public before sign-in, the authenticated one after) and its own HTTP wrapper.
 */
export type HqReferenceFetcher = (category: string, parentValue?: string | null) => Observable<HqReferencePage>;

export const HQ_REFERENCE_FETCHER = new InjectionToken<HqReferenceFetcher>('HQ_REFERENCE_FETCHER');

/** Where the read of a list is. */
export type HqReferenceStatus = 'loading' | 'loaded' | 'failed';

/** A list for a control: its values (display names, in the order Ops set) and whether it is still on its way or failed. */
export interface HqReferenceList {
  status: HqReferenceStatus;
  values: string[];
}

/** The list a control that waits for a parent shows before that parent is chosen: nothing to read yet, nothing to wait for. */
export const HQ_REFERENCE_EMPTY: HqReferenceList = { status: 'loaded', values: [] };
