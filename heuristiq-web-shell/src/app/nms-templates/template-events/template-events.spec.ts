import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { pendingChangesGuard } from '../../shared/guards/pending-changes.guard';
import { NmsEventDef } from '../event-attributes.config';
import { NmsTemplatesService } from '../nms-templates.service';
import { TemplateEvents } from './template-events';

const ev = (label: string, value: string, attributes: string[] = []): NmsEventDef => ({ label, value, attributes });

describe('TemplateEvents', () => {
  const router = { navigate: vi.fn() };
  const svc = {
    getEventDefinitions: vi.fn((): Observable<NmsEventDef[]> => of([ev('Booking Confirmed', 'BOOKING_CONFIRMED', ['{{id}}']), ev('Ride Started', 'RIDE_STARTED')])),
    saveEventDefinitions: vi.fn((e: NmsEventDef[], _baseline: NmsEventDef[]): Observable<NmsEventDef[]> => of(e)),
    resetEventDefinitions: vi.fn((): Observable<NmsEventDef[]> => of([ev('Default', 'DEFAULT')])),
  };
  const snack = { open: vi.fn() };
  const dialogs = { confirm: vi.fn((_: unknown): Observable<boolean> => of(true)) };
  let granted: Set<string> | null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };

  function create(): ComponentFixture<TemplateEvents> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [TemplateEvents],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: Router, useValue: router },
        { provide: NmsTemplatesService, useValue: svc },
        { provide: MatSnackBar, useValue: snack },
        { provide: AppDialogService, useValue: dialogs },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
    const fixture = TestBed.createComponent(TemplateEvents);
    fixture.detectChanges();
    return fixture;
  }
  const make = () => create().componentInstance;

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    dialogs.confirm.mockImplementation(() => of(true));
    svc.getEventDefinitions.mockReturnValue(of([ev('Booking Confirmed', 'BOOKING_CONFIRMED', ['{{id}}']), ev('Ride Started', 'RIDE_STARTED')]));
  });

  describe('loading', () => {
    it('loads events, selects the first and starts clean', () => {
      const c = make();
      expect(c.events).toHaveLength(2);
      expect(c.selected?.value).toBe('BOOKING_CONFIRMED');
      expect(c.hasChanges).toBe(false);
      expect(c.hasPendingChanges()).toBe(false);
    });

    it('shows an empty state and no selection when there are no events', () => {
      svc.getEventDefinitions.mockReturnValue(of([]));
      const c = make();
      expect(c.selected).toBeNull();
      expect(c.selectedIndex).toBe(-1);
    });

    it('reports a failed load', () => {
      svc.getEventDefinitions.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      expect(c.events).toEqual([]);
      expect(snack.open).toHaveBeenCalledWith('nmsTemplates.events.messages.loadFailed', 'nmsTemplates.list.close', { duration: 3000 });
    });
  });

  describe('editing', () => {
    it('selecting clears the attribute draft', () => {
      const c = make();
      c.attrDraft = 'x';
      c.selectEvent(1);
      expect(c.selected?.value).toBe('RIDE_STARTED');
      expect(c.attrDraft).toBe('');
    });

    it('adds a custom event with a unique value and selects it', () => {
      const c = make();
      c.events[0].value = 'CUSTOM_EVENT_3';
      c.addEvent();
      expect(c.events).toHaveLength(3);
      expect(c.selected?.value).toBe('CUSTOM_EVENT_4');
      expect(c.selected?.label).toBe('nmsTemplates.events.newEventLabel');
      expect(c.hasChanges).toBe(true);
    });

    it('derives the value from the label, and cleans a typed value', () => {
      const c = make();
      c.updateLabel('Payment received now!');
      expect(c.selected?.label).toBe('Payment received now!');
      expect(c.selected?.value).toBe('PAYMENT_RECEIVED_NOW');
      c.updateValue('  rideDone-2 ');
      expect(c.selected?.value).toBe('RIDE_DONE_2');
      c.updateValue('camelCaseValue');
      expect(c.selected?.value).toBe('CAMEL_CASE_VALUE');
    });

    it('ignores edits when nothing is selected', () => {
      svc.getEventDefinitions.mockReturnValue(of([]));
      const c = make();
      c.updateLabel('x');
      c.updateValue('x');
      c.attrDraft = 'a';
      c.addAttribute();
      c.removeAttribute('a');
      c.deleteSelected();
      expect(dialogs.confirm).not.toHaveBeenCalled();
      expect(c.events).toEqual([]);
    });

    it('adds attributes in {{braces}} form without duplicates, ignoring blanks', () => {
      const c = make();
      c.attrDraft = ' name ';
      c.addAttribute();
      c.attrDraft = '{{ name }}';
      c.addAttribute();
      c.attrDraft = '   ';
      c.addAttribute();
      c.attrDraft = '{{}}';
      c.addAttribute();
      expect(c.selected?.attributes).toEqual(['{{id}}', '{{name}}']);
      expect(c.attrDraft).toBe('{{}}'); // an unusable draft is kept for the user to correct
      c.removeAttribute('{{id}}');
      expect(c.selected?.attributes).toEqual(['{{name}}']);
    });

    it('deletes the selected event only after confirmation and selects its neighbour', () => {
      const c = make();
      c.selectEvent(1);
      dialogs.confirm.mockImplementation(() => of(false));
      c.deleteSelected();
      expect(c.events).toHaveLength(2);
      dialogs.confirm.mockImplementation(() => of(true));
      c.deleteSelected();
      expect(dialogs.confirm).toHaveBeenLastCalledWith(expect.objectContaining({ variant: 'danger' }));
      expect(c.events.map((e) => e.value)).toEqual(['BOOKING_CONFIRMED']);
      expect(c.selectedIndex).toBe(0);
      c.deleteSelected();
      expect(c.selectedIndex).toBe(-1);
    });
  });

  describe('saving', () => {
    it('does nothing when unchanged', () => {
      const c = make();
      c.save();
      expect(svc.saveEventDefinitions).not.toHaveBeenCalled();
    });

    it('saves normalised events and becomes clean again', () => {
      const c = make();
      c.selected!.label = '  Booked  ';
      c.selected!.attributes = ['plain'];
      expect(c.hasChanges).toBe(true);
      c.save();
      const sent = svc.saveEventDefinitions.mock.calls[0][0];
      expect(sent[0]).toMatchObject({ label: 'Booked', value: 'BOOKING_CONFIRMED', attributes: ['{{plain}}'] });
      expect(snack.open).toHaveBeenCalledWith('nmsTemplates.events.messages.saved', 'nmsTemplates.list.close', { duration: 2500 });
      expect(c.hasChanges).toBe(false);
      expect(c.saving).toBe(false);
    });

    it('refuses a blank label or value', () => {
      const c = make();
      c.selected!.label = '   ';
      c.selected!.value = '';
      c.save();
      expect(svc.saveEventDefinitions).not.toHaveBeenCalled();
      expect(snack.open).toHaveBeenCalledWith('nmsTemplates.events.messages.labelValueRequired', 'nmsTemplates.list.close', { duration: 3000 });
    });

    it('refuses two events with the same value', () => {
      const c = make();
      c.selectEvent(1);
      c.updateValue('BOOKING_CONFIRMED');
      c.save();
      expect(svc.saveEventDefinitions).not.toHaveBeenCalled();
      expect(snack.open.mock.calls.at(-1)?.[0]).toBe('nmsTemplates.events.messages.duplicateValue');
    });

    it('reports a failed save and stays dirty', () => {
      const c = make();
      c.selected!.label = 'Changed';
      svc.saveEventDefinitions.mockReturnValueOnce(throwError(() => new Error('x')));
      c.save();
      expect(snack.open).toHaveBeenCalledWith('nmsTemplates.events.messages.saveFailed', 'nmsTemplates.list.close', { duration: 4000 });
      expect(c.saving).toBe(false);
      expect(c.hasChanges).toBe(true);
    });
  });

  describe('keeping the right event selected and saving only changes', () => {
    const saved = (label: string, value: string, id: string): NmsEventDef => ({ id, label, value, attributes: [] });
    const loaded = () => [saved('Account created', 'ACCOUNT_CREATED', 'a'), saved('Password reset', 'PASSWORD_RESET', 'p'), saved('Ride started', 'RIDE_STARTED', 'r')];

    beforeEach(() => svc.getEventDefinitions.mockReturnValue(of(loaded())));

    it('keeps editing the same event after a save re-sorts the list', () => {
      const c = make();
      c.selectEvent(1);
      c.updateLabel('Zebra reset');
      // The server answers sorted by label, so the edited event moves to the end of the list.
      svc.saveEventDefinitions.mockReturnValueOnce(of([saved('Account created', 'ACCOUNT_CREATED', 'a'), saved('Ride started', 'RIDE_STARTED', 'r'), saved('Zebra reset', 'PASSWORD_RESET', 'p')]));
      c.save();
      expect(c.selected?.id).toBe('p');
      expect(c.selected?.label).toBe('Zebra reset');
      c.updateLabel('Zebra reset again');
      expect(c.events.find((e) => e.id === 'a')?.label).toBe('Account created');
      expect(c.events.find((e) => e.id === 'r')?.label).toBe('Ride started');
      expect(c.events.find((e) => e.id === 'p')?.label).toBe('Zebra reset again');
    });

    it('finds a new event again by its value once the server has given it an id', () => {
      const c = make();
      c.addEvent();
      c.updateLabel('Brand new');
      svc.saveEventDefinitions.mockReturnValueOnce(of([saved('Account created', 'ACCOUNT_CREATED', 'a'), saved('Brand new', 'BRAND_NEW', 'n'), saved('Password reset', 'PASSWORD_RESET', 'p')]));
      c.save();
      expect(c.selected?.id).toBe('n');
    });

    it('keeps the value of a saved event when it is renamed, but derives it for a new one', () => {
      const c = make();
      c.updateLabel('Account was created');
      expect(c.selected?.value).toBe('ACCOUNT_CREATED');
      c.updateValue('account_made');
      expect(c.selected?.value).toBe('ACCOUNT_MADE');
      c.addEvent();
      c.updateLabel('Fresh one');
      expect(c.selected?.value).toBe('FRESH_ONE');
    });

    it('hands the service the list as loaded, so only the changed event is sent', () => {
      const c = make();
      c.selectEvent(2);
      c.updateLabel('Ride has started');
      c.save();
      const [sent, baseline] = svc.saveEventDefinitions.mock.calls[0];
      expect(sent).toHaveLength(3);
      expect(baseline).toEqual([
        { id: 'a', label: 'Account created', value: 'ACCOUNT_CREATED', attributes: [] },
        { id: 'p', label: 'Password reset', value: 'PASSWORD_RESET', attributes: [] },
        { id: 'r', label: 'Ride started', value: 'RIDE_STARTED', attributes: [] },
      ]);
    });

    it('flags two events with the same value and will not save until they differ', () => {
      const c = make();
      c.selectEvent(1);
      c.updateValue('ACCOUNT_CREATED');
      expect(c.hasDuplicate).toBe(true);
      expect(c.isDuplicate(c.events[0])).toBe(true);
      expect(c.isDuplicate(c.events[1])).toBe(true);
      expect(c.isDuplicate(c.events[2])).toBe(false);
      c.updateValue('PASSWORD_RESET');
      expect(c.hasDuplicate).toBe(false);
    });

    it('says a value is already taken when the server answers 409, and stays dirty', () => {
      const c = make();
      c.updateLabel('Account created again');
      svc.saveEventDefinitions.mockReturnValueOnce(throwError(() => ({ status: 409 })));
      c.save();
      expect(snack.open).toHaveBeenLastCalledWith('nmsTemplates.events.messages.conflict', 'nmsTemplates.list.close', { duration: 4000 });
      expect(c.hasChanges).toBe(true);
      expect(c.saving).toBe(false);
    });

    it('takes the loaded list as the new baseline after a save, so a second save sends only the next edit', () => {
      const c = make();
      c.updateLabel('Account made');
      c.save();
      c.selectEvent(2);
      c.updateLabel('Ride went');
      c.save();
      const secondBaseline = svc.saveEventDefinitions.mock.calls[1][1];
      expect(secondBaseline.find((e) => e.id === 'a')?.label).toBe('Account made');
    });
  });

  describe('restore defaults', () => {
    it('warns, in a danger dialog, that every event on the server is replaced', () => {
      const c = make();
      c.restoreDefaults();
      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({
        variant: 'danger',
        title: 'nmsTemplates.events.restoreDialog.title',
        message: 'nmsTemplates.events.restoreDialog.message',
        confirmText: 'nmsTemplates.events.restoreDialog.confirm',
      }));
    });

    it('restores the defaults after confirmation', () => {
      const c = make();
      c.restoreDefaults();
      expect(c.events.map((e) => e.value)).toEqual(['DEFAULT']);
      expect(c.hasChanges).toBe(false);
      expect(snack.open).toHaveBeenCalledWith('nmsTemplates.events.messages.defaultsRestored', 'nmsTemplates.list.close', { duration: 2500 });
    });

    it('does nothing when declined, and reports a failure', () => {
      const c = make();
      dialogs.confirm.mockImplementation(() => of(false));
      c.restoreDefaults();
      expect(svc.resetEventDefinitions).not.toHaveBeenCalled();
      dialogs.confirm.mockImplementation(() => of(true));
      svc.resetEventDefinitions.mockReturnValueOnce(throwError(() => new Error('x')));
      c.restoreDefaults();
      expect(c.loading).toBe(false);
      expect(snack.open).toHaveBeenLastCalledWith('nmsTemplates.events.messages.restoreFailed', 'nmsTemplates.list.close', { duration: 3000 });
    });

    it('selects nothing when the defaults are empty', () => {
      const c = make();
      svc.resetEventDefinitions.mockReturnValueOnce(of([]));
      c.restoreDefaults();
      expect(c.selectedIndex).toBe(-1);
    });
  });

  describe('leaving', () => {
    it('goes straight back when clean', () => {
      make().back();
      expect(router.navigate).toHaveBeenCalledWith(['/nms-templates']);
    });

    it('leaves the question to the route guard, so it is asked once and not twice', () => {
      const c = make();
      c.selected!.label = 'Changed';
      c.back();
      expect(dialogs.confirm).not.toHaveBeenCalled();
      expect(router.navigate).toHaveBeenCalledWith(['/nms-templates']);
    });

    it('is stopped by the pending-changes guard until the user agrees to discard', () => {
      const c = make();
      const leave = () => TestBed.runInInjectionContext(
        () => pendingChangesGuard(c, {} as never, {} as never, {} as never) as Observable<boolean> | boolean,
      );
      expect(leave()).toBe(true);
      c.selected!.label = 'Changed';
      let answer: boolean | undefined;
      dialogs.confirm.mockImplementation(() => of(false));
      (leave() as Observable<boolean>).subscribe((v) => (answer = v));
      expect(answer).toBe(false);
      expect(dialogs.confirm).toHaveBeenLastCalledWith(expect.objectContaining({ variant: 'warning' }));
      dialogs.confirm.mockImplementation(() => of(true));
      (leave() as Observable<boolean>).subscribe((v) => (answer = v));
      expect(answer).toBe(true);
    });

    it('blocks the browser unload only when there are unsaved changes', () => {
      const c = make();
      const clean = { preventDefault: vi.fn(), returnValue: 'x' } as unknown as BeforeUnloadEvent;
      c.beforeUnload(clean);
      expect(clean.preventDefault).not.toHaveBeenCalled();
      c.selected!.label = 'Changed';
      const dirty = { preventDefault: vi.fn(), returnValue: 'x' } as unknown as BeforeUnloadEvent;
      c.beforeUnload(dirty);
      expect(dirty.preventDefault).toHaveBeenCalled();
      expect(dirty.returnValue).toBe('');
    });
  });

  describe('privileges', () => {
    const labels = (el: HTMLElement) => Array.from(el.querySelectorAll('button')).map((b) => b.textContent?.trim());

    it('a full manager sees reset, save, add and delete', () => {
      const el: HTMLElement = create().nativeElement;
      const t = labels(el);
      expect(t.some((x) => x?.includes('nmsTemplates.events.restoreDefaults'))).toBe(true);
      expect(t.some((x) => x?.includes('shared.adminCrud.save'))).toBe(true);
      expect(t.some((x) => x?.includes('nmsTemplates.events.addEvent'))).toBe(true);
      expect(t.some((x) => x?.includes('shared.listPage.rowActions.delete'))).toBe(true);
    });

    it('a viewer sees the events but no change actions', () => {
      granted = new Set(['NMS_EVENT_LIST']);
      const el: HTMLElement = create().nativeElement;
      const t = labels(el);
      expect(el.querySelectorAll('.te-event-row')).toHaveLength(2);
      expect(t.some((x) => x?.includes('nmsTemplates.events.restoreDefaults'))).toBe(false);
      expect(t.some((x) => x?.includes('shared.adminCrud.save'))).toBe(false);
      expect(t.some((x) => x?.includes('nmsTemplates.events.addEvent'))).toBe(false);
      expect(t.some((x) => x?.includes('shared.listPage.rowActions.delete'))).toBe(false);
    });

    it('delete needs its own privilege, add and save theirs', () => {
      granted = new Set(['NMS_EVENT_DELETE']);
      let t = labels(create().nativeElement);
      expect(t.some((x) => x?.includes('shared.listPage.rowActions.delete'))).toBe(true);
      expect(t.some((x) => x?.includes('nmsTemplates.events.addEvent'))).toBe(false);
      granted = new Set(['NMS_EVENT_CREATE']);
      t = labels(create().nativeElement);
      expect(t.some((x) => x?.includes('nmsTemplates.events.addEvent'))).toBe(true);
      expect(t.some((x) => x?.includes('shared.listPage.rowActions.delete'))).toBe(false);
      granted = new Set(['NMS_EVENT_RESET_DEFAULTS']);
      t = labels(create().nativeElement);
      expect(t.some((x) => x?.includes('nmsTemplates.events.restoreDefaults'))).toBe(true);
    });

    it('offers Add event from the empty state only with the create privilege', () => {
      svc.getEventDefinitions.mockReturnValue(of([]));
      let el: HTMLElement = create().nativeElement;
      expect(el.querySelector('.te-empty-state button')).not.toBeNull();
      granted = new Set(['NMS_EVENT_LIST']);
      el = create().nativeElement;
      expect(el.querySelector('.te-empty-state button')).toBeNull();
    });

    it('clicking a row selects it and shows its details', () => {
      const fixture = create();
      const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('.te-event-row');
      (rows[1] as HTMLElement).click();
      expect(fixture.componentInstance.selectedIndex).toBe(1);
    });
  });
});
