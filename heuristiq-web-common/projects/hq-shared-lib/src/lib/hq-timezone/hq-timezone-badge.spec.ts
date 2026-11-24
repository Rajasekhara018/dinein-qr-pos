import { TestBed } from '@angular/core/testing';
import { HqTimezoneBadge } from './hq-timezone-badge';
import { HqTimezoneService } from './hq-timezone.service';

describe('HqTimezoneBadge', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  function render(timezone?: string | null) {
    const fixture = TestBed.createComponent(HqTimezoneBadge);
    if (timezone !== undefined) fixture.componentRef.setInput('timezone', timezone);
    fixture.detectChanges();
    const badge = () => (fixture.nativeElement as HTMLElement).querySelector('.hq-timezone-badge')!;
    return { fixture, badge };
  }

  it('describes the given zone as name and offset, also in the title', () => {
    const service = TestBed.inject(HqTimezoneService);
    const { badge } = render('Asia/Kolkata');
    expect(badge().textContent).toBe(service.describe('Asia/Kolkata'));
    expect(badge().textContent).toContain('Asia/Kolkata');
    expect(badge().textContent).toContain('+05:30');
    expect(badge().getAttribute('title')).toBe(badge().textContent);
  });

  it('shows the effective timezone without an input and follows a change of it', async () => {
    const service = TestBed.inject(HqTimezoneService);
    const { fixture, badge } = render(null);
    expect(badge().textContent).toBe(service.describe(service.effectiveTimezone()));

    service.setUserTimezone('Asia/Dubai');
    await fixture.whenStable();
    fixture.detectChanges();
    expect(badge().textContent).toBe(service.describe('Asia/Dubai'));
  });
});
