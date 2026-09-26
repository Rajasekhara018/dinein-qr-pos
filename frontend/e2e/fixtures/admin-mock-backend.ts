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

    if (path === '/api/auth/csrf') return route.fulfill({ status: 204 });
    if (path === '/api/auth/refresh') {
      return state.loggedIn
        ? json(route, token(state.mustChangePassword))
        : json(route, { code: 'SESSION_EXPIRED', message: 'Please sign in again' }, 401);
    }
    if (path === '/api/auth/login') {
      const { username, password } = body as { username: string; password: string };
      if (username !== 'owner' || password !== 'Owner@2026x') {
        return json(route, { code: 'INVALID_CREDENTIALS', message: 'Invalid username or password' }, 401);
      }
      state.loggedIn = true;
      return json(route, token(state.mustChangePassword));
    }
    if (path === '/api/auth/change-password') {
      state.mustChangePassword = false;
      return json(route, token(false));
    }
    if (path === '/api/auth/logout') {
      state.loggedIn = false;
      return route.fulfill({ status: 204 });
    }

    if (path === '/api/admin/dashboard') return json(route, dashboard);
    if (path === '/api/admin/notifications/unread-count') return json(route, { count: 2 });
    if (path === '/api/admin/notifications') {
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

    if (path === '/api/admin/categories' && method === 'GET') return json(route, categories);

    if (path === '/api/admin/items' && method === 'GET') {
      const categoryId = url.searchParams.get('categoryId');
      const q = url.searchParams.get('q')?.toLowerCase();
      const content = state.items.filter(
        (i) =>
          (!categoryId || String(i['categoryId']) === categoryId) &&
          (!q || String(i['name']).toLowerCase().includes(q)),
      );
      return json(route, { content, page: 0, size: 50, totalElements: content.length, totalPages: 1 });
    }
    if (path === '/api/admin/items' && method === 'POST') {
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
    const priceMatch = /^\/api\/admin\/items\/(\d+)\/price$/.exec(path);
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
    const itemMatch = /^\/api\/admin\/items\/(\d+)$/.exec(path);
    if (itemMatch && method === 'GET') {
      return json(route, state.items.find((i) => i['id'] === Number(itemMatch[1])));
    }

    if (path === '/api/admin/orders') {
      const content = [...recentOrders, ...Array.from({ length: 8 }, (_, i) => orderSummary(30 - i, 'COMPLETED'))];
      return json(route, { content, page: 0, size: 25, totalElements: content.length, totalPages: 1 });
    }

    if (path.startsWith('/api/images/')) return route.fulfill({ status: 404 });
    return json(route, { code: 'NOT_FOUND', message: `Unmocked ${method} ${path}` }, 404);
  });

  return state;
}
