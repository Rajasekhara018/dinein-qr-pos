import { Injectable, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import type { OperatingModel } from '../access-management/organization-operating-model/organization-operating-model.types';
import type { HomeLoadStatus } from '../home/home.models';
import { UamService } from '../shared/services/uam-service';
import { MY_OPERATING_MODEL_PATH } from './nav-operating-model.constants';

/** GET /identity/me/operating-model. */
export interface MyOperatingModel {
  organizationId: string;
  /** Today in the organization's time zone, YYYY-MM-DD. */
  date: string;
  model: OperatingModel;
}

/**
 * The operating model (DIRECT, FRANCHISE or HYBRID) of the session's organization, for the sidebar and header to show only
 * the menus that fit it. Read from UAM's self-service lookup, so any signed-in user may ask. A failed lookup leaves the
 * status 'failed' (franchise-only items stay hidden) and is retried quietly on the next navigation. The organization
 * switch reloads the page, which resets this state.
 */
@Injectable({ providedIn: 'root' })
export class SessionOperatingModelService {
  private readonly uam = inject(UamService);

  readonly status = signal<HomeLoadStatus>('loading');
  /** Today's model, null until loaded. */
  readonly model = signal<OperatingModel | null>(null);

  private requested = false;
  private inFlight = false;

  /** Starts the lookup once, and again only after a failure; does nothing while one is outstanding or once it is known. */
  ensureLoaded(): void {
    if (this.inFlight || (this.requested && this.status() !== 'failed')) return;
    this.load().subscribe();
  }

  /** Loads the model; always completes, with the outcome in the signals. */
  load(): Observable<void> {
    return new Observable<void>((subscriber) => {
      this.requested = true;
      this.inFlight = true;
      this.status.set('loading');
      const finish = (model: OperatingModel | null, status: HomeLoadStatus) => {
        this.model.set(model);
        this.status.set(status);
        this.inFlight = false;
        subscriber.next();
        subscriber.complete();
      };
      const subscription = this.uam.get<MyOperatingModel>(MY_OPERATING_MODEL_PATH).subscribe({
        next: (answer) => (answer?.model ? finish(answer.model, 'ready') : finish(null, 'failed')),
        error: () => finish(null, 'failed'),
      });
      return () => {
        this.inFlight = false;
        subscription.unsubscribe();
      };
    });
  }
}
