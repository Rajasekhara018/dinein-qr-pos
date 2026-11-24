import { Injectable, inject, signal } from '@angular/core';
import { HqApplicationsService } from 'hq-shared-lib';
import { Observable } from 'rxjs';
import { applicationsFromToken } from '../shared/services/organization-context.helpers';
import { ACCESS_TOKEN_KEY } from './home.constants';
import { HomeLoadStatus } from './home.models';

/**
 * What the Applications hub needs to know about applications: the ones the session's organization is subscribed to (UAM's
 * GET /identity/applications, readable by any signed-in user, organization taken from the token) and the ones the access token
 * grants the user. If the subscription lookup fails the status becomes 'failed' and the hub shows nothing (fail closed). The sidebar and header read the same signals.
 */
@Injectable({ providedIn: 'root' })
export class HomeApplicationsService {
  private readonly applications = inject(HqApplicationsService);

  readonly status = signal<HomeLoadStatus>('loading');
  /** Codes of the applications the organization is subscribed to (empty until loaded). */
  readonly subscribedCodes = signal<string[]>([]);

  private requested = false;
  private inFlight = false;

  /**
   * For the sidebar and header: starts the lookup once, and again only after a failure (a quiet retry on the next navigation).
   * Does nothing while a request is outstanding or once the list is known. The organization switch reloads the page, which
   * resets this state.
   */
  ensureLoaded(): void {
    if (this.inFlight || (this.requested && this.status() !== 'failed')) return;
    this.load().subscribe();
  }

  /** Loads the subscriptions (from the shared cache when fresh); `force` reads them again, for the retry button. */
  load(force = false): Observable<void> {
    return new Observable<void>((subscriber) => {
      this.requested = true;
      this.inFlight = true;
      this.status.set('loading');
      const source = force ? this.applications.refresh() : this.applications.load();
      const subscription = source.subscribe({
        next: (list) => {
          this.subscribedCodes.set(list.map((app) => app.code.toUpperCase()));
          this.status.set('ready');
          this.inFlight = false;
          subscriber.next();
          subscriber.complete();
        },
        error: () => {
          this.subscribedCodes.set([]);
          this.status.set('failed');
          this.inFlight = false;
          subscriber.next();
          subscriber.complete();
        },
      });
      return () => {
        this.inFlight = false;
        subscription.unsubscribe();
      };
    });
  }

  /** The application codes the signed-in user's token grants. */
  grantedCodes(): string[] {
    return applicationsFromToken(localStorage.getItem(ACCESS_TOKEN_KEY));
  }
}
