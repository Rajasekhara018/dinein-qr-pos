import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { NAV_AREA_ALL, NAV_AREA_STATE_KEY } from './nav-areas.constants';

/**
 * The area of the menu the user is working in (an area key or NAV_AREA_ALL), shared by the sidebar's area switcher and the
 * header's app launcher. Remembered per browser; storage that is unavailable just means the choice lasts until reload.
 */
@Injectable({ providedIn: 'root' })
export class NavAreaService {
  private readonly subject = new BehaviorSubject<string>(NavAreaService.read());

  readonly selected$ = this.subject.asObservable();

  get selected(): string {
    return this.subject.value;
  }

  select(key: string): void {
    if (key === this.subject.value) return;
    this.subject.next(key);
    try {
      localStorage.setItem(NAV_AREA_STATE_KEY, key);
    } catch {
      // storage unavailable
    }
  }

  private static read(): string {
    try {
      return localStorage.getItem(NAV_AREA_STATE_KEY) ?? NAV_AREA_ALL;
    } catch {
      return NAV_AREA_ALL;
    }
  }
}
