import { TestBed } from '@angular/core/testing';
import { HqPrivilegeAccessService } from './hq-privilege-access.service';
import { HQ_PRIVILEGES_CHANGED_EVENT, HQ_PRIVILEGES_STORAGE_KEY } from './hq-privilege.constants';

function savePrivileges(permissions: unknown): void {
  localStorage.setItem(HQ_PRIVILEGES_STORAGE_KEY, JSON.stringify({ permissions }));
}

describe('HqPrivilegeAccessService', () => {
  let service: HqPrivilegeAccessService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
    service = TestBed.inject(HqPrivilegeAccessService);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  describe('granted()', () => {
    it('is null when nothing is saved', () => {
      expect(service.granted()).toBeNull();
    });

    it('returns the saved permission codes as a set', () => {
      savePrivileges(['A', 'B']);
      const granted = service.granted();
      expect(granted).not.toBeNull();
      expect([...(granted ?? [])]).toEqual(['A', 'B']);
    });

    it('is null when the saved value has no permissions array', () => {
      localStorage.setItem(HQ_PRIVILEGES_STORAGE_KEY, JSON.stringify({ permissions: 'A' }));
      expect(service.granted()).toBeNull();
      localStorage.setItem(HQ_PRIVILEGES_STORAGE_KEY, 'null');
      expect(service.granted()).toBeNull();
    });

    it('is null when the saved value is not valid JSON', () => {
      localStorage.setItem(HQ_PRIVILEGES_STORAGE_KEY, '{not json');
      expect(service.granted()).toBeNull();
    });

    it('is null when storage cannot be read', () => {
      vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
        throw new Error('blocked');
      });
      expect(service.granted()).toBeNull();
    });
  });

  describe('can()', () => {
    it('allows everything when no codes are asked for', () => {
      savePrivileges([]);
      expect(service.can([])).toBe(true);
      expect(service.can(null)).toBe(true);
      expect(service.can(undefined)).toBe(true);
    });

    it('allows everything when no privileges are saved (the gateway stays the gate)', () => {
      expect(service.can('ANY_CODE')).toBe(true);
      expect(service.can(['X', 'Y'])).toBe(true);
    });

    it('checks a single code against the saved privileges', () => {
      savePrivileges(['ADMIN_RIDE_CANCEL']);
      expect(service.can('ADMIN_RIDE_CANCEL')).toBe(true);
      expect(service.can('ADMIN_RIDE_LIST')).toBe(false);
    });

    it('allows when any one of several codes is held', () => {
      savePrivileges(['ADMIN_PAYOUT_READ']);
      expect(service.can(['ADMIN_PAYOUT_LIST', 'ADMIN_PAYOUT_READ'])).toBe(true);
      expect(service.can(['ADMIN_PAYOUT_LIST', 'ADMIN_PAYOUT_UPDATE'])).toBe(false);
    });

    it('denies everything when an empty privilege list is saved', () => {
      savePrivileges([]);
      expect(service.can('ANY_CODE')).toBe(false);
    });
  });

  describe('canOpen()', () => {
    const mapping = { view_rides: ['ADMIN_RIDE_LIST', 'ADMIN_RIDES_MANAGE'] } as const;

    it('is null when the permission key has no mapping', () => {
      savePrivileges(['ADMIN_RIDE_LIST']);
      expect(service.canOpen('unknown_key', mapping)).toBeNull();
    });

    it('is null when no privileges are saved', () => {
      expect(service.canOpen('view_rides', mapping)).toBeNull();
    });

    it('is true when any mapped privilege is held and false otherwise', () => {
      savePrivileges(['ADMIN_RIDES_MANAGE']);
      expect(service.canOpen('view_rides', mapping)).toBe(true);
      savePrivileges(['SOMETHING_ELSE']);
      expect(service.canOpen('view_rides', mapping)).toBe(false);
    });
  });

  it('changes$ emits whenever the privileges-changed event fires on window', () => {
    const seen: unknown[] = [];
    const sub = service.changes$.subscribe((v) => seen.push(v));
    window.dispatchEvent(new Event(HQ_PRIVILEGES_CHANGED_EVENT));
    window.dispatchEvent(new Event('some-other-event'));
    window.dispatchEvent(new Event(HQ_PRIVILEGES_CHANGED_EVENT));
    sub.unsubscribe();
    window.dispatchEvent(new Event(HQ_PRIVILEGES_CHANGED_EVENT));
    expect(seen).toEqual([undefined, undefined]);
  });
});
