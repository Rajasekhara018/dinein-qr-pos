import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HqSharedLibModule } from '../../hq-shared-lib.module';
import { HqTooltip } from './hq-tooltip';
import { HqTooltipPosition } from './hq-tooltip.constants';

@Component({
  standalone: true,
  imports: [HqSharedLibModule],
  template: `<button type="button" [hqTooltip]="text" [hqTooltipPosition]="position" [hqTooltipDisabled]="off"
                     [attr.aria-label]="label">x</button>`,
})
class Host {
  text: string | null = 'Archive the order';
  position: HqTooltipPosition = 'top';
  off = false;
  label: string | null = null;
}

describe('HqTooltip', () => {
  let fixture: ComponentFixture<Host>;
  let tooltip: HqTooltip;
  let button: HTMLButtonElement;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [Host] });
    fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    button = fixture.nativeElement.querySelector('button');
    tooltip = fixture.debugElement.children[0].injector.get(HqTooltip);
  });

  afterEach(() => tooltip.hide(0));

  const bubble = () => document.querySelector('.hq-tooltip');

  it('shows the text in an overlay and hides it again', () => {
    tooltip.show(0);
    expect(bubble()?.textContent?.trim()).toBe('Archive the order');
    expect(document.querySelector('.hq-tooltip-pane')).not.toBeNull();
    tooltip.hide(0);
    expect(bubble()).toBeNull();
  });

  it('shows nothing for empty text (never the word "null")', () => {
    fixture.componentInstance.text = null;
    fixture.detectChanges();
    tooltip.show(0);
    expect(bubble()).toBeNull();
    fixture.componentInstance.text = '   ';
    fixture.detectChanges();
    tooltip.show(0);
    expect(bubble()).toBeNull();
  });

  it('hides when the text is cleared or the tooltip is disabled while it is open', () => {
    tooltip.show(0);
    fixture.componentInstance.text = '';
    fixture.detectChanges();
    expect(bubble()).toBeNull();

    fixture.componentInstance.text = 'Again';
    fixture.detectChanges();
    tooltip.show(0);
    fixture.componentInstance.off = true;
    fixture.detectChanges();
    expect(bubble()).toBeNull();
    tooltip.show(0);
    expect(bubble()).toBeNull();
  });

  it('updates the text while it is open', () => {
    tooltip.show(0);
    fixture.componentInstance.text = 'Restore the order';
    fixture.detectChanges();
    expect(bubble()?.textContent?.trim()).toBe('Restore the order');
  });

  it('hides on Escape and on a click', () => {
    tooltip.show(0);
    button.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(bubble()).toBeNull();
    tooltip.show(0);
    button.dispatchEvent(new MouseEvent('mousedown'));
    expect(bubble()).toBeNull();
  });

  it('describes the element for screen readers unless it repeats the element name', () => {
    expect(button.getAttribute('aria-describedby')).toBeTruthy();
    fixture.componentInstance.label = 'Archive the order';
    fixture.detectChanges();
    fixture.componentInstance.text = 'Archive the order ';
    fixture.detectChanges();
    expect(button.getAttribute('aria-describedby')).toBeFalsy();
  });
});
