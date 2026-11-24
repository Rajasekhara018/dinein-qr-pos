import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { HqSharedLibModule } from '../../hq-shared-lib.module';
import { HqSlideToggle } from './hq-slide-toggle';

@Component({
  standalone: true,
  imports: [HqSharedLibModule, ReactiveFormsModule],
  template: '<hq-slide-toggle [formControl]="control" [loading]="loading" ariaLabel="Notifications">Notifications</hq-slide-toggle>',
})
class Host {
  control = new FormControl(false, { nonNullable: true, validators: Validators.requiredTrue });
  loading = false;
}

@Component({
  standalone: true,
  imports: [HqSharedLibModule, FormsModule],
  template: '<hq-slide-toggle [(ngModel)]="enabled">Enabled</hq-slide-toggle>',
})
class ModelHost { enabled = false; }

describe('HqSlideToggle', () => {
  function setup() {
    TestBed.configureTestingModule({ imports: [Host] });
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const toggle = fixture.debugElement.query(By.directive(HqSlideToggle)).componentInstance as HqSlideToggle;
    const button = fixture.nativeElement.querySelector('button[role="switch"]') as HTMLButtonElement;
    return { fixture, host: fixture.componentInstance, toggle, button };
  }

  it('updates the reactive form and checkedChange once, and marks touched on blur', () => {
    const { fixture, host, toggle, button } = setup();
    const changed = vi.fn();
    toggle.checkedChange.subscribe(changed);
    expect(host.control.invalid).toBe(true);
    button.click();
    fixture.detectChanges();
    expect(host.control.value).toBe(true);
    expect(host.control.valid).toBe(true);
    expect(changed).toHaveBeenCalledExactlyOnceWith(true);
    button.dispatchEvent(new FocusEvent('focusout', { bubbles: true }));
    expect(host.control.touched).toBe(true);
  });

  it('renders programmatic changes without emitting a user change and supports reset', () => {
    const { fixture, host, toggle, button } = setup();
    const changed = vi.fn();
    toggle.checkedChange.subscribe(changed);
    host.control.setValue(true);
    fixture.detectChanges();
    expect(button.getAttribute('aria-checked')).toBe('true');
    expect(changed).not.toHaveBeenCalled();
    host.control.reset();
    fixture.detectChanges();
    expect(toggle.checked).toBe(false);
  });

  it('blocks interaction when disabled by the form or loading, and restores it afterwards', () => {
    const { fixture, host, button } = setup();
    host.control.disable();
    fixture.detectChanges();
    expect(button.disabled).toBe(true);
    button.click();
    expect(host.control.value).toBe(false);
    host.control.enable();
    host.loading = true;
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    expect(button.disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('[aria-busy="true"]')).not.toBeNull();
    host.loading = false;
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    button.click();
    expect(host.control.value).toBe(true);
  });

  it('forwards accessible labels and uses the Material switch semantics', () => {
    const { button } = setup();
    expect(button.getAttribute('aria-label')).toBe('Notifications');
    expect(button.getAttribute('aria-checked')).toBe('false');
  });

  it('supports template-driven forms', async () => {
    TestBed.configureTestingModule({ imports: [ModelHost] });
    const fixture = TestBed.createComponent(ModelHost);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.nativeElement.querySelector('button[role="switch"]').click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.componentInstance.enabled).toBe(true);
    fixture.componentInstance.enabled = false;
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('button[role="switch"]').getAttribute('aria-checked')).toBe('false');
  });
});
