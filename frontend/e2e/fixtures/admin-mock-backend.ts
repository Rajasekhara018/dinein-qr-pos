import { Page, Route } from '@playwright/test';

/** Canned admin backend (shapes mirror the Spring Boot DTOs), stateful where the tests need it. */

type Json = Record<string, unknown>;

export interface AdminMockState {
  loggedIn: boolean;
  mustChangePassword: boolean;
  items: Json[];
  requests: { method: string; path: string; body: unknown }[];
}

const owner = (mustChangePassword: boolean) => ({
  id: 1,
  username: 'owner',
  displayName: 'Asha Owner',
  role: 'OWNER',
  mustChangePassword,
});

const token = (mustChangePassword: boolean) => ({
  accessToken: `jwt-${mustChangePassword ? 'pwc' : 'ok'}`,
  expiresIn: 900,
  user: owner(mustChangePassword),
});

export const categories = [
  { id: 10, name: 'Starters', displayOrder: 0, active: true, itemCount: 1 },
  { id: 20, name: 'Biryani', displayOrder: 1, active: true, itemCount: 1 },
];

export const dosaItem = {
  id: 1,
  categoryId: 10,
  categoryName: 'Starters',
  name: 'Masala Dosa',
  description: 'Crispy rice crepe',
  basePrice: 120,
  displayPrice: 120,
  foodType: 'VEG',
  gstPercent: 5,
  available: true,
  active: true,
  displayOrder: 0,
  version: 3,
  hasVariants: false,
  variants: [],
  addons: [],
};

export const biryaniItem = {
  id: 2,
  categoryId: 20,
  categoryName: 'Biryani',
  name: 'Chicken Biryani',
  displayPrice: 180,
  foodType: 'NON_VEG',
  gstPercent: 5,
  available: true,
  active: true,
  displayOrder: 0,
  version: 9,
  hasVariants: true,
  variants: [
    { id: 21, name: 'Half', price: 180, isDefault: true },
    { id: 22, name: 'Full', price: 320, isDefault: false },
  ],
  addons: [{ id: 31, name: 'Extra raita', price: 30 }],
};

const orderSummary = (id: number, status: string, flagged = false) => ({
  id,
  orderNumber: `260927-0${id}`,
  displayToken: id,
  status,
  tableLabel: `T${id % 5}`,
  customerName: id % 2 ? 'Ravi Kumar' : undefined,
  grandTotal: 250 + id * 10.5,
  itemCount: 1 + (id % 3),
  paymentMethod: 'upi',
  paymentFlagged: flagged,
  placedAt: '2026-09-27T06:30:00Z',
  paidAt: '2026-09-27T06:31:00Z',
});

const recentOrders = [
  orderSummary(41, 'CONFIRMED'),
  orderSummary(40, 'PREPARING'),
  orderSummary(39, 'READY', true),
  orderSummary(38, 'COMPLETED'),
];

const dashboard = {
  date: '2026-09-27',
  ordersToday: 42,
  revenueToday: 18450.5,
  averageOrderValue: 439.3,
  ordersByStatus: { CONFIRMED: 3, PREPARING: 2, READY: 1, COMPLETED: 34, CANCELLED: 2 },
  activeKitchenOrders: 6,
  flaggedOrders: 1,
  recentOrders,
};

const PNG_1PX =
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=';

export const orderDetail = {
  id: 39,
  orderNumber: '260927-039',
  displayToken: 39,
  status: 'CANCELLED',
  tableLabel: 'T4',
  customerName: 'Ravi Kumar',
  customerPhone: '9876543210',
  notes: 'Less spicy please',
  items: [
    { itemId: 2, variantId: 22, name: 'Chicken Biryani', variantName: 'Full', foodType: 'NON_VEG', addons: [{ name: 'Extra raita', price: 30 }], unitPrice: 350, quantity: 1, gstPercent: 5, lineTotal: 350, taxAmount: 17.5, notes: 'No onion' },
    { itemId: 1, name: 'Masala Dosa', foodType: 'VEG', addons: [], unitPrice: 120, quantity: 2, gstPercent: 5, lineTotal: 240, taxAmount: 12 },
  ],
  bill: { subtotal: 590, taxTotal: 29.5, cgst: 14.75, sgst: 14.75, grandTotal: 619.5, pricesIncludeGst: false },
  payments: [
    { provider: 'RAZORPAY', status: 'CAPTURED', method: 'upi', providerPaymentId: 'pay_TEST123456789', providerOrderId: 'order_TEST987654321', amountPaise: 61950, refundStatus: 'FAILED' },
  ],
  paymentFlagged: true,
  flagReason: 'Captured amount 600.00 does not match order total 619.50',
  cancelReason: 'Guest left',
  placedAt: '2026-09-27T06:30:00Z',
  paidAt: '2026-09-27T06:31:00Z',
  cancelledAt: '2026-09-27T06:50:00Z',
};

const tables = [1, 2, 3, 12].map((n) => ({
  id: n,
  label: n === 12 ? 'COUNTER' : `T${n}`,
  active: n !== 3,
  qrUrl: `http://localhost:4200/menu?t=token-${n}-abcdefghijklmnopqrstuvwxyz`,
  qrImageUrl: `/api/v1/admin/tables/${n}/qr.png`,
  createdAt: '2026-09-01T00:00:00Z',
  updatedAt: '2026-09-01T00:00:00Z',
}));

const salesSummary = {
  from: '2026-09-21',
  to: '2026-09-27',
  ordersCount: 212,
  gross: 93210.5,
  tax: 4438.6,
  cgst: 2219.3,
  sgst: 2219.3,
  net: 88771.9,
  averageOrderValue: 439.67,
  cancelledCount: 4,
  refundedAmount: 1650,
  paymentMethods: [
    { method: 'upi', count: 160, amount: 70010.5 },
    { method: 'card', count: 40, amount: 18200 },
    { method: 'netbanking', count: 12, amount: 5000 },
  ],
  topItems: Array.from({ length: 10 }, (_, i) => ({ name: `Dish number ${i + 1}`, quantity: 90 - i * 7, revenue: 12000 - i * 900 })),
  daily: [21, 22, 23, 24, 25, 26, 27].map((d, i) => ({ date: `2026-09-${d}`, orders: 20 + i * 3, gross: 9000 + ((i * 3517) % 7000) })),
};

const settings = {
  name: 'Spice Route',
  address: '12 MG Road, Bengaluru',
  phone: '080 1234 5678',
  gstin: '29ABCDE1234F1Z5',
  fssaiNo: '12345678901234',
  acceptingOrders: true,
  pricesIncludeGst: false,
  openingTime: '11:00:00',
  closingTime: '23:00:00',
  currency: 'INR',
  brandColor: '#b45309',
  kitchenWarnMinutes: 10,
  kitchenAlertMinutes: 20,
  readyAutoHideMinutes: 10,
};

const staff = [
  { id: 1, username: 'owner', displayName: 'Asha Owner', role: 'OWNER', active: true, mustChangePassword: false, hasPin: false, lastLoginAt: '2026-09-27T05:00:00Z', createdAt: '2026-09-01T00:00:00Z', email: 'asha@example.com' },
  { id: 2, username: 'manager.ravi', displayName: 'Ravi', role: 'MANAGER', active: true, mustChangePassword: true, hasPin: false, createdAt: '2026-09-02T00:00:00Z', phone: '9876543210' },
  { id: 3, username: 'kitchen1', role: 'KITCHEN', active: true, mustChangePassword: false, hasPin: true, lockedUntil: '2099-01-01T00:00:00Z', createdAt: '2026-09-02T00:00:00Z' },
];

const devices = [
  { id: 5, deviceName: 'Kitchen tablet', username: 'kitchen1', createdAt: '2026-09-10T00:00:00Z', lastSeenAt: '2026-09-27T06:59:00Z', expiresAt: '2026-10-10T00:00:00Z', active: true },
];

const json = (route: Route, body: unknown, status = 200) =>
  route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });

export async function mockAdminBackend(
  page: Page,
  options: { loggedIn?: boolean; mustChangePassword?: boolean } = {},
): Promise<AdminMockState> {
  const state: AdminMockState = {
    loggedIn: options.loggedIn ?? false,
    mustChangePassword: options.mustChangePassword ?? false,
    items: [structuredClone(dosaItem), structuredClone(biryaniItem)],
    requests: [],
  };
  let nextItemId = 100;

  await page.routeWebSocket(/\/ws$/, (ws) => {
    ws.onMessage((message) => {
      const frame = String(message);
      if (frame.startsWith('CONNECT') || frame.startsWith('STOMP')) {
        ws.send('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\u0000');
      }
    });
  });

  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();
    let body: unknown = null;
    try {
      body = request.postDataJSON();
    } catch {
      body = null;
    }
    state.requests.push({ method, path, body });

    if (path === '/api/v1/auth/csrf') return route.fulfill({ status: 204 });
    if (path === '/api/v1/auth/refresh') {
      return state.loggedIn
        ? json(route, token(state.mustChangePassword))
        : json(route, { code: 'SESSION_EXPIRED', message: 'Please sign in again' }, 401);
    }
    if (path === '/api/v1/auth/login') {
      const { username, password } = body as { username: string; password: string };
      if (username !== 'owner' || password !== 'Owner@2026x') {
        return json(route, { code: 'INVALID_CREDENTIALS', message: 'Invalid username or password' }, 401);
      }
      state.loggedIn = true;
      return json(route, token(state.mustChangePassword));
    }
    if (path === '/api/v1/auth/change-password') {
      state.mustChangePassword = false;
      return json(route, token(false));
    }
    if (path === '/api/v1/auth/logout') {
      state.loggedIn = false;
      return route.fulfill({ status: 204 });
    }

    if (path === '/api/v1/admin/dashboard') return json(route, dashboard);
    if (path === '/api/v1/admin/notifications/unread-count') return json(route, { count: 2 });
    if (path === '/api/v1/admin/notifications') {
      return json(route, {
        content: [
          {
            id: 1,
            event: 'PAYMENT_FLAGGED',
            title: 'Payment flagged for order #39',
            body: 'Amount mismatch',
            link: '/admin/orders/39',
            severity: 'HIGH',
            orderId: 39,
            read: false,
            createdAt: '2026-09-27T06:35:00Z',
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      });
    }

    if (path === '/api/v1/admin/categories' && method === 'GET') return json(route, categories);

    if (path === '/api/v1/admin/items' && method === 'GET') {
      const categoryId = url.searchParams.get('categoryId');
      const q = url.searchParams.get('q')?.toLowerCase();
      const content = state.items.filter(
        (i) =>
          (!categoryId || String(i['categoryId']) === categoryId) &&
          (!q || String(i['name']).toLowerCase().includes(q)),
      );
      return json(route, { content, page: 0, size: 50, totalElements: content.length, totalPages: 1 });
    }
    if (path === '/api/v1/admin/items' && method === 'POST') {
      const req = body as Json & { variants: Json[]; addons: Json[]; categoryId: number };
      const variants = req.variants.map((v, i) => ({ ...v, id: 1000 + i }));
      const item = {
        ...req,
        id: nextItemId++,
        categoryName: categories.find((c) => c.id === req.categoryId)?.name ?? '',
        active: true,
        displayOrder: 0,
        version: 0,
        hasVariants: variants.length > 0,
        variants,
        addons: req.addons.map((a, i) => ({ ...a, id: 2000 + i })),
        displayPrice: variants.find((v) => v['isDefault'])?.['price'] ?? req['basePrice'],
      };
      state.items.push(item);
      return json(route, item, 201);
    }
    const priceMatch = /^\/api\/v1\/admin\/items\/(\d+)\/price$/.exec(path);
    if (priceMatch && method === 'PATCH') {
      const item = state.items.find((i) => i['id'] === Number(priceMatch[1]))!;
      const req = body as { basePrice?: number; version: number };
      if (req.version !== item['version']) {
        return json(route, { code: 'CONCURRENT_MODIFICATION', message: 'Changed elsewhere' }, 409);
      }
      if (req.basePrice !== undefined) {
        item['basePrice'] = req.basePrice;
        item['displayPrice'] = req.basePrice;
      }
      item['version'] = (item['version'] as number) + 1;
      return json(route, item);
    }
    const itemMatch = /^\/api\/v1\/admin\/items\/(\d+)$/.exec(path);
    if (itemMatch && method === 'GET') {
      return json(route, state.items.find((i) => i['id'] === Number(itemMatch[1])));
    }

    if (path === '/api/v1/admin/orders') {
      const content = [...recentOrders, ...Array.from({ length: 8 }, (_, i) => orderSummary(30 - i, 'COMPLETED'))];
      return json(route, { content, page: 0, size: 25, totalElements: content.length, totalPages: 1 });
    }

    const orderMatch = /^\/api\/v1\/admin\/orders\/(\d+)$/.exec(path);
    if (orderMatch) return json(route, { ...orderDetail, id: Number(orderMatch[1]) });
    if (path === '/api/v1/admin/tables') return json(route, tables);
    if (/^\/api\/v1\/admin\/tables\/\d+\/qr\.png$/.test(path)) {
      return route.fulfill({ status: 200, contentType: 'image/png', body: Buffer.from(PNG_1PX, 'base64') });
    }
    if (path === '/api/v1/admin/reports/summary') return json(route, salesSummary);
    if (path === '/api/v1/admin/settings') return json(route, settings);
    if (path === '/api/v1/admin/staff') return json(route, staff);
    if (path === '/api/v1/admin/devices') return json(route, devices);

    if (path.startsWith('/api/v1/images/')) return route.fulfill({ status: 404 });
    return json(route, { code: 'NOT_FOUND', message: `Unmocked ${method} ${path}` }, 404);
  });

  return state;
}
