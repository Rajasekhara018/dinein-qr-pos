import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { HqSharedLibModule } from '../../hq-shared-lib.module';
import { BehaviorSubject } from 'rxjs';
import { HqApplication } from '../hq-application.model';
import { HqApplicationsService } from '../hq-applications.service';
import { HqApplicationSelect } from './hq-application-select';

const RIDER: HqApplication = { code: 'BESTRIDE_RIDER_APP', name: 'BestRide Rider', type: 'MOBILE', displayOrder: 20 };
const SHOP: HqApplication = { code: 'BEST_PURCHASE_APP', name: 'Best Purchase', type: 'WEB', displayOrder: 40 };
const WALLET: HqApplication = { code: 'CENTRALISED_WALLET_APP', name: 'Centralised Wallet', type: 'WEB', displayOrder: 50 };

@Component({
  imports: [HqSharedLibModule, ReactiveFormsModule],
  template: `<hq-application-select [formControl]="control" label="Application" placeholder="Pick one" [required]="true"
               emptyText="No applications" errorText="Could not load" [onlyCodes]="only" [excludeCodes]="exclude"
               (applicationChange)="picked = $event"></hq-application-select>`,
})
class HostComponent {
  control = new FormControl<string | null>('');
  only: string[] | null = null;
  exclude: string[] | null = null;
  picked: HqApplication | null | undefined = undefined;
}

describe('HqApplicationSelect', () => {
  let list$: BehaviorSubject<HqApplication[]>;
  let failed: ReturnType<typeof signal<boolean>>;
  let fixture: ComponentFixture<HostComponent>;
  let host: HostComponent;

  function setUp(emit = true): void {
    TestBed.resetTestingModule();
    list$ = new BehaviorSubject<HqApplication[]>([RIDER, SHOP, WALLET]);
    failed = signal(false);
    const service = { applications$: emit ? list$.asObservable() : new BehaviorSubject<HqApplication[]>([]).pipe(), loadFailed: failed };
    TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [{ provide: HqApplicationsService, useValue: service }],
    });
    fixture = TestBed.createComponent(HostComponent);
    host = fixture.componentInstance;
    refresh();
  }

  /** Zoneless: plain field changes are not notified, so mark the host dirty before checking. */
  function refresh(): void {
    fixture.componentRef.changeDetectorRef.markForCheck();
    fixture.detectChanges();
  }

  const select = () => fixture.nativeElement.querySelector('select') as HTMLSelectElement;
  const optionTexts = () => Array.from(select().options).map((o) => o.textContent?.trim());
  const component = () => fixture.debugElement.children[0].componentInstance as HqApplicationSelect;
  const message = () => (fixture.nativeElement.querySelector('.application-select__message') as HTMLElement | null);

  beforeEach(() => setUp());

  it('shows the label, the required mark and every application as "Name (CODE)" after the placeholder', () => {
    const label = fixture.nativeElement.querySelector('label') as HTMLLabelElement;

    expect(label.textContent).toContain('Application');
    expect(label.textContent).toContain('*');
    expect(label.htmlFor).toBe(select().id);
    expect(select().required).toBe(true);
    expect(optionTexts()).toEqual(['Pick one', 'BestRide Rider (BESTRIDE_RIDER_APP)', 'Best Purchase (BEST_PURCHASE_APP)',
      'Centralised Wallet (CENTRALISED_WALLET_APP)']);
    expect(message()).toBeNull();
  });

  it('picking an option sets the form value to the code and emits the application; the placeholder clears it', () => {
    select().value = 'BEST_PURCHASE_APP';
    select().dispatchEvent(new Event('change'));

    expect(host.control.value).toBe('BEST_PURCHASE_APP');
    expect(host.picked).toEqual(SHOP);

    select().value = '';
    select().dispatchEvent(new Event('change'));
    expect(host.control.value).toBe('');
    expect(host.picked).toBeNull();
  });

  it('marks the control touched on blur', () => {
    select().dispatchEvent(new Event('blur'));

    expect(host.control.touched).toBe(true);
  });

  it('shows a value written by the form, and keeps an unknown value visible by its code', () => {
    host.control.setValue('CENTRALISED_WALLET_APP');
    refresh();
    expect(select().value).toBe('CENTRALISED_WALLET_APP');

    host.control.setValue('OLD_APP');
    refresh();
    expect(select().value).toBe('OLD_APP');
    expect(optionTexts()).toContain('OLD_APP');

    host.control.setValue(null);
    refresh();
    expect(component().value).toBe('');
    expect(component().valueOutsideOptions).toBe(false);
  });

  it('onlyCodes and excludeCodes narrow the choices', () => {
    host.only = ['BESTRIDE_RIDER_APP', 'BEST_PURCHASE_APP'];
    host.exclude = ['BEST_PURCHASE_APP'];
    refresh();

    expect(optionTexts()).toEqual(['Pick one', 'BestRide Rider (BESTRIDE_RIDER_APP)']);
  });

  it('follows changes to the list', () => {
    list$.next([WALLET]);
    refresh();

    expect(optionTexts()).toEqual(['Pick one', 'Centralised Wallet (CENTRALISED_WALLET_APP)']);
  });

  it('shows the empty text when nothing can be offered', () => {
    host.exclude = [RIDER.code, SHOP.code, WALLET.code];
    refresh();

    expect(message()?.textContent).toContain('No applications');
    expect(message()?.classList.contains('is-error')).toBe(false);
  });

  it('shows the error text when the list could not be loaded and none is known', () => {
    failed.set(true);
    list$.next([]);
    refresh();

    expect(message()?.textContent).toContain('Could not load');
    expect(message()?.classList.contains('is-error')).toBe(true);
    expect(select().getAttribute('aria-invalid')).toBe('true');
  });

  it('after a failed refresh with a known list it keeps offering it without an error', () => {
    failed.set(true);
    refresh();

    expect(message()).toBeNull();
    expect(optionTexts().length).toBe(4);
  });

  it('shows nothing under the field before the list arrives', () => {
    setUp(false);
    expect(component().message).toBe('No applications');
    component().loaded = false;
    expect(component().message).toBe('');
  });

  it('is disabled by the form and enabled again', () => {
    host.control.disable();
    refresh();
    expect(select().disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('.application-select').classList.contains('is-disabled')).toBe(true);

    host.control.enable();
    refresh();
    expect(select().disabled).toBe(false);
  });

  it('stops following the list when destroyed', () => {
    const instance = component();
    fixture.destroy();
    list$.next([]);

    expect(instance.all.length).toBe(3);
  });

  it('works without a form and without a label', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [HqSharedLibModule],
      providers: [{ provide: HqApplicationsService, useValue: { applications$: list$.asObservable(), loadFailed: signal(false) } }],
    });
    const bare = TestBed.createComponent(HqApplicationSelect);
    bare.detectChanges();

    expect(bare.nativeElement.querySelector('label')).toBeNull();
    expect(bare.componentInstance.inputId).toMatch(/^hq-application-select-\d+$/);
    bare.componentInstance.select('BESTRIDE_RIDER_APP');
    bare.componentInstance.touch();
    expect(bare.componentInstance.value).toBe('BESTRIDE_RIDER_APP');
  });
});
