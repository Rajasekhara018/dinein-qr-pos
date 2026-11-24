import { TestBed } from '@angular/core/testing';
import { HqSharedLibModule } from '../../hq-shared-lib.module';
import { HqStatCardItem, HqStatCards } from './hq-stat-cards';

describe('HqStatCards', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [HqSharedLibModule] }));

  function render(stats?: HqStatCardItem[]): HTMLElement {
    const fixture = TestBed.createComponent(HqStatCards);
    if (stats) fixture.componentRef.setInput('stats', stats);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('keeps the fixed-width row unless asked to fill the row or to sit flush beside another card', () => {
    const fixture = TestBed.createComponent(HqStatCards);
    fixture.componentRef.setInput('stats', [{ label: 'Total', value: 1, icon: 'bi-people', tone: 'blue' }]);
    fixture.detectChanges();
    const row = (): HTMLElement => fixture.nativeElement.querySelector('.stat-cards');
    expect(row().classList).not.toContain('stat-cards--fill');
    expect(row().classList).not.toContain('stat-cards--flush');

    fixture.componentRef.setInput('fill', true);
    fixture.componentRef.setInput('flush', true);
    fixture.detectChanges();
    expect(row().classList).toContain('stat-cards--fill');
    expect(row().classList).toContain('stat-cards--flush');
  });

  it('renders nothing when there are no stats', () => {
    expect(render().querySelector('.stat-cards')).toBeNull();
    expect(render([]).querySelector('.stat-cards')).toBeNull();
  });

  it('renders one card per stat with label, value, tone and icon', () => {
    const el = render([
      { label: 'Total', value: 134, icon: 'bi-people-fill', tone: 'blue' },
      { label: 'Active', value: '12', icon: 'bi-check', tone: 'green' },
    ]);
    const cards = el.querySelectorAll('.stat-card');
    expect(cards.length).toBe(2);
    expect(cards[0].querySelector('.stat-label')?.textContent).toBe('Total');
    expect(cards[0].querySelector('.stat-value')?.textContent).toBe('134');
    expect(cards[0].querySelector('.stat-icon')?.classList).toContain('stat-icon--blue');
    expect(cards[0].querySelector('.stat-icon i')?.classList).toContain('bi-people-fill');
    expect(cards[1].querySelector('.stat-icon')?.classList).toContain('stat-icon--green');
    expect(cards[1].querySelector('.stat-value')?.textContent).toBe('12');
  });
});
