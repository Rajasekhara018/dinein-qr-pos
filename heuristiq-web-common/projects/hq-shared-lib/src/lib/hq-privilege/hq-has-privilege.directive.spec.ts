import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HqHasPrivilege } from './hq-has-privilege.directive';
import { HqPrivilegeAccessService } from './hq-privilege-access.service';
import { HQ_PRIVILEGES_CHANGED_EVENT, HQ_PRIVILEGES_STORAGE_KEY } from './hq-privilege.constants';

@Component({
  standalone: false,
  template: `<button *hqHasPrivilege="codes()" class="guarded">Cancel ride</button><span class="always">x</span>`,
})
class HostComponent {
  readonly codes = signal<string | readonly string[]>('ADMIN_RIDE_CANCEL');
}

function savePrivileges(permissions: string[]): void {
  localStorage.setItem(HQ_PRIVILEGES_STORAGE_KEY, JSON.stringify({ permissions }));
}

function notifyChanged(): void {
  window.dispatchEvent(new Event(HQ_PRIVILEGES_CHANGED_EVENT));
}

describe('HqHasPrivilege', () => {
  let fixture: ComponentFixture<HostComponent>;

  function create(codes?: string | readonly string[]): HTMLElement {
    TestBed.configureTestingModule({ declarations: [HostComponent, HqHasPrivilege] });
    fixture = TestBed.createComponent(HostComponent);
    if (codes !== undefined) fixture.componentInstance.codes.set(codes);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const guardedCount = (el: HTMLElement) => el.querySelectorAll('button.guarded').length;

  beforeEach(() => localStorage.clear());
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('shows the element when the user holds the privilege', () => {
    savePrivileges(['ADMIN_RIDE_CANCEL']);
    const el = create();
    expect(guardedCount(el)).toBe(1);
    expect(el.querySelector('.always')).not.toBeNull();
  });

  it('hides the element when the user lacks the privilege', () => {
    savePrivileges(['ADMIN_RIDE_LIST']);
    const el = create();
    expect(guardedCount(el)).toBe(0);
    expect(el.querySelector('.always')).not.toBeNull();
  });

  it('shows the element when any one of several codes is held', () => {
    savePrivileges(['ADMIN_PAYOUT_READ']);
    expect(guardedCount(create(['ADMIN_PAYOUT_LIST', 'ADMIN_PAYOUT_READ']))).toBe(1);
  });

  it('re-renders when the bound codes change, without duplicating the view', () => {
    savePrivileges(['A']);
    const el = create('A');
    expect(guardedCount(el)).toBe(1);

    fixture.componentInstance.codes.set(['A', 'B']);
    fixture.detectChanges();
    expect(guardedCount(el)).toBe(1);

    fixture.componentInstance.codes.set('B');
    fixture.detectChanges();
    expect(guardedCount(el)).toBe(0);

    fixture.componentInstance.codes.set(['C']);
    fixture.detectChanges();
    expect(guardedCount(el)).toBe(0);

    fixture.componentInstance.codes.set('A');
    fixture.detectChanges();
    expect(guardedCount(el)).toBe(1);
  });

  it('re-checks when the saved privileges change (sign-in, refresh, sign-out)', () => {
    savePrivileges([]);
    const el = create();
    expect(guardedCount(el)).toBe(0);

    savePrivileges(['ADMIN_RIDE_CANCEL']);
    notifyChanged();
    expect(guardedCount(el)).toBe(1);

    savePrivileges(['OTHER']);
    notifyChanged();
    expect(guardedCount(el)).toBe(0);
  });

  it('stops listening for changes once destroyed', () => {
    savePrivileges([]);
    create();
    const access = TestBed.inject(HqPrivilegeAccessService);
    const can = vi.spyOn(access, 'can');
    fixture.destroy();
    notifyChanged();
    expect(can).not.toHaveBeenCalled();
  });
});
