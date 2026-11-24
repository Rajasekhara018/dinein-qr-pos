import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Observable, Subject, of, throwError } from 'rxjs';
import { HqSharedLibModule } from '../../hq-shared-lib.module';
import { HQ_LOOKUP_DEFAULT_TEXTS, HQ_LOOKUP_TEXTS, HqLookupTexts } from '../hq-lookup.texts';
import { HqLookupConfig, HqLookupDialog, HqPickerPage } from './hq-lookup-dialog';

interface User {
  id: string;
  name: string;
  email?: string | null;
  org?: { city: string } | null;
}

const ANA: User = { id: 'u1', name: 'Ana Maria Lopez', email: 'ana@x.io', org: { city: 'Pune' } };
const BOB: User = { id: 'u2', name: 'bob', email: null, org: null };
const CY: User = { id: 'u3', name: 'Cy Young', email: 'cy@x.io' };

describe('HqLookupDialog', () => {
  let fixture: ComponentFixture<HqLookupDialog<User>>;
  let comp: HqLookupDialog<User>;
  let el: HTMLElement;
  let close: ReturnType<typeof vi.fn>;
  let fetch: ReturnType<typeof vi.fn>;

  function create(config: Partial<HqLookupConfig<User>> = {}, texts?: HqLookupTexts): void {
    close = vi.fn();
    fetch = fetch ?? vi.fn(() => of({ items: [ANA, BOB], total: 2 }));
    const data: HqLookupConfig<User> = {
      title: 'Select users',
      columns: [
        { key: 'name', label: 'Name' },
        { key: 'email', label: 'Email' },
        { key: 'org.city', label: 'City' },
      ],
      idOf: (u) => u.id,
      fetch: fetch as unknown as HqLookupConfig<User>['fetch'],
      ...config,
    };
    TestBed.configureTestingModule({
      imports: [HqSharedLibModule],
      providers: [
        { provide: MatDialogRef, useValue: { close } },
        { provide: MAT_DIALOG_DATA, useValue: data },
        ...(texts ? [{ provide: HQ_LOOKUP_TEXTS, useValue: texts }] : []),
      ],
    });
    fixture = TestBed.createComponent(HqLookupDialog<User>);
    comp = fixture.componentInstance;
    el = fixture.nativeElement;
    fixture.detectChanges();
  }

  const q = <E extends HTMLElement = HTMLElement>(sel: string) => el.querySelector<E>(sel);
  const all = (sel: string) => Array.from(el.querySelectorAll<HTMLElement>(sel));
  const names = () => all('.lookup__row:not(.lookup__row--skeleton) .lookup__name').map((n) => n.textContent?.trim());
  const typeQuery = (value: string) => {
    const input = q<HTMLInputElement>('.lookup__search input')!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };

  beforeEach(() => {
    vi.useFakeTimers();
    fetch = undefined as unknown as ReturnType<typeof vi.fn>;
  });
  afterEach(() => vi.useRealTimers());

  describe('loading the first page', () => {
    it('fetches page 0 with an empty query and the default page size, then renders the rows', () => {
      create();
      expect(fetch).toHaveBeenCalledWith('', 0, 10);
      expect(names()).toEqual(['Ana Maria Lopez', 'bob']);
      expect(q('.lookup__total')?.textContent).toBe('2');
      expect(q('.lookup__heading h2')?.textContent).toBe('Select users');
      expect(q('.lookup__heading p')).toBeNull();
      expect(comp.texts).toBe(HQ_LOOKUP_DEFAULT_TEXTS);
    });

    it('shows a table with a header per column and the values of each record, the first in bold', () => {
      create();
      expect(all('.lookup__list th').map((th) => th.textContent?.trim())).toEqual(['Name', 'Email', 'City', '']);
      const rows = all('.lookup__row');
      expect(Array.from(rows[0].querySelectorAll('td')).map((td) => td.textContent?.trim())).toEqual(['Ana Maria Lopez', 'ana@x.io', 'Pune', '']);
      expect(rows[0].querySelector('.lookup__name')?.textContent).toBe('Ana Maria Lopez');
      // a missing value is an empty cell, not the word "null"
      expect(Array.from(rows[1].querySelectorAll('td')).map((td) => td.textContent?.trim())).toEqual(['bob', '', '', '']);
    });

    it('searches at once from the SEARCH button and from Enter, and clears the search from the clear button', () => {
      create();
      fetch.mockClear();
      typeQuery('ana');
      q<HTMLButtonElement>('.lookup__go')!.click();
      expect(fetch).toHaveBeenCalledWith('ana', 0, 10);

      fetch.mockClear();
      q<HTMLButtonElement>('.lookup__clear')!.click();
      expect(comp.query).toBe('');
      expect(fetch).toHaveBeenCalledWith('', 0, 10);
    });

    it('names what is being shown once a search has results', () => {
      create();
      typeQuery('ana');
      vi.advanceTimersByTime(400);
      fixture.detectChanges();
      expect(q('.lookup__showing')?.textContent).toContain('Showing results for');
      expect(q('.lookup__showing b')?.textContent).toBe('ana');
    });

    it('uses the configured page size, subtitle and placeholder', () => {
      create({ pageSize: 25, subtitle: 'Tick people', searchPlaceholder: 'Find a user' });
      expect(fetch).toHaveBeenCalledWith('', 0, 25);
      expect(q('.lookup__heading p')?.textContent).toBe('Tick people');
      expect(q<HTMLInputElement>('.lookup__search input')?.placeholder).toBe('Find a user');
    });

    it('falls back to the default search placeholder', () => {
      create();
      expect(q<HTMLInputElement>('.lookup__search input')?.placeholder).toBe('Search...');
    });

    it('shows skeleton rows while the first page is in flight', () => {
      const pending = new Subject<HqPickerPage<User>>();
      fetch = vi.fn(() => pending);
      create();
      expect(comp.isLoading).toBe(true);
      expect(all('.lookup__row--skeleton').length).toBe(5);
      pending.next({ items: [CY], total: 1 });
      fixture.detectChanges();
      expect(all('.lookup__row--skeleton').length).toBe(0);
      expect(names()).toEqual(['Cy Young']);
    });

    it('shows the empty state when nothing matches', () => {
      fetch = vi.fn(() => of({ items: [], total: 0 }));
      create();
      expect(q('.lookup__state p')?.textContent).toBe('No matching records.');
      expect(q('.lookup__list')).toBeNull();
    });

    it('treats a missing items array and total as no rows and an unknown total', () => {
      fetch = vi.fn(() => of({} as HqPickerPage<User>));
      create();
      expect(comp.rows).toEqual([]);
      expect(comp.total).toBeNull();
      expect(q('.lookup__total')).toBeNull();
    });
  });

  describe('errors', () => {
    it('shows the server detail and reloads the same page on retry', () => {
      fetch = vi.fn()
        .mockReturnValueOnce(throwError(() => ({ error: { detail: 'Search is down' } })))
        .mockReturnValue(of({ items: [CY], total: 1 }));
      create();
      expect(comp.hasError).toBe(true);
      expect(q('.lookup__state--error p')?.textContent).toBe('Failed to load data.');
      expect(q('.lookup__state--error small')?.textContent).toBe('Search is down');
      expect(q('hq-table-footer')).toBeNull();
      expect(q('.lookup__total')).toBeNull();

      q<HTMLButtonElement>('.lookup__retry')!.click();
      fixture.detectChanges();
      expect(fetch).toHaveBeenLastCalledWith('', 0, 10);
      expect(comp.hasError).toBe(false);
      expect(names()).toEqual(['Cy Young']);
    });

    it('uses the server message when there is no detail, and nothing when the error has no body', () => {
      fetch = vi.fn(() => throwError(() => ({ error: { message: 'Bad gateway' } })));
      create();
      expect(comp.errorMessage).toBe('Bad gateway');

      fetch.mockReturnValue(throwError(() => new Error('network')));
      comp.retry();
      fixture.detectChanges();
      expect(comp.errorMessage).toBe('');
      expect(q('.lookup__state--error small')).toBeNull();

      fetch.mockReturnValue(throwError(() => null));
      comp.retry();
      expect(comp.errorMessage).toBe('');
    });
  });

  describe('searching', () => {
    it('debounces typing and searches the trimmed text from page 0', () => {
      create();
      fetch.mockClear();
      typeQuery('  ana ');
      expect(comp.query).toBe('  ana ');
      vi.advanceTimersByTime(299);
      expect(fetch).not.toHaveBeenCalled();
      vi.advanceTimersByTime(1);
      expect(fetch).toHaveBeenCalledWith('ana', 0, 10);
    });

    it('does not search again for the same trimmed text', () => {
      create();
      fetch.mockClear();
      typeQuery('ana');
      vi.advanceTimersByTime(300);
      typeQuery('ana ');
      vi.advanceTimersByTime(300);
      expect(fetch).toHaveBeenCalledTimes(1);
    });

    it('drops the answer of an older request when a newer one starts', () => {
      const first = new Subject<HqPickerPage<User>>();
      const second = new Subject<HqPickerPage<User>>();
      fetch = vi.fn().mockReturnValueOnce(of({ items: [], total: 0 })).mockReturnValueOnce(first).mockReturnValueOnce(second);
      create();
      typeQuery('a');
      vi.advanceTimersByTime(300);
      typeQuery('an');
      vi.advanceTimersByTime(300);
      first.next({ items: [BOB], total: 1 });
      second.next({ items: [ANA], total: 1 });
      fixture.detectChanges();
      expect(names()).toEqual(['Ana Maria Lopez']);
    });

    it('asks for more characters below the minimum length and does not call the server', () => {
      create({ minQueryLength: 3 });
      expect(fetch).not.toHaveBeenCalled();
      expect(comp.tooShort).toBe(true);
      expect(q('.lookup__state p')?.textContent).toBe('Type at least 3 characters to search.');
      expect(q('hq-table-footer')).toBeNull();

      typeQuery('an');
      vi.advanceTimersByTime(300);
      expect(fetch).not.toHaveBeenCalled();

      typeQuery('ana');
      vi.advanceTimersByTime(300);
      fixture.detectChanges();
      expect(fetch).toHaveBeenCalledWith('ana', 0, 10);
      expect(names()).toEqual(['Ana Maria Lopez', 'bob']);
    });

    it('stops listening once destroyed', () => {
      create();
      fetch.mockClear();
      typeQuery('x');
      fixture.destroy();
      vi.advanceTimersByTime(300);
      expect(fetch).not.toHaveBeenCalled();
    });
  });

  describe('paging', () => {
    it('moves to another page and changes the size through the footer', () => {
      fetch = vi.fn(() => of({ items: [ANA, BOB], total: 30 }));
      create({ pageSize: 2 });
      expect(comp.hasMore).toBe(true);
      const next = all('.table-footer__btn')[2] as HTMLButtonElement;
      next.click();
      expect(fetch).toHaveBeenLastCalledWith('', 1, 2);
      expect(comp.page).toBe(1);

      fixture.detectChanges();
      const select = q<HTMLSelectElement>('.table-footer__size-select')!;
      select.value = '25';
      select.dispatchEvent(new Event('change'));
      expect(comp.size).toBe(25);
      expect(fetch).toHaveBeenLastCalledWith('', 0, 25);
    });

    it('goTo and resize search the current trimmed query', () => {
      create();
      comp.query = ' bo ';
      comp.goTo(3);
      expect(fetch).toHaveBeenLastCalledWith('bo', 3, 10);
      comp.resize(50);
      expect(fetch).toHaveBeenLastCalledWith('bo', 0, 50);
    });

    it('knows whether more pages exist from the total, or from a full page when the total is unknown', () => {
      create({ pageSize: 2 });
      comp.total = 4; comp.page = 1;
      expect(comp.hasMore).toBe(false);
      comp.total = 5;
      expect(comp.hasMore).toBe(true);
      comp.total = null; comp.rows = [ANA, BOB];
      expect(comp.hasMore).toBe(true);
      comp.rows = [ANA];
      expect(comp.hasMore).toBe(false);
    });
  });

  describe('single select', () => {
    it('confirms the clicked row straight away and has no confirm button', () => {
      create();
      expect(q('.lookup__confirm')).toBeNull();
      expect(q('.lookup__chips')).toBeNull();
      all('.lookup__row')[1].click();
      expect(close).toHaveBeenCalledWith([BOB]);
    });

    it('replaces the earlier choice when toggled', () => {
      create({ selected: [ANA] });
      comp.toggle(BOB);
      expect(comp.chosenItems).toEqual([BOB]);
    });

    it('closes with null from the Close button', () => {
      create();
      q<HTMLButtonElement>('.lookup__x')!.click();
      expect(close.mock.calls).toEqual([[null]]);
      expect(q('.lookup__x')?.textContent?.trim()).toBe('Close');
    });
  });

  describe('multiple select', () => {
    it('starts with the already-chosen records ticked and as chips', () => {
      create({ multiple: true, selected: [ANA] });
      expect(comp.isChosen(ANA)).toBe(true);
      expect(all('.lookup__row')[0].classList).toContain('lookup__row--chosen');
      expect(all('.lookup__row')[0].querySelector('.lookup__tick')?.classList).toContain('lookup__tick--on');
      expect(q('.lookup__count')?.textContent).toBe('1 selected');
      expect(q('.lookup__chip')?.textContent).toContain('Ana Maria Lopez');
      expect(q('.lookup__chip-avatar')?.textContent).toBe('AL');
      expect(q('.lookup__confirm')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Select (1)');
    });

    it('toggles rows on click without closing, and confirms the selection', () => {
      create({ multiple: true });
      const confirm = () => q<HTMLButtonElement>('.lookup__confirm')!;
      expect(confirm().disabled).toBe(true);
      expect(confirm().textContent?.trim()).toBe('Select');

      all('.lookup__row')[0].click();
      all('.lookup__row')[1].click();
      fixture.detectChanges();
      expect(close).not.toHaveBeenCalled();
      expect(q('.lookup__count')?.textContent).toBe('2 selected');

      all('.lookup__row')[0].click();
      fixture.detectChanges();
      expect(comp.chosenItems).toEqual([BOB]);

      confirm().click();
      expect(close).toHaveBeenCalledWith([BOB]);
    });

    it('removes a chip and clears the whole selection', () => {
      create({ multiple: true, selected: [ANA, BOB, CY] });
      q<HTMLButtonElement>('.lookup__chip button')!.click();
      fixture.detectChanges();
      expect(comp.chosenItems).toEqual([BOB, CY]);
      expect(q('.lookup__link')?.textContent).toBe('Clear selection');
      q<HTMLButtonElement>('.lookup__link')!.click();
      fixture.detectChanges();
      expect(comp.chosen.size).toBe(0);
      expect(q('.lookup__chips')).toBeNull();
    });

    it('uses the configured confirm text and chip label', () => {
      create({ multiple: true, selected: [CY], confirmText: 'Add people', chipLabel: (u) => u.email ?? '' });
      expect(q('.lookup__confirm')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Add people (1)');
      expect(q('.lookup__chip')?.textContent).toContain('cy@x.io');
      expect(comp.initials(CY)).toBe('C');
    });
  });

  describe('cell helpers', () => {
    beforeEach(() => create());

    it('uses a column render function when given', () => {
      expect(comp.cell(ANA, { key: 'x', label: 'X', render: (u) => u.name.toUpperCase() })).toBe('ANA MARIA LOPEZ');
    });

    it('reads dot paths and turns missing values into empty text', () => {
      expect(comp.cell(ANA, { key: 'org.city', label: '' })).toBe('Pune');
      expect(comp.cell(BOB, { key: 'org.city', label: '' })).toBe('');
      expect(comp.cell(CY, { key: 'org.city', label: '' })).toBe('');
      expect(comp.cell(BOB, { key: 'email', label: '' })).toBe('');
    });

    it('falls back to "?" for a record with no label text', () => {
      expect(comp.initials({ id: 'z', name: '   ' })).toBe('?');
    });

    it('tracks rows by id', () => {
      expect(comp.trackRow(0, CY)).toBe('u3');
    });
  });

  describe('search box', () => {
    it('is the shared hq-field with a search icon, so it follows the app theme', () => {
      create();
      const field = q('hq-field.lookup__field');
      expect(field).not.toBeNull();
      expect(field?.querySelector('input')?.getAttribute('type')).toBe('search');
      expect(field?.querySelector('.hq-field__icon')?.classList).toContain('bi-search');
      expect(q('.lookup__search input')?.getAttribute('aria-label')).toBe('Search');
    });

    it('searches at once on Enter', () => {
      create();
      fetch.mockClear();
      typeQuery('ana');
      q('hq-field.lookup__field')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
      expect(fetch).toHaveBeenCalledWith('ana', 0, 10);
    });
  });

  describe('search-scope chips', () => {
    const chips = () => all('.lookup__scope');

    it('shows "All fields" then one chip per column, with All fields selected', () => {
      create();
      expect(chips().map((c) => c.textContent?.trim())).toEqual(['All fields', 'Name', 'Email', 'City']);
      expect(chips()[0].getAttribute('aria-selected')).toBe('true');
      expect(chips()[0].classList).toContain('is-active');
      expect(chips().slice(1).every((c) => c.getAttribute('aria-selected') === 'false')).toBe(true);
      expect(q('.lookup__scopes')?.getAttribute('role')).toBe('listbox');
      expect(chips()[0].getAttribute('role')).toBe('option');
    });

    it('leaves out columns marked searchable: false, and hides the chips when only one column can be searched', () => {
      create({ columns: [{ key: 'name', label: 'Name' }, { key: 'email', label: 'Email', searchable: false }] });
      expect(q('.lookup__scopes')).toBeNull();
      TestBed.resetTestingModule();
      fetch = undefined as unknown as ReturnType<typeof vi.fn>;
      create({ columns: [{ key: 'name', label: 'Name' }, { key: 'email', label: 'Email' }, { key: 'org.city', label: 'City', searchable: false }] });
      expect(chips().map((c) => c.textContent?.trim())).toEqual(['All fields', 'Name', 'Email']);
    });

    it('passes the chosen column to the search, goes back to page 1, and keeps the choice', () => {
      create({ scopedSearch: true });
      typeQuery('ana');
      vi.advanceTimersByTime(400);
      comp.goTo(2);
      fetch.mockClear();
      chips()[2].click();
      fixture.detectChanges();
      expect(fetch).toHaveBeenCalledWith('ana', 0, 10, 'email');
      expect(chips()[2].getAttribute('aria-selected')).toBe('true');
      expect(chips()[2].classList).toContain('is-active');
      expect(chips()[0].getAttribute('aria-selected')).toBe('false');
      expect(comp.page).toBe(0);

      // the choice stays for the next search, and "All fields" drops the column argument again
      fetch.mockClear();
      q<HTMLButtonElement>('.lookup__go')!.click();
      expect(fetch).toHaveBeenCalledWith('ana', 0, 10, 'email');
      fetch.mockClear();
      chips()[0].click();
      expect(fetch).toHaveBeenCalledWith('ana', 0, 10);
    });

    it('names the chosen column in the search box placeholder', () => {
      create({ searchPlaceholder: 'Find a user' });
      expect(q<HTMLInputElement>('.lookup__search input')?.placeholder).toBe('Find a user');
      chips()[2].click();
      fixture.detectChanges();
      expect(q<HTMLInputElement>('.lookup__search input')?.placeholder).toBe('Search in Email');
    });

    it('narrows the page to the chosen column itself when the search callback cannot scope by column', () => {
      fetch = vi.fn(() => of({ items: [ANA, BOB, CY], total: 3 }));
      create();
      typeQuery('cy');
      vi.advanceTimersByTime(400);
      chips()[1].click();            // Name
      fixture.detectChanges();
      expect(names()).toEqual(['Cy Young']);
      chips()[2].click();            // Email
      fixture.detectChanges();
      expect(names()).toEqual(['Cy Young']);
      expect(comp.narrowed).toBe(true);
      expect(q('.lookup__total')).toBeNull();
      chips()[3].click();            // City: nobody's city contains "cy"
      fixture.detectChanges();
      expect(all('.lookup__row').length).toBe(0);
    });

    it('does not narrow when the callback scopes by column on the server', () => {
      fetch = vi.fn(() => of({ items: [ANA, BOB, CY], total: 3 }));
      create({ scopedSearch: true });
      typeQuery('zzz');
      vi.advanceTimersByTime(400);
      chips()[1].click();
      fixture.detectChanges();
      expect(names()).toEqual(['Ana Maria Lopez', 'bob', 'Cy Young']);
      expect(comp.narrowed).toBe(false);
    });

    it('uses the app words for the chips', () => {
      const texts: HqLookupTexts = {
        ...HQ_LOOKUP_DEFAULT_TEXTS,
        allFields: () => 'Tous les champs',
        searchIn: (field) => `Chercher dans ${field}`,
        searchFields: () => 'Chercher dans',
      };
      create({}, texts);
      expect(chips()[0].textContent?.trim()).toBe('Tous les champs');
      expect(q('.lookup__scopes')?.getAttribute('aria-label')).toBe('Chercher dans');
      chips()[1].click();
      fixture.detectChanges();
      expect(q<HTMLInputElement>('.lookup__search input')?.placeholder).toBe('Chercher dans Name');
    });
  });

  describe('multi-select checkbox column', () => {
    const rowChecks = () => all('.lookup__row td.lookup__col-select [role="checkbox"]');

    it('puts a checkbox column first, always in view, and no trailing pick column', () => {
      create({ multiple: true });
      expect(all('.lookup__list th').map((th) => th.textContent?.trim())).toEqual(['', 'Name', 'Email', 'City']);
      expect(q('th.lookup__col-pick')).toBeNull();
      expect(q('.lookup__col-select')).not.toBeNull();
      expect(rowChecks().length).toBe(2);
    });

    it('keeps the round pick button for a single choice and no checkbox column', () => {
      create();
      expect(q('.lookup__col-select')).toBeNull();
      expect(q('td.lookup__col-pick .lookup__tick')).not.toBeNull();
    });

    it('shows the ticked state with aria-checked and the chosen row class, and toggles on click', () => {
      create({ multiple: true, selected: [ANA] });
      expect(rowChecks().map((c) => c.getAttribute('aria-checked'))).toEqual(['true', 'false']);
      expect(rowChecks()[0].classList).toContain('lookup__tick--on');
      expect(all('.lookup__row')[0].classList).toContain('lookup__row--chosen');
      expect(rowChecks()[0].getAttribute('aria-label')).toBe('Ana Maria Lopez');

      all('.lookup__row')[1].click();
      fixture.detectChanges();
      expect(rowChecks().map((c) => c.getAttribute('aria-checked'))).toEqual(['true', 'true']);
      expect(all('.lookup__row')[1].classList).toContain('lookup__row--chosen');
      all('.lookup__row')[0].click();
      fixture.detectChanges();
      expect(rowChecks().map((c) => c.getAttribute('aria-checked'))).toEqual(['false', 'true']);
      expect(close).not.toHaveBeenCalled();
    });

    it('has a header checkbox that ticks every record on the page, shows a mixed state, and clears them', () => {
      create({ multiple: true, selected: [ANA] });
      const header = () => q<HTMLButtonElement>('th.lookup__col-select [role="checkbox"]')!;
      expect(header().getAttribute('aria-checked')).toBe('mixed');
      expect(header().getAttribute('aria-label')).toBe('Select all on this page');

      header().click();
      fixture.detectChanges();
      expect(comp.chosenItems).toEqual([ANA, BOB]);
      expect(header().getAttribute('aria-checked')).toBe('true');

      header().click();
      fixture.detectChanges();
      expect(comp.chosenItems).toEqual([]);
      expect(header().getAttribute('aria-checked')).toBe('false');
    });

    it('keeps choices made on other pages when the header checkbox clears this page', () => {
      create({ multiple: true, selected: [CY] });
      q<HTMLButtonElement>('th.lookup__col-select [role="checkbox"]')!.click();
      fixture.detectChanges();
      expect(comp.chosenItems.map((u) => u.id).sort()).toEqual(['u1', 'u2', 'u3']);
      q<HTMLButtonElement>('th.lookup__col-select [role="checkbox"]')!.click();
      fixture.detectChanges();
      expect(comp.chosenItems).toEqual([CY]);
    });
  });

  describe('table layout', () => {
    it('is built on the shared hq-common-table, with row classes and focusable rows', () => {
      create();
      expect(q('hq-common-table.lookup__list')).not.toBeNull();
      expect(all('.lookup__row')[0].tagName).toBe('TR');
      expect(all('.lookup__row')[0].getAttribute('tabindex')).toBe('0');
    });

    it('confirms a single choice from the Enter key on a focused row', () => {
      create();
      all('.lookup__row')[1].dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
      expect(close).toHaveBeenCalledWith([BOB]);
    });

    it('shows an avatar with the initials before the first column without adding text to the cell', () => {
      create();
      const first = all('.lookup__row')[0].querySelector('td')!;
      expect(first.querySelector('.lookup__avatar')?.getAttribute('data-initials')).toBe('AL');
      expect(first.textContent?.trim()).toBe('Ana Maria Lopez');
    });

    it('gives each cell its full text as a tooltip, for values the table cuts short', () => {
      create();
      const cells = Array.from(all('.lookup__row')[0].querySelectorAll('td'));
      expect(cells.map((td) => td.getAttribute('title'))).toEqual(['Ana Maria Lopez', 'ana@x.io', 'Pune', null]);
    });

    it('casts the header shadow once the body has scrolled', () => {
      create();
      const body = q('.lookup__body')!;
      expect(body.classList).not.toContain('lookup__body--scrolled');
      Object.defineProperty(body, 'scrollTop', { value: 24, configurable: true });
      body.dispatchEvent(new Event('scroll'));
      fixture.detectChanges();
      expect(body.classList).toContain('lookup__body--scrolled');
    });
  });

  it('uses the app texts from HQ_LOOKUP_TEXTS', () => {
    const texts: HqLookupTexts = {
      search: () => 'Chercher',
      typeToSearch: (n) => `Au moins ${n}`,
      noResults: () => 'Aucun',
      loadFailed: () => 'Échec',
      retry: () => 'Réessayer',
      selected: (n) => `${n} choisis`,
      clearSelection: () => 'Effacer',
      cancel: () => 'Annuler',
      select: () => 'Choisir',
    };
    fetch = vi.fn(() => of({ items: [], total: 0 }));
    create({ multiple: true, selected: [ANA] }, texts);
    expect(q<HTMLInputElement>('.lookup__search input')?.placeholder).toBe('Chercher');
    expect(q('.lookup__state p')?.textContent).toBe('Aucun');
    expect(q('.lookup__count')?.textContent).toBe('1 choisis');
    expect(q('.lookup__link')?.textContent).toBe('Effacer');
    // the optional words an app leaves out fall back to the English default
    expect(q('.lookup__x')?.textContent?.trim()).toBe('Close');
    expect(q('.lookup__confirm')?.textContent).toContain('Choisir');

    fetch.mockReturnValue(throwError(() => ({})) as Observable<HqPickerPage<User>>);
    comp.retry();
    fixture.detectChanges();
    expect(q('.lookup__state--error p')?.textContent).toBe('Échec');
    expect(q('.lookup__retry')?.textContent?.trim()).toBe('Réessayer');
  });
});
