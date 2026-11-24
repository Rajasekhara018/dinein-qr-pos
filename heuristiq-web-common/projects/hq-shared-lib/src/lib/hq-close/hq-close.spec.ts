import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { HqCloseDialogData } from '../hq-common.models';
import { HqClose } from './hq-close';

describe('HqClose', () => {
  let fixture: ComponentFixture<HqClose>;
  let close: ReturnType<typeof vi.fn>;

  function render(data: HqCloseDialogData): HTMLElement {
    close = vi.fn();
    TestBed.configureTestingModule({
      declarations: [HqClose],
      providers: [
        { provide: MatDialogRef, useValue: { close } },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    });
    fixture = TestBed.createComponent(HqClose);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const buttons = (el: HTMLElement) => Array.from(el.querySelectorAll('button'));
  const text = (el: HTMLElement, selector: string) => el.querySelector(selector)?.textContent?.trim();

  describe('confirmation', () => {
    it('shows the title, message and Yes/No buttons', () => {
      const el = render({ confirm: true, title: 'Delete?', message: 'This cannot be undone.' });
      expect(text(el, '.hq-close__title')).toBe('Delete?');
      expect(text(el, '.hq-close__message')).toBe('This cannot be undone.');
      expect(buttons(el).map((b) => b.textContent?.trim())).toEqual(['No', 'Yes']);
    });

    it('omits the title when none is given', () => {
      const el = render({ confirm: true, message: 'Sure?' });
      expect(el.querySelector('.hq-close__title')).toBeNull();
      expect(text(el, '.hq-close__message')).toBe('Sure?');
    });

    it('closes with true on Yes and false on No', () => {
      const el = render({ confirm: true, message: 'Sure?' });
      const [no, yes] = buttons(el);
      yes.click();
      expect(close).toHaveBeenLastCalledWith(true);
      no.click();
      expect(close).toHaveBeenLastCalledWith(false);
      expect(close).toHaveBeenCalledTimes(2);
    });
  });

  describe('acknowledgement', () => {
    it('shows the given title and message with a single Close button', () => {
      const el = render({ confirm: false, title: 'Saved', message: 'All done.' });
      expect(el.querySelector('.hq-close--center')).not.toBeNull();
      expect(text(el, '.hq-close__title--success')).toBe('Saved');
      expect(text(el, '.hq-close__message')).toBe('All done.');
      expect(buttons(el).map((b) => b.textContent?.trim())).toEqual(['Close']);
    });

    it('falls back to "Thank you!" and hides an empty message', () => {
      const el = render({ confirm: false, message: '' });
      expect(text(el, '.hq-close__title--success')).toBe('Thank you!');
      expect(el.querySelector('.hq-close__message')).toBeNull();
    });

    it('closes with false on Close', () => {
      const el = render({ confirm: false, message: 'Done' });
      buttons(el)[0].click();
      expect(close).toHaveBeenCalledOnce();
      expect(close).toHaveBeenCalledWith(false);
    });
  });
});
