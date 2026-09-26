import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { KitchenOrderView } from '../../../core/api/models';
import { KitchenModule } from '../kitchen-module';
import { kOrder, MIN, T0, testConfig } from '../data/test-fixtures';
import { TicketCard } from './ticket-card';

describe('TicketCard', () => {
  let fixture: ComponentFixture<TicketCard>;
  let el: HTMLElement;

  beforeEach(() => {
    // Import the declaring NgModule; don't re-declare the component.
    TestBed.configureTestingModule({ imports: [KitchenModule] });
    fixture = TestBed.createComponent(TicketCard);
    el = fixture.nativeElement;
  });

  function render(
    order: KitchenOrderView,
    now = T0,
    extra: { pending?: boolean; highlighted?: boolean } = {},
  ) {
    fixture.componentRef.setInput('order', order);
    fixture.componentRef.setInput('now', now);
    fixture.componentRef.setInput('config', testConfig);
    fixture.componentRef.setInput('pending', extra.pending ?? false);
    fixture.componentRef.setInput('highlighted', extra.highlighted ?? false);
    fixture.detectChanges();
  }

  const button = () => el.querySelector<HTMLButtonElement>('[data-testid^="ticket-action-"]');
  const article = () => el.querySelector('article')!;

  it('shows token, table, items, modifiers and notes', () => {
    render(
      kOrder({
        id: 42,
        tableLabel: 'T3',
        notes: 'Less spicy please',
        items: [
          {
            name: 'Chicken Biryani',
            variantName: 'Full',
            foodType: 'NON_VEG',
            addons: ['Extra raita'],
            quantity: 3,
            notes: 'No onion',
          },
        ],
      }),
    );
    expect(el.querySelector('[data-testid="ticket-token"]')!.textContent).toContain('#42');
    expect(el.textContent).toContain('T3');
    expect(el.querySelector('.ticket-qty')!.textContent).toContain('3');
    expect(el.querySelector('.ticket-mod--variant')!.textContent).toContain('Full');
    expect(el.querySelector('.ticket-mod--addon')!.textContent).toContain('Extra raita');
    expect(el.textContent).toContain('No onion');
    expect(el.textContent).toContain('Less spicy please');
    expect(el.querySelector('app-veg-marker')).not.toBeNull();
  });

  it.each([
    ['CONFIRMED', 'Start'],
    ['PREPARING', 'Ready'],
    ['READY', 'Served'],
  ] as const)('%s shows the %s button', (status, label) => {
    render(kOrder({ id: 1, status, readyAt: new Date(T0).toISOString() }));
    expect(button()!.textContent!.trim()).toBe(label);
  });

  it('emits advance on click and disables the button while pending', () => {
    const order = kOrder({ id: 1 });
    const emitted: KitchenOrderView[] = [];
    fixture.componentInstance.advance.subscribe((o) => emitted.push(o));
    render(order);
    button()!.click();
    expect(emitted).toEqual([order]);

    render(order, T0, { pending: true });
    expect(button()!.disabled).toBe(true);
    expect(button()!.getAttribute('aria-busy')).toBe('true');
  });

  it('updates the elapsed time from the shared ticker', () => {
    render(kOrder({ id: 1 }, 0), T0 + 65_000);
    expect(el.querySelector('.ticket-elapsed')!.textContent).toContain('1:05');
    render(kOrder({ id: 1 }, 0), T0 + 125_000);
    expect(el.querySelector('.ticket-elapsed')!.textContent).toContain('2:05');
  });

  it('escalates the age class with a text cue (amber → red)', () => {
    const order = kOrder({ id: 1 }, 0);
    render(order, T0 + 5 * MIN);
    expect(article().classList).toContain('ticket--ok');
    expect(el.querySelector('[data-testid="ticket-age"]')).toBeNull();

    render(order, T0 + 11 * MIN);
    expect(article().classList).toContain('ticket--warn');
    expect(el.querySelector('[data-testid="ticket-age"]')!.textContent).toContain('Late');

    render(order, T0 + 21 * MIN);
    expect(article().classList).toContain('ticket--alert');
    expect(el.querySelector('[data-testid="ticket-age"]')!.textContent).toContain('Overdue');
  });

  it('marks new orders', () => {
    render(kOrder({ id: 1 }), T0, { highlighted: true });
    expect(article().classList).toContain('ticket--new');
    expect(el.textContent).toContain('New');
  });
});
