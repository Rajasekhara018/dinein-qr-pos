import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { Observable, firstValueFrom, of, throwError } from 'rxjs';
import { HqCommonModel } from './hq-common-model/hq-common-model';
import { HqCommonSearchService } from './hq-common-search.service';
import { HQ_CONFIG } from './hq-common.config';
import { HqAdvancedQuery, HqLookupFetchRequest, HqLookupPage } from './hq-common.models';

const SEARCH = 'http://gw/api/v1/search';
const ADMIN = 'http://admin/api/v1';
const UAM = 'http://gw/api/v1/uam';

const req = (overrides: Partial<HqLookupFetchRequest> = {}): HqLookupFetchRequest => ({
  page: 0, size: 10, search: '', searchField: 'name', ...overrides,
});
const filter = (fieldName: string, operator: HqAdvancedQuery['operator'], value: string, condition: 'AND' | 'OR' = 'AND'): HqAdvancedQuery =>
  ({ fieldName, operator, value, condition });

type Row = { id: number; name: string; city?: string | null; owner?: { name: string } };
const ROWS: Row[] = [
  { id: 1, name: 'Alpha Cabs', city: 'Chennai', owner: { name: 'Ravi' } },
  { id: 2, name: 'Beta Rides', city: 'Hyderabad', owner: { name: 'Sita' } },
  { id: 3, name: 'Gamma Travels', city: null },
];

describe('HqCommonSearchService', () => {
  let service: HqCommonSearchService;
  let http: HttpTestingController;
  let dialogOpen: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    dialogOpen = vi.fn(() => ({ afterClosed: () => of({ lookupTable: 'VENDORS', modelKey: '7' }) }));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialog, useValue: { open: dialogOpen } },
        { provide: HQ_CONFIG, useValue: { gatewayUrl: 'http://gw/api/', adminApiUrl: `${ADMIN}/`, currentUserId: () => 'u' } },
      ],
    });
    service = TestBed.inject(HqCommonSearchService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /** Registers a client-side table whose fetch() returns ROWS and records each request. */
  function registerLocal(serverSearch?: boolean): HqLookupFetchRequest[] {
    const calls: HqLookupFetchRequest[] = [];
    service.register({
      id: 'LOCAL', title: 'Local', keyField: 'id', displayField: 'name', defaultSearchField: 'name', serverSearch,
      columns: [{ key: 'name', label: 'Name' }],
      fetch: (r) => {
        calls.push(r);
        return of({ rows: ROWS, total: 99 });
      },
    });
    return calls;
  }

  function expectElastic(model: string): TestRequest {
    const request = http.expectOne(`${SEARCH}/elastic/${model}/getall`);
    expect(request.request.method).toBe('POST');
    return request;
  }

  describe('registry', () => {
    it('has the built-in Rider tables', () => {
      for (const id of ['UAM_USERS', 'UAM_ROLES', 'VENDORS', 'VEHICLES']) {
        expect(service.getTable(id).id).toBe(id);
      }
      expect(service.getTable('UAM_USERS').serverSearch).toBe(true);
      expect(service.getTable('VEHICLES').serverSearch).toBe(true);
      expect(service.getTable('VENDORS').serverSearch).toBeUndefined();
    });

    it('throws for an unknown table', () => {
      expect(() => service.getTable('NOPE')).toThrow("Unknown hq lookup table 'NOPE'");
    });

    it('an index-backed table searches on the server and gets a fetch() from the index', async () => {
      service.register({
        id: 'CITIES', title: 'Cities', keyField: 'id', displayField: 'name', defaultSearchField: 'name', serverSearch: false,
        columns: [], elastic: { model: 'city', slots: { name: 'text1' } },
      });
      const table = service.getTable('CITIES');
      expect(table.serverSearch).toBe(true);
      const result = firstValueFrom(table.fetch(req()));
      expectElastic('city').flush({ success: true, object: { content: [{ modelKey: 'c1', text1: 'Pune' }], totalElements: 1 } });
      expect(await result).toEqual({ rows: [{ id: 'c1', name: 'Pune' }], total: 1 });
    });

    it('re-registering an id replaces the table', () => {
      registerLocal();
      registerLocal(true);
      expect(service.getTable('LOCAL').serverSearch).toBe(true);
    });
  });

  describe('openLookup', () => {
    it('opens the lookup dialog with a copy of the data and resolves with its result', async () => {
      const data = { lookupTable: 'VENDORS', multiSelect: false };
      const result = await firstValueFrom(service.openLookup(data));
      expect(dialogOpen).toHaveBeenCalledWith(HqCommonModel, {
        data: { lookupTable: 'VENDORS', multiSelect: false }, width: '960px', maxWidth: '95vw', autoFocus: false,
      });
      expect(dialogOpen.mock.calls[0][1].data).not.toBe(data);
      expect(result).toEqual({ lookupTable: 'VENDORS', modelKey: '7' });
    });

    it('uses the given width', () => {
      service.openLookup({ lookupTable: 'VENDORS' }, '600px').subscribe();
      expect(dialogOpen.mock.calls[0][1].width).toBe('600px');
    });
  });

  describe('query on a client-side table', () => {
    it('returns the fetched page untouched when there is nothing to filter', async () => {
      const calls = registerLocal();
      const page = await firstValueFrom(service.query('LOCAL', req({ search: '   ' })));
      expect(page).toEqual({ rows: ROWS, total: 99 });
      expect(calls).toHaveLength(1);
    });

    it('treats a missing search as no search', async () => {
      registerLocal();
      const page = await firstValueFrom(service.query('LOCAL', { page: 0, size: 10, searchField: 'name' } as HqLookupFetchRequest));
      expect(page.total).toBe(99);
    });

    it('applies the quick search on the chosen column when the backend cannot', async () => {
      registerLocal();
      const page = await firstValueFrom(service.query('LOCAL', req({ search: '  BETA ', searchField: 'name' })));
      expect(page).toEqual({ rows: [ROWS[1]], total: 1 });
    });

    it('leaves the quick search to a backend that searches itself', async () => {
      registerLocal(true);
      const page = await firstValueFrom(service.query('LOCAL', req({ search: 'beta' })));
      expect(page).toEqual({ rows: ROWS, total: 99 });
    });

    it('still applies advanced filters for a server-search backend', async () => {
      registerLocal(true);
      const page = await firstValueFrom(service.query('LOCAL', req({ search: 'zzz' }), [filter('city', 'EQ', 'chennai')]));
      expect(page.rows.map((r: Row) => r.id)).toEqual([1]);
    });

    it('supports EQ, NE and LIKE on nested paths and missing values', async () => {
      registerLocal();
      const run = (f: HqAdvancedQuery) => firstValueFrom(service.query('LOCAL', req(), [f])).then((p) => p.rows.map((r: Row) => r.id));
      expect(await run(filter('owner.name', 'EQ', ' RAVI '))).toEqual([1]);
      expect(await run(filter('owner.name', 'NE', 'ravi'))).toEqual([2, 3]);
      expect(await run(filter('city', 'LIKE', 'bad'))).toEqual([2]);
      expect(await run(filter('city', 'EQ', ''))).toEqual([1, 2, 3]); // empty values are ignored
      expect(await run({ fieldName: 'city', operator: 'EQ', value: undefined as unknown as string, condition: 'AND' })).toEqual([3]);
    });

    it('combines filters left to right with AND / OR, pre-filters first', async () => {
      registerLocal();
      const ids = (p: HqLookupPage) => p.rows.map((r: Row) => r.id);
      const or = await firstValueFrom(service.query('LOCAL', req(), [filter('name', 'LIKE', 'alpha'), filter('name', 'LIKE', 'gamma', 'OR')]));
      expect(ids(or)).toEqual([1, 3]);
      const and = await firstValueFrom(service.query('LOCAL', req(), [filter('name', 'LIKE', 'a', 'AND')], [filter('city', 'LIKE', 'chen')]));
      expect(ids(and)).toEqual([1]);
    });

    it('ignores filters with no field', async () => {
      registerLocal();
      const page = await firstValueFrom(service.query('LOCAL', req(), [filter('', 'EQ', 'x')]));
      expect(page.total).toBe(99);
    });
  });

  describe('query on an index-backed table', () => {
    it('sends only the paging and the default sort when nothing is filtered', async () => {
      const result = firstValueFrom(service.query('UAM_USERS', req({ page: 2, size: 25, search: '' })));
      const request = expectElastic('user');
      expect(request.request.body).toEqual({
        requestType: 'LIST', searchQuery: undefined, pageNo: 2, pageSize: 25, sort: { field1: 'text1', directionField1: 'ASC' },
      });
      request.flush({ success: true, object: { content: [], totalElements: 0 } });
      expect(await result).toEqual({ rows: [], total: 0 });
    });

    it('sends pre-filters, advanced filters and the quick search as one AND tree', () => {
      service.query(
        'UAM_USERS',
        req({ search: ' ann ', searchField: 'displayName' }),
        [filter('email', 'LIKE', 'x.com'), filter('status', 'EQ', ' ACTIVE ', 'OR'), filter('phone', 'EQ', '9'), filter('email', 'EQ', '')],
        [filter('tenantId', 'EQ', 'default'), filter('status', 'NE', 'DELETED', 'AND')],
      ).subscribe();
      const request = expectElastic('user');
      expect(request.request.body.searchQuery).toEqual({
        group: {
          criteria1: 0,
          group: [
            { criteria1: 0, group: [{ field: 'text5', operator: 'EQ', value: 'default' }, { field: 'text4', operator: 'NE', value: 'DELETED' }] },
            { criteria1: 1, group: [{ field: 'text2', operator: 'LIKE', value: 'x.com' }, { field: 'text4', operator: 'EQ', value: 'ACTIVE' }] },
            { field: 'text6', operator: 'LIKE', value: 'ann' },
          ],
        },
      });
      request.flush({ success: true, object: { content: [] } });
    });

    it('builds nested rows from the slots, skipping empty values, with totals nested under page', async () => {
      service.register({
        id: 'PEOPLE', title: 'People', keyField: 'id', displayField: 'name', defaultSearchField: 'name', columns: [],
        elastic: { model: 'person', slots: { name: 'text1', 'address.city': 'text2', 'address.pin': 'text3', phone: 'text4' }, sortSlot: 'date1' },
      });
      const result = firstValueFrom(service.query('PEOPLE', req()));
      const request = expectElastic('person');
      expect(request.request.body.sort).toEqual({ field1: 'date1', directionField1: 'ASC' });
      request.flush({
        success: true,
        object: { content: [{ modelKey: 'p1', text1: 'Asha', text2: 'Pune', text3: null, text4: '' }], page: { totalElements: 40 } },
      });
      expect(await result).toEqual({ rows: [{ id: 'p1', name: 'Asha', address: { city: 'Pune' } }], total: 40 });
    });

    it('uses the table row builder when given, and the row count when no total is sent', async () => {
      service.register({
        id: 'CUSTOM', title: 'Custom', keyField: 'id', displayField: 'label', defaultSearchField: 'label', columns: [],
        elastic: { model: 'a b', slots: {}, toRow: (d) => ({ id: d['modelKey'], label: `#${d['modelKey']}` }) },
      });
      const result = firstValueFrom(service.query('CUSTOM', req()));
      const request = expectElastic('a%20b');
      expect(request.request.body.sort).toBeUndefined();
      request.flush({ success: true, object: { content: [{ modelKey: 'x' }, { modelKey: 'y' }] } });
      expect(await result).toEqual({ rows: [{ id: 'x', label: '#x' }, { id: 'y', label: '#y' }], total: 2 });
    });

    it('returns an empty page when the index sends no object', async () => {
      const result = firstValueFrom(service.query('UAM_ROLES', req()));
      expectElastic('role').flush({ success: true });
      expect(await result).toEqual({ rows: [], total: 0 });
    });

    it('fails with the first index error, or a generic message', async () => {
      const first = firstValueFrom(service.query('UAM_ROLES', req()));
      expectElastic('role').flush({ success: false, errors: ['Index down', 'other'] });
      await expect(first).rejects.toEqual({ error: { message: 'Index down' } });

      const second = firstValueFrom(service.query('UAM_ROLES', req()));
      expectElastic('role').flush({ success: false });
      await expect(second).rejects.toEqual({ error: { message: 'Lookup failed' } });
    });
  });

  describe('built-in AdminAPI tables', () => {
    it('VENDORS pages through /admin/vendors and filters the quick search locally', async () => {
      const result = firstValueFrom(service.query('VENDORS', req({ page: 1, size: 5, search: 'alp', searchField: 'businessName' })));
      const request = http.expectOne((r) => r.url === `${ADMIN}/admin/vendors`);
      expect(request.request.params.get('page')).toBe('1');
      expect(request.request.params.get('size')).toBe('5');
      expect(request.request.params.has('search')).toBe(false);
      request.flush({ status: true, data: { content: [{ businessName: 'Alpha' }, { businessName: 'Beta' }], totalElements: 2 } });
      expect(await result).toEqual({ rows: [{ businessName: 'Alpha' }], total: 1 });
    });

    it('VEHICLES sends the trimmed quick search to the server', async () => {
      const result = firstValueFrom(service.query('VEHICLES', req({ search: ' TS09 ' })));
      const request = http.expectOne((r) => r.url === `${ADMIN}/admin/vehicles`);
      expect(request.request.params.get('search')).toBe('TS09');
      request.flush({ status: true, data: { content: [{ vehicleNumber: 'TS09AB1234' }], totalElements: 30 } });
      expect(await result).toEqual({ rows: [{ vehicleNumber: 'TS09AB1234' }], total: 30 });
    });

    it('VEHICLES sends no search parameter when there is none', async () => {
      const result = firstValueFrom(service.query('VEHICLES', req()));
      const request = http.expectOne((r) => r.url === `${ADMIN}/admin/vehicles`);
      expect(request.request.params.has('search')).toBe(false);
      request.flush({ status: true });
      expect(await result).toEqual({ rows: [], total: 0 });
    });

    it('fails with the AdminAPI message, or a generic one', async () => {
      const first = firstValueFrom(service.query('VEHICLES', req()));
      http.expectOne((r) => r.url === `${ADMIN}/admin/vehicles`).flush({ status: false, message: 'Not allowed' });
      await expect(first).rejects.toEqual({ error: { message: 'Not allowed' } });

      const second = firstValueFrom(service.query('VEHICLES', req()));
      http.expectOne((r) => r.url === `${ADMIN}/admin/vehicles`).flush({ status: false });
      await expect(second).rejects.toEqual({ error: { message: 'Lookup failed' } });
    });
  });

  describe('resolveDisplayValue', () => {
    it('is null for an empty key, without any request', async () => {
      expect(await firstValueFrom(service.resolveDisplayValue('UAM_ROLES', ''))).toBeNull();
    });

    it('looks an indexed key up directly with an EQ filter', async () => {
      const result = firstValueFrom(service.resolveDisplayValue('UAM_ROLES', 'ADMIN'));
      const request = expectElastic('role');
      expect(request.request.body.pageSize).toBe(1);
      expect(request.request.body.searchQuery).toEqual({
        group: { criteria1: 0, group: [{ field: 'text1', operator: 'EQ', value: 'ADMIN' }] },
      });
      request.flush({ success: true, object: { content: [{ modelKey: 'r1', text1: 'ADMIN', text2: 'Administrator' }] } });
      expect(await result).toEqual({ displayValue: 'ADMIN', element: { id: 'r1', code: 'ADMIN', name: 'Administrator' } });
    });

    it('scans a page when the key has no index slot', async () => {
      const result = firstValueFrom(service.resolveDisplayValue('UAM_USERS', 'u2'));
      const request = expectElastic('user');
      expect(request.request.body.pageSize).toBe(200);
      expect(request.request.body.searchQuery).toBeUndefined();
      request.flush({ success: true, object: { content: [{ modelKey: 'u1', text1: 'ann' }, { modelKey: 'u2', text1: 'bob' }] } });
      expect(await result).toEqual({ displayValue: 'bob', element: { id: 'u2', userName: 'bob' } });
    });

    it('resolves through a client-side table, comparing keys as text', async () => {
      registerLocal();
      expect(await firstValueFrom(service.resolveDisplayValue('LOCAL', '2'))).toEqual({ displayValue: 'Beta Rides', element: ROWS[1] });
    });

    it('shows an empty display value when the row has none', async () => {
      service.register({
        id: 'BLANK', title: 'Blank', keyField: 'id', displayField: 'label', defaultSearchField: 'label', columns: [],
        fetch: () => of({ rows: [{ id: 'k' }], total: 1 }),
      });
      expect(await firstValueFrom(service.resolveDisplayValue('BLANK', 'k'))).toEqual({ displayValue: '', element: { id: 'k' } });
    });

    it('is null when the key is not found or the lookup fails', async () => {
      registerLocal();
      expect(await firstValueFrom(service.resolveDisplayValue('LOCAL', '404'))).toBeNull();
      service.register({
        id: 'BROKEN', title: 'Broken', keyField: 'id', displayField: 'name', defaultSearchField: 'name', columns: [],
        fetch: (): Observable<HqLookupPage> => throwError(() => new Error('down')),
      });
      expect(await firstValueFrom(service.resolveDisplayValue('BROKEN', '1'))).toBeNull();
    });
  });

  it('read() follows dot paths and stops at missing parts', () => {
    expect(service.read({ a: { b: { c: 5 } } }, 'a.b.c')).toBe(5);
    expect(service.read({ a: null }, 'a.b.c')).toBeNull();
    expect(service.read(undefined, 'a')).toBeUndefined();
  });

  describe('uamPage (UAM list helper, not used by any built-in table yet)', () => {
    type UamPage = (path: string, r: HqLookupFetchRequest) => Observable<HqLookupPage>;
    const uamPage = (): UamPage => (service as unknown as { uamPage: UamPage }).uamPage.bind(service);

    it('maps a UAM Spring page envelope to a lookup page', async () => {
      const result = firstValueFrom(uamPage()('/users', req({ page: 3, size: 20 })));
      const request = http.expectOne((r) => r.url === `${UAM}/users`);
      expect(request.request.params.get('page')).toBe('3');
      expect(request.request.params.get('size')).toBe('20');
      request.flush({ success: true, responseObject: { content: [{ id: 1 }], totalElements: 7 } });
      expect(await result).toEqual({ rows: [{ id: 1 }], total: 7 });
    });

    it('returns an empty page when UAM sends none and fails on an error envelope', async () => {
      const empty = firstValueFrom(uamPage()('/users', req()));
      http.expectOne((r) => r.url === `${UAM}/users`).flush({ success: true });
      expect(await empty).toEqual({ rows: [], total: 0 });

      const withMessage = firstValueFrom(uamPage()('/users', req()));
      http.expectOne((r) => r.url === `${UAM}/users`).flush({ success: false, message: 'Denied' });
      await expect(withMessage).rejects.toEqual({ error: { message: 'Denied' } });

      const generic = firstValueFrom(uamPage()('/users', req()));
      http.expectOne((r) => r.url === `${UAM}/users`).flush({ success: false });
      await expect(generic).rejects.toEqual({ error: { message: 'Lookup failed' } });
    });
  });

  describe('filling a form from the picked row', () => {
    const pincodeRow = { pin: '506001', town: 'Hanamkonda', place: { district: 'Warangal' }, state: undefined };

    it('copies each mapped property, reading inside the row by a dotted path and from a function', () => {
      const values = service.mapRow(pincodeRow, {
        pincode: 'pin',
        town: 'town',
        district: 'place.district',
        label: (row) => `${row.pin} - ${row.town}`,
      });

      expect(values).toEqual({ pincode: '506001', town: 'Hanamkonda', district: 'Warangal', label: '506001 - Hanamkonda' });
    });

    it('gives null for a property the row does not have, so an earlier pick is cleared', () => {
      const values = service.mapRow(pincodeRow, { state: 'state', country: 'nothing.here' });

      expect(values).toEqual({ state: null, country: null });
    });

    it('opens the popup and patches the form with what the picked row maps to', async () => {
      dialogOpen.mockReturnValue({ afterClosed: () => of({ lookupTable: 'PINCODES', modelKey: '506001', element: pincodeRow }) });
      const form = { patchValue: vi.fn() };

      const result = await firstValueFrom(service.openLookupInto(form, { lookupTable: 'PINCODES' }, { pincode: 'pin', town: 'town' }));

      expect(form.patchValue).toHaveBeenCalledWith({ pincode: '506001', town: 'Hanamkonda' });
      expect(result?.modelKey).toBe('506001');
    });

    it('leaves the form alone when the popup is dismissed or closed without a pick', async () => {
      const form = { patchValue: vi.fn() };
      dialogOpen.mockReturnValue({ afterClosed: () => of(undefined) });
      await firstValueFrom(service.openLookupInto(form, { lookupTable: 'PINCODES' }, { pincode: 'pin' }));
      dialogOpen.mockReturnValue({ afterClosed: () => of({ lookupTable: 'PINCODES' }) });
      await firstValueFrom(service.openLookupInto(form, { lookupTable: 'PINCODES' }, { pincode: 'pin' }));

      expect(form.patchValue).not.toHaveBeenCalled();
    });
  });
});
