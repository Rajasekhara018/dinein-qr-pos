import { debounceTime, filter, map, merge, Observable } from 'rxjs';
import { RealtimeEvent, TOPICS } from '../../../core/api/models';
import { RealtimeService } from '../../../core/realtime/realtime.service';

/**
 * Emits when admin data showing orders should be refetched: on `/topic/kitchen/orders` events (optionally
 * filtered) and on every (re)connection — WebSocket is only a notification channel, REST is the source of truth.
 * Debounced so a burst of events causes one refetch. Subscribing opens the connection.
 */
export function orderRefreshSignals(
  realtime: RealtimeService,
  accept: (event: RealtimeEvent) => boolean = () => true,
  debounceMs = 400,
): Observable<void> {
  return merge(
    realtime.watch<RealtimeEvent>(TOPICS.kitchenOrders).pipe(filter(accept)),
    realtime.connected$,
  ).pipe(
    debounceTime(debounceMs),
    map(() => undefined),
  );
}
