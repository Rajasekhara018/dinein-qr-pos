import { Injectable } from '@angular/core';
import { Observable, concat, defer, of } from 'rxjs';
import { catchError, map, switchMap, timeout, toArray } from 'rxjs/operators';
import { ApiService } from '../shared/services/api-service';
import { NmsChannel, NMS_EVENTS, NmsEventDef, getEventAttributes } from './event-attributes.config';
import { diffEvents } from './event-definitions.helpers';
import { NmsTemplate, NmsTemplateTestSendRequest, NmsTemplateTestSendResult } from './nms-template.model';

export interface NmsEventOption {
  value: string;
  label: string;
}

interface CommonRequestObject<T> {
  messageID: string;
  requestObject: T;
}

interface PageResponse<T> {
  content: T[];
}

/**
 * Talks to Rider_NMS via the API Gateway's nms-api-versioned route (/api/nms/** -> version-aware
 * rewrite to NMS's backend /api/v1/nms/** -- see Rider_ApiGateway's RouteConfig's nested
 * RequestVersioning). Calls here use the /api/v{N}/nms/... ordering (version right after
 * environment.apiUrl, before "nms") -- RouteConfig's route accepts this same request in either
 * position, this is just this app's chosen style, consistent with environment.ts's other bases.
 */
@Injectable({ providedIn: 'root' })
export class NmsTemplatesService {
  private base = '/v1/nms/templates';
  private eventsBase = '/v1/nms/events';

  constructor(private api: ApiService) {}

  private defaultEventDefinitions(): NmsEventDef[] {
    return NMS_EVENTS.map(e => ({ ...e, attributes: [...e.attributes] }));
  }

  private normalizeEventDefinitions(events: Partial<NmsEventDef>[]): NmsEventDef[] {
    return events
      .filter(e => !!e.value && !!e.label)
      .map(e => ({
        id: e.id,
        value: String(e.value),
        label: String(e.label),
        attributes: Array.isArray(e.attributes) ? e.attributes : [],
      }));
  }

  private fetchEventDefinitions(): Observable<NmsEventDef[]> {
    return this.api.getAll<NmsEventDef[] | PageResponse<NmsEventDef>>("WEB_NMS_EVENT").pipe(
      map(result => this.normalizeEventDefinitions(Array.isArray(result) ? result : result?.content ?? []))
    );
  }

  /** Fetches the live event catalog from NMS; falls back to the static default list if unreachable. */
  getEventDefinitions(): Observable<NmsEventDef[]> {
    return this.fetchEventDefinitions().pipe(
      timeout(4000),
      map(events => events.length ? events : this.defaultEventDefinitions()),
      catchError(() => of(this.defaultEventDefinitions()))
    );
  }

  /**
   * Brings NMS in line with the edited event list, sending only what changed against {@code baseline} (the list as it was
   * loaded): removed events are deleted, changed saved events are updated, new ones are created. One request at a time and
   * in that order, so a value freed by a delete can be taken by an update in the same save and a failure stops the rest.
   * Nothing changed means no request at all. The server's list is read back afterwards.
   */
  saveEventDefinitions(events: NmsEventDef[], baseline: NmsEventDef[]): Observable<NmsEventDef[]> {
    const { created, updated, removed } = diffEvents(events, baseline);
    const calls: Observable<unknown>[] = [
      ...removed.map(e => defer(() => this.api.delete<void>(`${this.eventsBase}/${e.id}`))),
      ...updated.map(e => defer(() => this.api.put<NmsEventDef>(`${this.eventsBase}/${e.id}`, this.wrap('nms-event-update', e)))),
      ...created.map(e => defer(() => this.api.post<NmsEventDef>(this.eventsBase, this.wrap('nms-event-create', e)))),
    ];
    return concat(...calls).pipe(
      toArray(),
      switchMap(() => this.getEventDefinitions())
    );
  }

  resetEventDefinitions(): Observable<NmsEventDef[]> {
    return this.api.post<NmsEventDef[] | PageResponse<NmsEventDef>>(`${this.eventsBase}/reset-defaults`, {}).pipe(
      map(result => {
        const normalized = this.normalizeEventDefinitions(Array.isArray(result) ? result : result?.content ?? []);
        return normalized.length ? normalized : this.defaultEventDefinitions();
      }),
      catchError(() => of(this.defaultEventDefinitions()))
    );
  }

  /** Fetches all events from the NMS backend; falls back to static config. */
  getEvents(): Observable<NmsEventOption[]> {
    return this.getEventDefinitions().pipe(
      map(events => events.map(e => ({ value: e.value, label: e.label })))
    );
  }

  /** Fetches attributes for a given event from the NMS backend; falls back to static config. */
  getAttributes(event: string): Observable<string[]> {
    return this.api.get<string[]>(`${this.eventsBase}/${event}/attributes`).pipe(
      timeout(4000),
      map(attrs => attrs?.length ? attrs : getEventAttributes(event)),
      catchError(() => of(getEventAttributes(event)))
    );
  }

  getAll(event?: string, channel?: NmsChannel): Observable<NmsTemplate[]> {
    if (event && channel) {
      return this.api.get<NmsTemplate>(`${this.base}/event/${event}/channel/${channel}`).pipe(
        map(t => t ? [t] : []),
        catchError(() => of([]))
      );
    }
    if (event) {
      return this.api.getAll<NmsTemplate[] | PageResponse<NmsTemplate>>("WEB_NMS_EVENT_TEMPLATE", { "event": event }).pipe(
        map(result => Array.isArray(result) ? result : result?.content ?? [])
      );
    }
    return this.api.getAll<NmsTemplate[] | PageResponse<NmsTemplate>>("WEB_NMS_TEMPLATE", { size: 1000 }).pipe(
      map(result => {
        const templates = Array.isArray(result) ? result : result?.content ?? [];
        return channel ? templates.filter(t => t.channel === channel) : templates;
      })
    );
  }

  getById(id: string): Observable<NmsTemplate> {
    return this.api.get<NmsTemplate>(`${this.base}/${id}`);
  }

  create(template: Omit<NmsTemplate, 'id' | 'createdAt' | 'updatedAt'>): Observable<NmsTemplate> {
    const payload = { ...template, body: this.encode(template.body) };
    return this.api.post<NmsTemplate>(this.base, this.wrap('nms-template-create', payload));
  }

  update(id: string, template: Partial<NmsTemplate>): Observable<NmsTemplate> {
    const payload = template.body
      ? { ...template, body: this.encode(template.body) }
      : template;
    return this.api.put<NmsTemplate>(`${this.base}/${id}`, this.wrap(`nms-template-update-${id}`, payload));
  }

  /** Sends one [TEST] message with the unsaved editor content; only EMAIL, SMS and WHATSAPP are accepted. */
  testSend(request: NmsTemplateTestSendRequest): Observable<NmsTemplateTestSendResult> {
    return this.api.post<NmsTemplateTestSendResult>(`${this.base}/test-send`, this.wrap('nms-template-test-send', request));
  }

  remove(id: string): Observable<void> {
    return this.api.delete<void>(`${this.base}/${id}`);
  }

  /** Base64-encode HTML body for storage. */
  encode(html: string): string {
    try { return btoa(unescape(encodeURIComponent(html))); }
    catch { return html; }
  }

  /** Decode Base64 body from API for editing. */
  decode(body: string): string {
    try { return new TextDecoder().decode(Uint8Array.from(atob(body), c => c.charCodeAt(0))); }
    catch { return body; }
  }

  private wrap<T>(messageID: string, requestObject: T): CommonRequestObject<T> {
    return { messageID, requestObject };
  }
}
