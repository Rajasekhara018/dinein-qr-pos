import { KitchenConfig, KitchenOrderView } from '../../../core/api/models';

export const T0 = Date.parse('2026-09-26T07:00:00Z');
export const MIN = 60_000;

export const testConfig: KitchenConfig = {
  restaurantName: 'Spice Route',
  warnMinutes: 10,
  alertMinutes: 20,
  readyAutoHideMinutes: 5,
};

let seq = 0;

/** Kitchen order paid `paidMinutesAgo` before T0 (defaults to 1). */
export function kOrder(
  overrides: Partial<KitchenOrderView> & { id: number },
  paidMinutesAgo = 1,
): KitchenOrderView {
  seq++;
  return {
    orderNumber: `260926-${String(overrides.id).padStart(3, '0')}`,
    displayToken: overrides.id,
    status: 'CONFIRMED',
    tableLabel: `T${(seq % 9) + 1}`,
    items: [{ name: 'Masala Dosa', foodType: 'VEG', addons: [], quantity: 2 }],
    paidAt: new Date(T0 - paidMinutesAgo * MIN).toISOString(),
    ...overrides,
  };
}
