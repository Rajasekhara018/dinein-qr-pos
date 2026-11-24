import { Observable, Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { NMS_EVENTS, NmsEventDef } from './event-attributes.config';
import { NmsTemplate } from './nms-template.model';
import { NmsTemplatesService } from './nms-templates.service';

const ev = (value: string, id?: string, attributes: string[] = ['{{a}}']): NmsEventDef => ({ id, value, label: value.toLowerCase(), attributes });
const tpl = (over: Partial<NmsTemplate> = {}): NmsTemplate => ({ id: 't1', event: 'BOOKING_CONFIRMED', channel: 'EMAIL', name: 'N', body: 'Hello', active: true, ...over });

describe('NmsTemplatesService', () => {
  const api = {
    getAll: vi.fn((_model: string, _params?: unknown): Observable<unknown> => of([])),
    get: vi.fn((_url: string, _params?: unknown): Observable<unknown> => of([])),
    post: vi.fn((_url: string, _body: unknown): Observable<unknown> => of({})),
    put: vi.fn((_url: string, _body: unknown): Observable<unknown> => of({})),
    delete: vi.fn((_url: string): Observable<unknown> => of(undefined)),
  };
  let service: NmsTemplatesService;

  beforeEach(() => {
    vi.clearAllMocks();
    api.get.mockReturnValue(of([]));
    api.getAll.mockReturnValue(of([]));
    service = new NmsTemplatesService(api as never);
  });
  afterEach(() => vi.useRealTimers());

  describe('event definitions', () => {
    it('reads the live catalog, accepting a plain array or a page, and drops incomplete entries', () => {
      api.getAll.mockReturnValue(of([ev('A', '1'), { value: 'B' }, { label: 'x' }, { value: 'C', label: 'C', attributes: undefined }]));
      let out: NmsEventDef[] = [];
      service.getEventDefinitions().subscribe((e) => (out = e));
      expect(api.getAll).toHaveBeenCalledWith('WEB_NMS_EVENT');
      expect(out.map((e) => e.value)).toEqual(['A', 'C']);
      expect(out[1].attributes).toEqual([]);
      api.getAll.mockReturnValue(of({ content: [ev('P')] }));
      service.getEventDefinitions().subscribe((e) => (out = e));
      expect(out.map((e) => e.value)).toEqual(['P']);
    });

    it('falls back to the built-in list when the catalog is empty, null or unreachable', () => {
      let out: NmsEventDef[] = [];
      service.getEventDefinitions().subscribe((e) => (out = e));
      expect(out.map((e) => e.value)).toEqual(NMS_EVENTS.map((e) => e.value));
      expect(out[0].attributes).not.toBe(NMS_EVENTS[0].attributes); // a copy, safe to edit
      api.getAll.mockReturnValue(of(null));
      service.getEventDefinitions().subscribe((e) => (out = e));
      expect(out).toHaveLength(NMS_EVENTS.length);
      api.getAll.mockReturnValue(throwError(() => new Error('down')));
      out = [];
      service.getEventDefinitions().subscribe((e) => (out = e));
      expect(out).toHaveLength(NMS_EVENTS.length);
    });

    it('gives up after four seconds and uses the built-in list', () => {
      vi.useFakeTimers();
      api.getAll.mockReturnValue(new Subject());
      let out: NmsEventDef[] = [];
      service.getEventDefinitions().subscribe((e) => (out = e));
      expect(out).toEqual([]);
      vi.advanceTimersByTime(4001);
      expect(out).toHaveLength(NMS_EVENTS.length);
    });

    it('lists event options as value and label', () => {
      api.getAll.mockReturnValue(of([ev('A', '1')]));
      let out: unknown;
      service.getEvents().subscribe((e) => (out = e));
      expect(out).toEqual([{ value: 'A', label: 'a' }]);
    });

    it('sends only what changed: the edited event is updated, the new one created, the removed one deleted', () => {
      const loaded = [ev('KEEP', 'k'), ev('EDIT', 'e'), ev('GONE', 'g')];
      const edited = [ev('KEEP', 'k'), ev('EDIT', 'e', ['{{changed}}']), ev('NEW')];
      let out: NmsEventDef[] = [];
      service.saveEventDefinitions(edited, loaded).subscribe((e) => (out = e));
      expect(api.put).toHaveBeenCalledTimes(1);
      expect(api.put).toHaveBeenCalledWith('/v1/nms/events/e', { messageID: 'nms-event-update', requestObject: ev('EDIT', 'e', ['{{changed}}']) });
      expect(api.post).toHaveBeenCalledTimes(1);
      expect(api.post).toHaveBeenCalledWith('/v1/nms/events', { messageID: 'nms-event-create', requestObject: { value: 'NEW', label: 'new', attributes: ['{{a}}'] } });
      expect(api.delete).toHaveBeenCalledTimes(1);
      expect(api.delete).toHaveBeenCalledWith('/v1/nms/events/g');
      expect(out.length).toBeGreaterThan(0);
    });

    it('does not rewrite untouched events: 65 loaded events and one edit are one request', () => {
      const loaded = Array.from({ length: 65 }, (_, i) => ev(`EVENT_${i}`, `id${i}`));
      const edited = loaded.map((e, i) => (i === 7 ? { ...e, label: 'Renamed' } : { ...e }));
      service.saveEventDefinitions(edited, loaded).subscribe();
      expect(api.put).toHaveBeenCalledTimes(1);
      expect(api.put.mock.calls[0][0]).toBe('/v1/nms/events/id7');
      expect(api.post).not.toHaveBeenCalled();
      expect(api.delete).not.toHaveBeenCalled();
    });

    it('deletes first, then updates, then creates, one request at a time', () => {
      const order: string[] = [];
      api.delete.mockImplementation((url: string) => { order.push(`delete ${url}`); return of(undefined); });
      api.put.mockImplementation((url: string) => { order.push(`put ${url}`); return of({}); });
      api.post.mockImplementation(() => { order.push('post'); return of({}); });
      service.saveEventDefinitions([ev('A', 'a', ['{{x}}']), ev('NEW')], [ev('A', 'a'), ev('B', 'b')]).subscribe();
      expect(order).toEqual(['delete /v1/nms/events/b', 'put /v1/nms/events/a', 'post']);
    });

    it('stops at the first failed request and reports it', () => {
      api.put.mockReturnValueOnce(throwError(() => ({ status: 409 })));
      let error: unknown;
      service.saveEventDefinitions([ev('A', 'a', ['{{x}}']), ev('NEW')], [ev('A', 'a')]).subscribe({ error: (e) => (error = e) });
      expect(error).toEqual({ status: 409 });
      expect(api.post).not.toHaveBeenCalled();
    });

    it('saves nothing but still reloads when there is nothing to change', () => {
      let out: NmsEventDef[] | undefined;
      service.saveEventDefinitions([], []).subscribe((e) => (out = e));
      expect(api.put).not.toHaveBeenCalled();
      expect(api.post).not.toHaveBeenCalled();
      expect(api.delete).not.toHaveBeenCalled();
      expect(out).toHaveLength(NMS_EVENTS.length);
    });

    it('resets to defaults using the server answer, or the built-in list on an empty answer or failure', () => {
      api.post.mockReturnValueOnce(of([ev('S')]));
      let out: NmsEventDef[] = [];
      service.resetEventDefinitions().subscribe((e) => (out = e));
      expect(api.post).toHaveBeenCalledWith('/v1/nms/events/reset-defaults', {});
      expect(out.map((e) => e.value)).toEqual(['S']);
      api.post.mockReturnValueOnce(of({ content: [] }));
      service.resetEventDefinitions().subscribe((e) => (out = e));
      expect(out).toHaveLength(NMS_EVENTS.length);
      api.post.mockReturnValueOnce(throwError(() => new Error('x')));
      out = [];
      service.resetEventDefinitions().subscribe((e) => (out = e));
      expect(out).toHaveLength(NMS_EVENTS.length);
    });
  });

  describe('attributes', () => {
    it('uses the server list, else the built-in attributes of the event', () => {
      let out: string[] = [];
      api.get.mockReturnValue(of(['{{x}}']));
      service.getAttributes('BOOKING_CONFIRMED').subscribe((a) => (out = a));
      expect(api.get).toHaveBeenCalledWith('/v1/nms/events/BOOKING_CONFIRMED/attributes');
      expect(out).toEqual(['{{x}}']);
      api.get.mockReturnValue(of([]));
      service.getAttributes('BOOKING_CONFIRMED').subscribe((a) => (out = a));
      expect(out).toEqual(NMS_EVENTS[0].attributes);
      api.get.mockReturnValue(throwError(() => new Error('x')));
      service.getAttributes('UNKNOWN').subscribe((a) => (out = a));
      expect(out).toEqual([]);
    });
  });

  describe('templates', () => {
    it('fetches one template for an event and channel, as a list of one, or none on failure', () => {
      let out: NmsTemplate[] = [];
      api.get.mockReturnValue(of(tpl()));
      service.getAll('E', 'SMS').subscribe((t) => (out = t));
      expect(api.get).toHaveBeenCalledWith('/v1/nms/templates/event/E/channel/SMS');
      expect(out).toHaveLength(1);
      api.get.mockReturnValue(of(null));
      service.getAll('E', 'SMS').subscribe((t) => (out = t));
      expect(out).toEqual([]);
      api.get.mockReturnValue(throwError(() => new Error('404')));
      out = [tpl()];
      service.getAll('E', 'SMS').subscribe((t) => (out = t));
      expect(out).toEqual([]);
    });

    it('lists the templates of an event from an array or a page', () => {
      let out: NmsTemplate[] = [];
      api.getAll.mockReturnValue(of([tpl()]));
      service.getAll('E').subscribe((t) => (out = t));
      expect(api.getAll).toHaveBeenCalledWith('WEB_NMS_EVENT_TEMPLATE', { event: 'E' });
      expect(out).toHaveLength(1);
      api.getAll.mockReturnValue(of({ content: [tpl(), tpl({ id: 't2' })] }));
      service.getAll('E').subscribe((t) => (out = t));
      expect(out).toHaveLength(2);
      api.getAll.mockReturnValue(of({}));
      service.getAll('E').subscribe((t) => (out = t));
      expect(out).toEqual([]);
    });

    it('lists everything (capped page) and filters by channel on the client', () => {
      const all = [tpl(), tpl({ id: 't2', channel: 'SMS' })];
      let out: NmsTemplate[] = [];
      api.getAll.mockReturnValue(of(all));
      service.getAll().subscribe((t) => (out = t));
      expect(api.getAll).toHaveBeenCalledWith('WEB_NMS_TEMPLATE', { size: 1000 });
      expect(out).toHaveLength(2);
      service.getAll(undefined, 'SMS').subscribe((t) => (out = t));
      expect(out.map((t) => t.id)).toEqual(['t2']);
      api.getAll.mockReturnValue(of({ content: all }));
      service.getAll().subscribe((t) => (out = t));
      expect(out).toHaveLength(2);
    });

    it('reads one template by id', () => {
      service.getById('t9').subscribe();
      expect(api.get).toHaveBeenCalledWith('/v1/nms/templates/t9');
    });

    it('stores the body Base64-encoded when creating', () => {
      service.create({ event: 'E', channel: 'EMAIL', name: 'N', body: '<p>héllo</p>', active: true }).subscribe();
      const [url, wrapped] = api.post.mock.calls[0] as [string, { messageID: string; requestObject: NmsTemplate }];
      expect(url).toBe('/v1/nms/templates');
      expect(wrapped.messageID).toBe('nms-template-create');
      expect(wrapped.requestObject.body).toBe(service.encode('<p>héllo</p>'));
      expect(service.decode(wrapped.requestObject.body)).toBe('<p>héllo</p>');
    });

    it('encodes the body on update only when one is sent', () => {
      service.update('t1', { name: 'Renamed' }).subscribe();
      expect(api.put).toHaveBeenLastCalledWith('/v1/nms/templates/t1', { messageID: 'nms-template-update-t1', requestObject: { name: 'Renamed' } });
      service.update('t1', { body: 'Hi' }).subscribe();
      expect((api.put.mock.calls[1][1] as { requestObject: { body: string } }).requestObject.body).toBe(btoa('Hi'));
    });

    it('sends a test message and deletes a template', () => {
      const request = { channel: 'SMS' as const, recipient: '+91', body: 'b', variables: {} };
      service.testSend(request).subscribe();
      expect(api.post).toHaveBeenCalledWith('/v1/nms/templates/test-send', { messageID: 'nms-template-test-send', requestObject: request });
      service.remove('t1').subscribe();
      expect(api.delete).toHaveBeenCalledWith('/v1/nms/templates/t1');
    });
  });

  describe('body encoding', () => {
    it('round-trips text with accents and emoji', () => {
      const text = 'Olá — ₹100 😀';
      expect(service.decode(service.encode(text))).toBe(text);
    });

    it('returns the input unchanged when it cannot be converted', () => {
      expect(service.decode('%%% not base64')).toBe('%%% not base64');
      expect(service.encode('\ud800')).toBe('\ud800'); // lone surrogate cannot be URI-encoded
    });
  });
});
