import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HqSharedLibModule } from '../../hq-shared-lib.module';
import { HQ_TABLE_FOOTER_DEFAULT_SIZE, HQ_TABLE_FOOTER_FIRST_PAGE } from './hq-table-footer.constants';
import { HqTableFooter } from './hq-table-footer';
import { HQ_TABLE_FOOTER_DEFAULT_TEXTS, HQ_TABLE_FOOTER_TEXTS, HqTableFooterTexts } from './hq-table-footer.texts';

describe('HqTableFooter', () => {
  let fixture: ComponentFixture<HqTableFooter>;
  let comp: HqTableFooter;
  let el: HTMLElement;
  let pages: number[];
  let sizes: number[];

  function create(texts?: HqTableFooterTexts): void {
    TestBed.configureTestingModule({
      imports: [HqSharedLibModule],
      providers: texts ? [{ provide: HQ_TABLE_FOOTER_TEXTS, useValue: texts }] : [],
    });
    fixture = TestBed.createComponent(HqTableFooter);
    comp = fixture.componentInstance;
    el = fixture.nativeElement;
    pages = [];
    sizes = [];
    comp.pageChange.subscribe((p) => pages.push(p));
    comp.sizeChange.subscribe((s) => sizes.push(s));
  }

  const set = (inputs: Record<string, unknown>) => {
    Object.entries(inputs).forEach(([k, v]) => fixture.componentRef.setInput(k, v));
    fixture.detectChanges();
  };
  const range = () => el.querySelector('.table-footer__range')?.textContent?.replace(/\s+/g, ' ').trim();
  const buttons = () => Array.from(el.querySelectorAll<HTMLButtonElement>('.table-footer__btn'));

  describe('with the default texts', () => {
    beforeEach(() => create());

    it('defaults to the first page and the services page size', () => {
      expect(comp.page).toBe(HQ_TABLE_FOOTER_FIRST_PAGE);
      expect(comp.size).toBe(HQ_TABLE_FOOTER_DEFAULT_SIZE);
      expect(comp.texts).toBe(HQ_TABLE_FOOTER_DEFAULT_TEXTS);
    });

    it('shows "No results" for an empty page and disables every button', () => {
      set({});
      expect(range()).toBe('No results');
      expect(comp.rangeStart).toBe(0);
      expect(buttons().every((b) => b.disabled)).toBe(true);
      expect(el.querySelector('.table-footer__size')).toBeNull();
      expect(el.querySelector('.table-footer__current')?.textContent).toBe('1');
    });

    it('computes the range from page, size and the items returned, with the total when known', () => {
      set({ page: 2, size: 10, itemCount: 7 });
      expect(comp.rangeStart).toBe(21);
      expect(comp.rangeEnd).toBe(27);
      expect(range()).toBe('Showing 21–27');
      set({ totalItems: 27 });
      expect(range()).toBe('Showing 21–27 of 27');
      expect(el.querySelector('.table-footer__current')?.textContent).toBe('3');
    });

    it('shows a total of zero (not hidden) when the server says 0', () => {
      set({ itemCount: 1, totalItems: 0 });
      expect(range()).toBe('Showing 1–1 of 0');
    });

    it('emits the first, previous and next pages from the buttons', () => {
      set({ page: 3, size: 10, itemCount: 10, hasMore: true });
      const [first, prev, next] = buttons();
      expect(first.getAttribute('aria-label')).toBe('First page');
      expect(prev.title).toBe('Previous page');
      expect(next.getAttribute('aria-label')).toBe('Next page');
      first.click();
      prev.click();
      next.click();
      expect(pages).toEqual([0, 2, 4]);
    });

    it('does not move past either end or while loading', () => {
      set({ page: 0, itemCount: 5, hasMore: false });
      expect(comp.disablePrev).toBe(true);
      expect(comp.disableNext).toBe(true);
      comp.first();
      comp.prev();
      comp.next();
      set({ page: 4, hasMore: true, loading: true });
      expect(comp.disablePrev).toBe(true);
      expect(comp.disableNext).toBe(true);
      comp.first();
      comp.prev();
      comp.next();
      expect(pages).toEqual([]);
    });

    it('renders the size selector and emits only a real, different, positive size', () => {
      set({ size: 10, itemCount: 10, sizeOptions: [10, 25, 50] });
      const select = el.querySelector<HTMLSelectElement>('.table-footer__size-select')!;
      expect(Array.from(select.options).map((o) => o.textContent)).toEqual(['10', '25', '50']);
      expect(el.querySelector('.table-footer__size span')?.textContent).toBe('Rows');
      select.value = '25';
      select.dispatchEvent(new Event('change'));
      expect(sizes).toEqual([25]);

      comp.onSizeChange('10');
      comp.onSizeChange('abc');
      comp.onSizeChange('0');
      comp.onSizeChange('-5');
      expect(sizes).toEqual([25]);
    });

    it('hides the size selector for an empty options list and disables it while loading', () => {
      set({ sizeOptions: [] });
      expect(el.querySelector('.table-footer__size')).toBeNull();
      set({ sizeOptions: [10], loading: true });
      expect(el.querySelector<HTMLSelectElement>('.table-footer__size-select')?.disabled).toBe(true);
    });
  });

  it('uses the app texts from HQ_TABLE_FOOTER_TEXTS', () => {
    create({
      noResults: () => 'Rien',
      showing: () => 'Affichage',
      of: () => 'sur',
      rows: () => 'Lignes',
      firstPage: () => 'Première',
      previousPage: () => 'Précédente',
      nextPage: () => 'Suivante',
    });
    set({});
    expect(range()).toBe('Rien');
    set({ itemCount: 3, totalItems: 3, sizeOptions: [20] });
    expect(range()).toBe('Affichage 1–3 sur 3');
    expect(el.querySelector('.table-footer__size span')?.textContent).toBe('Lignes');
    expect(buttons().map((b) => b.title)).toEqual(['Première', 'Précédente', 'Suivante']);
  });
});
