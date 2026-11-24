import { ChangeDetectorRef, SimpleChange } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { PICKER_MAX_RESULTS, PICKER_SEARCH_DEBOUNCE_MS } from '../org-structure.constants';
import { PickerItem } from '../org-structure.models';
import { OrgPicker } from './org-picker';

const DEBOUNCE = PICKER_SEARCH_DEBOUNCE_MS + 10;

function setup(kind: OrgPicker['kind'], draftItems: PickerItem[] = []) {
  const api = {
    searchUnits: vi.fn((): Observable<unknown[]> => of([{ id: 'u1', name: 'Ops', code: 'OPS', ladderId: 'l1' }])),
    searchEmployees: vi.fn((): Observable<unknown[]> => of([{ id: 'e1', fullName: 'Asha', employeeCode: 'E1' }])),
    ladders: vi.fn((): Observable<unknown[]> => of([{ id: 'l1', name: 'Engineering' }, { id: 'l2', name: 'Sales' }])),
    positions: vi.fn((): Observable<unknown[]> => of([{ id: 'p1', name: 'Lead', ladderId: 'l1' }, { id: 'p2', name: 'Intern', ladderId: 'l1' }])),
  };
  const cdr = { detectChanges: vi.fn() };
  const picker = new OrgPicker(api as never, cdr as unknown as ChangeDetectorRef);
  picker.kind = kind as never;
  picker.draftItems = draftItems;
  const picked: (PickerItem | null)[] = [];
  picker.picked.subscribe((p: PickerItem | null) => picked.push(p));
  return { picker, api, cdr, picked };
}

describe('OrgPicker', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('searches units after the debounce and labels them with their code', () => {
    const { picker, api } = setup('unit');
    picker.onFocus();
    expect(picker.open).toBe(true);
    expect(picker.isLoading).toBe(true);
    vi.advanceTimersByTime(DEBOUNCE);
    expect(api.searchUnits).toHaveBeenCalledWith('', PICKER_MAX_RESULTS);
    expect(picker.items).toEqual([{ id: 'u1', label: 'Ops (OPS)', ladderId: 'l1' }]);
    expect(picker.isLoading).toBe(false);
  });

  it('searches employees by typed text and tells the parent the earlier choice is void', () => {
    const { picker, api, picked } = setup('employee');
    picker.onInput('as');
    expect(picked).toEqual([null]);
    vi.advanceTimersByTime(DEBOUNCE);
    expect(api.searchEmployees).toHaveBeenCalledWith('as', PICKER_MAX_RESULTS);
    expect(picker.items).toEqual([{ id: 'e1', label: 'Asha (E1)' }]);
  });

  it('filters ladders by name on the client', () => {
    const { picker } = setup('ladder');
    picker.onInput('sal');
    vi.advanceTimersByTime(DEBOUNCE);
    expect(picker.items.map((i) => i.label)).toEqual(['Sales']);
  });

  it('offers positions only once a ladder is chosen', () => {
    const { picker, api } = setup('position');
    picker.onInput('');
    vi.advanceTimersByTime(DEBOUNCE);
    expect(api.positions).not.toHaveBeenCalled();
    expect(picker.items).toEqual([]);
    picker.ladderId = 'l1';
    picker.onInput('lea');
    vi.advanceTimersByTime(DEBOUNCE);
    expect(api.positions).toHaveBeenCalledWith('l1');
    expect(picker.items).toEqual([{ id: 'p1', label: 'Lead', ladderId: 'l1' }]);
  });

  it('shows matching draft records immediately and merges them with the live ones', () => {
    const { picker } = setup('unit', [{ id: 'd1', label: 'Ops draft' }, { id: 'd2', label: 'Other' }]);
    picker.onInput('ops');
    expect(picker.items.map((i) => i.id)).toEqual(['d1']);
    vi.advanceTimersByTime(DEBOUNCE);
    expect(picker.items.map((i) => i.id).sort()).toEqual(['d1', 'u1']);
  });

  it('treats a failed search as no live results', () => {
    const { picker, api } = setup('unit');
    api.searchUnits.mockReturnValue(throwError(() => new Error('x')));
    picker.onInput('a');
    vi.advanceTimersByTime(DEBOUNCE);
    expect(picker.items).toEqual([]);
    expect(picker.isLoading).toBe(false);
  });

  it('does not repeat an identical search', () => {
    const { picker, api } = setup('unit');
    picker.onInput('a');
    vi.advanceTimersByTime(DEBOUNCE);
    picker.onInput('a');
    vi.advanceTimersByTime(DEBOUNCE);
    expect(api.searchUnits).toHaveBeenCalledTimes(1);
  });

  it('choosing an item fills the box, closes the list and emits it', () => {
    const { picker, picked } = setup('unit');
    picker.open = true;
    picker.choose({ id: 'u1', label: 'Ops (OPS)' });
    expect(picker.query).toBe('Ops (OPS)');
    expect(picker.open).toBe(false);
    expect(picked).toEqual([{ id: 'u1', label: 'Ops (OPS)' }]);
  });

  it('closes the list shortly after blur so a click on a result still lands', () => {
    const { picker, cdr } = setup('unit');
    picker.open = true;
    picker.onBlur();
    expect(picker.open).toBe(true);
    vi.advanceTimersByTime(160);
    expect(picker.open).toBe(false);
    expect(cdr.detectChanges).toHaveBeenCalled();
  });

  it('shows the selected label, and searches everything when focused on it', () => {
    const { picker, api } = setup('unit');
    picker.selectedLabel = 'Ops (OPS)';
    picker.ngOnChanges({ selectedLabel: new SimpleChange('', 'Ops (OPS)', true) });
    expect(picker.query).toBe('Ops (OPS)');
    picker.onFocus();
    vi.advanceTimersByTime(DEBOUNCE);
    expect(api.searchUnits).toHaveBeenCalledWith('', PICKER_MAX_RESULTS);
  });

  it('forgets results when the ladder changes after the first binding', () => {
    const { picker } = setup('position');
    picker.items = [{ id: 'x', label: 'x' }];
    picker.ngOnChanges({ ladderId: new SimpleChange(null, 'l1', true) });
    expect(picker.items).toHaveLength(1);
    picker.ngOnChanges({ ladderId: new SimpleChange('l1', 'l2', false) });
    expect(picker.items).toEqual([]);
  });

  it('stops listening for queries once destroyed', () => {
    const { picker, api } = setup('unit');
    picker.ngOnDestroy();
    picker.onInput('a');
    vi.advanceTimersByTime(DEBOUNCE);
    expect(api.searchUnits).not.toHaveBeenCalled();
  });
});
