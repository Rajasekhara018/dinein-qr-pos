import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HqSharedLibModule } from '../../hq-shared-lib.module';
import { HqToastService } from '../hq-toast.service';
import { HqToastHost } from './hq-toast-host';

describe('HqToastHost', () => {
  let fixture: ComponentFixture<HqToastHost>;
  let service: HqToastService;
  let el: HTMLElement;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({ imports: [HqSharedLibModule] });
    service = TestBed.inject(HqToastService);
    fixture = TestBed.createComponent(HqToastHost);
    el = fixture.nativeElement;
    fixture.detectChanges();
  });

  afterEach(() => vi.useRealTimers());

  const items = () => Array.from(el.querySelectorAll<HTMLElement>('.toast-item'));

  it('renders nothing until a toast is raised', () => {
    expect(items().length).toBe(0);
    expect(el.querySelector('.toast-stack')?.getAttribute('aria-live')).toBe('polite');
  });

  it('renders each toast with its type class, icon, message and progress duration', () => {
    service.add('success', 'Saved', 3000);
    service.add('error', 'Failed', 5000);
    service.add('warning', 'Careful', 4000);
    service.add('info', 'FYI', 2000);
    fixture.detectChanges();

    const shown = items();
    expect(shown.map((i) => i.querySelector('.toast-message')?.textContent?.trim())).toEqual(['Saved', 'Failed', 'Careful', 'FYI']);
    expect(shown[0].classList).toContain('toast-item--success');
    expect(shown[0].querySelector('i')?.className).toContain('bi-check-circle-fill');
    expect(shown[1].querySelector('i')?.className).toContain('bi-x-circle-fill');
    expect(shown[2].querySelector('i')?.className).toContain('bi-exclamation-triangle-fill');
    expect(shown[3].querySelector('i')?.className).toContain('bi-info-circle-fill');
    expect(shown[1].querySelector<HTMLElement>('.toast-progress')?.style.animationDuration).toBe('5000ms');
    expect(shown[0].getAttribute('role')).toBe('alert');
  });

  it('dismisses a toast when its close button is clicked, labelled with dismissLabel', () => {
    fixture.componentRef.setInput('dismissLabel', 'Close it');
    service.add('info', 'One', 9000);
    service.add('info', 'Two', 9000);
    fixture.detectChanges();

    const close = items()[0].querySelector<HTMLButtonElement>('.toast-close')!;
    expect(close.getAttribute('aria-label')).toBe('Close it');
    close.click();
    fixture.detectChanges();
    expect(items().map((i) => i.textContent?.trim())).toEqual(['Two']);
  });

  it('defaults the dismiss label to English and drops toasts when their time runs out', () => {
    service.add('success', 'Bye', 1000);
    fixture.detectChanges();
    expect(items()[0].querySelector('.toast-close')?.getAttribute('aria-label')).toBe('Dismiss');
    vi.advanceTimersByTime(1000);
    fixture.detectChanges();
    expect(items().length).toBe(0);
  });

  it('tracks toasts by id', () => {
    expect(fixture.componentInstance.trackById(0, { id: 7, type: 'info', message: '', duration: 1 })).toBe(7);
  });
});
