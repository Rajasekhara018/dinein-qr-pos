import { NO_ERRORS_SCHEMA } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { BehaviorSubject, EMPTY } from 'rxjs';
import { vi } from 'vitest';
import { NMS_CHANNELS } from '../event-attributes.config';
import { TemplateLanding } from './template-landing';

describe('TemplateLanding', () => {
  const router = { navigate: vi.fn() };
  let query$: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let granted: Set<string> | null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };

  function create() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [TemplateLanding],
      imports: [TranslateModule.forRoot(), HqSharedLibModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: { queryParamMap: query$ } },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
    const fixture = TestBed.createComponent(TemplateLanding);
    fixture.detectChanges();
    return fixture;
  }

  const tabs = (el: HTMLElement) => Array.from(el.querySelectorAll('[role="tab"]')) as HTMLButtonElement[];

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    query$ = new BehaviorSubject(convertToParamMap({}));
  });

  it('shows one tab per channel, the first open by default, with the template list under it on the same screen', () => {
    const fixture = create();
    const el = fixture.nativeElement as HTMLElement;

    expect(tabs(el)).toHaveLength(NMS_CHANNELS.length);
    expect(tabs(el)[0].getAttribute('aria-selected')).toBe('true');
    expect(tabs(el)[1].getAttribute('aria-selected')).toBe('false');
    expect(el.querySelectorAll('.channel-card')).toHaveLength(0);
    expect(el.querySelector('[role="tabpanel"] app-template-list')).not.toBeNull();
  });

  it('opens the tab named in the address, and falls back to the first for an unknown channel', () => {
    query$.next(convertToParamMap({ channel: 'WHATSAPP' }));
    expect(create().componentInstance.selected).toBe('WHATSAPP');
    query$.next(convertToParamMap({ channel: 'FAX' }));
    expect(create().componentInstance.selected).toBe(NMS_CHANNELS[0].value);
  });

  it('opens the clicked tab at once and records it in the address without a new history entry', () => {
    const fixture = create();
    tabs(fixture.nativeElement)[1].click();
    fixture.detectChanges();

    expect(fixture.componentInstance.selected).toBe(NMS_CHANNELS[1].value);
    expect(tabs(fixture.nativeElement)[1].getAttribute('aria-selected')).toBe('true');
    expect(router.navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { channel: NMS_CHANNELS[1].value }, replaceUrl: true }));
  });

  it('moves between tabs with the arrow keys, wrapping at the ends, and with Home and End', () => {
    const fixture = create();
    const key = (name: string, index: number) => fixture.componentInstance.onTabKey(new KeyboardEvent('keydown', { key: name }), index);
    const last = NMS_CHANNELS.length - 1;

    key('ArrowRight', 0);
    expect(fixture.componentInstance.selected).toBe(NMS_CHANNELS[1].value);
    key('ArrowLeft', 0);
    expect(fixture.componentInstance.selected).toBe(NMS_CHANNELS[last].value);
    key('ArrowRight', last);
    expect(fixture.componentInstance.selected).toBe(NMS_CHANNELS[0].value);
    key('End', 0);
    expect(fixture.componentInstance.selected).toBe(NMS_CHANNELS[last].value);
    key('Home', 2);
    expect(fixture.componentInstance.selected).toBe(NMS_CHANNELS[0].value);

    router.navigate.mockClear();
    key('Tab', 0);
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('opens the event catalog', () => {
    create().componentInstance.openEvents();
    expect(router.navigate).toHaveBeenCalledWith(['/nms-templates/events']);
  });

  it('shows Manage events to holders of the event privilege and hides it otherwise', () => {
    const withIt = create().nativeElement as HTMLElement;
    expect(withIt.querySelector('.page-header button')).not.toBeNull();
    granted = new Set(['NMS_EVENT_LIST']);
    expect((create().nativeElement as HTMLElement).querySelector('.page-header button')).not.toBeNull();
    granted = new Set(['NMS_TEMPLATE_LIST']);
    expect((create().nativeElement as HTMLElement).querySelector('.page-header button')).toBeNull();
  });
});
