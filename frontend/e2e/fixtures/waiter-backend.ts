import { Page, Route, WebSocketRoute } from '@playwright/test';
import { menu } from './mock-backend';

/**
 * Waiter API + STOMP mocks (shapes mirror `WaiterConfig`, `WaiterTableView`, `KitchenOrderView`, `GuestOrderView`,
 * `CheckoutResponse`, `TokenResponse`). The STOMP broker follows `kitchen-backend.ts` and remembers each
 * subscription's destination so tests can push to a topic.
 */

const minutesAgo = (m: number) => new Date(Date.now() - m * 60_000).toISOString();

export const waiterUser = {
  id: 9,
  username: 'ravi',
  displayName: 'Ravi',
  role: 'WAITER',
  mustChangePassword: false,
};

export const waiterConfig = {
  restaurantName: 'Spice Route',
  currency: 'INR',
  acceptingOrders: true,
  openNow: true,
  takeawayEnabled: true,
  pricesIncludeGst: false,
  onlinePaymentsAvailable: true,
  kitchenWarnMinutes: 10,
  kitchenAlertMinutes: 20,
  staff: waiterUser,
};

export function waiterTables() {
  return [
    { id: 1, label: 'T1', openOrders: 0, confirmed: 0, preparing: 0, ready: 0 },
    {
      id: 2,
      label: 'T2',
      openOrders: 2,
      confirmed: 1,
      preparing: 0,
      ready: 1,
      oldestOpenSince: minutesAgo(20),
    },
    { id: 3, label: 'T3', openOrders: 1, confirmed: 0, preparing: 1, ready: 0 },
    { id: 4, label: 'Terrace 12', openOrders: 0, confirmed: 0, preparing: 0, ready: 0 },
  ];
}

export function waiterOrders() {
  return [
    {
      id: 11,
      orderNumber: '260927-011',
      displayToken: 11,
      status: 'READY',
      orderType: 'DINE_IN',
      tableId: 2,
      tableLabel: 'T2',
      placedByStaff: false,
      items: [
        { name: 'Masala Dosa', foodType: 'VEG', addons: [], quantity: 2 },
        {
          name: 'Chicken Biryani',
          variantName: 'Full',
          foodType: 'NON_VEG',
          addons: ['Extra raita'],
          quantity: 1,
          notes: 'No onion',
        },
      ],
      paidAt: minutesAgo(20),
      preparingAt: minutesAgo(15),
      readyAt: minutesAgo(3),
    },
    {
      id: 12,
      orderNumber: '260927-012',
      displayToken: 12,
      status: 'CONFIRMED',
      orderType: 'DINE_IN',
      tableId: 2,
      tableLabel: 'T2',
      placedByStaff: true,
      items: [{ name: 'Filter Coffee', foodType: 'VEG', addons: [], quantity: 2 }],
      paidAt: minutesAgo(4),
    },
    {
      id: 13,
      orderNumber: '260927-013',
      displayToken: 13,
      status: 'PREPARING',
      orderType: 'TAKEAWAY',
      tableId: 3,
      tableLabel: 'T3',
      placedByStaff: false,
      items: [{ name: 'Egg Bhurji', foodType: 'EGG', addons: [], quantity: 1 }],
      paidAt: minutesAgo(8),
      preparingAt: minutesAgo(6),
    },
  ];
}

export interface WaiterMock {
  orders: ReturnType<typeof waiterOrders>;
  loggedIn: boolean;
  loginRequests: unknown[];
  placeRequests: { path: string; body: Record<string, unknown> }[];
  serveRequests: { id: number; auth?: string }[];
  connectFrames: string[];
  /** Pushes a STOMP MESSAGE to every subscriber of `destination`. */
  push(destination: string, body: unknown): void;
}

const json = (route: Route, body: unknown, status = 200) =>
  route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });

const token = () => ({ accessToken: 'jwt-waiter', expiresIn: 900, user: waiterUser });

export async function mockWaiterBackend(
  page: Page,
  options: { loggedIn?: boolean } = {},
): Promise<WaiterMock> {
  const subscribers: { ws: WebSocketRoute; id: string; destination: string }[] = [];
  let messageId = 0;
  const mock: WaiterMock = {
    orders: waiterOrders(),
    loggedIn: options.loggedIn ?? false,
    loginRequests: [],
    placeRequests: [],
    serveRequests: [],
    connectFrames: [],
    push(destination, event) {
      const body = JSON.stringify(event);
      for (const s of subscribers.filter((sub) => sub.destination === destination)) {
        s.ws.send(
          `MESSAGE\ndestination:${destination}\nsubscription:${s.id}\nmessage-id:${++messageId}\n` +
            `content-type:application/json\ncontent-length:${new TextEncoder().encode(body).length}\n\n${body}\u0000`,
        );
      }
    },
  };

  // Minimal STOMP broker: CONNECT → CONNECTED, remembers SUBSCRIBE ids per destination.
  await page.routeWebSocket(/\/ws$/, (ws) => {
    ws.onMessage((message) => {
      for (const frame of String(message).split('\u0000')) {
        const clean = frame.replace(/^\n+/, '');
        if (clean.startsWith('CONNECT') || clean.startsWith('STOMP')) {
          mock.connectFrames.push(clean);
          ws.send('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\u0000');
        } else if (clean.startsWith('SUBSCRIBE')) {
          const id = /\nid:([^\n]+)/.exec(clean)?.[1] ?? 'sub-0';
          const destination = /\ndestination:([^\n]+)/.exec(clean)?.[1] ?? '';
          subscribers.push({ ws, id, destination });
        }
      }
    });
  });

  let nextOrderId = 40;

  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

    if (path === '/api/v1/auth/csrf') return route.fulfill({ status: 204 });
    if (path === '/api/v1/auth/refresh') {
      return mock.loggedIn
        ? json(route, token())
        : json(route, { code: 'SESSION_EXPIRED', message: 'Session expired' }, 401);
    }
    if (path === '/api/v1/auth/logout') {
      mock.loggedIn = false;
      return route.fulfill({ status: 204 });
    }
    if (path === '/api/v1/auth/login' && method === 'POST') {
      const body = request.postDataJSON() as { username: string; pin?: string; password?: string };
      mock.loginRequests.push(body);
      if (body.username !== 'ravi' || (body.pin ?? body.password) !== '1234') {
        return json(route, { code: 'INVALID_CREDENTIALS', message: 'Invalid username or password' }, 401);
      }
      mock.loggedIn = true;
      return json(route, token());
    }

    if (path.startsWith('/api/v1/waiter/') && request.headers()['authorization'] !== 'Bearer jwt-waiter') {
      return json(route, { code: 'UNAUTHORIZED', message: 'Please sign in' }, 401);
    }
    if (path === '/api/v1/waiter/config') return json(route, waiterConfig);
    if (path === '/api/v1/waiter/tables') return json(route, waiterTables());
    if (path === '/api/v1/waiter/menu') return json(route, menu);
    if (path === '/api/v1/waiter/notifications/unread-count') return json(route, { count: 0 });
    if (path === '/api/v1/waiter/notifications') {
      return json(route, { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    }
    if (path === '/api/v1/waiter/orders' && method === 'GET') return json(route, mock.orders);
    if (path === '/api/v1/waiter/orders' && method === 'POST') {
      const body = request.postDataJSON() as Record<string, unknown>;
      mock.placeRequests.push({ path, body });
      const id = ++nextOrderId;
      return json(route, {
        orderId: id,
        orderNumber: `260927-0${id}`,
        displayToken: id,
        status: 'CONFIRMED',
        provider: 'OFFLINE',
        amountPaise: 25200,
        currency: 'INR',
        restaurantName: 'Spice Route',
      });
    }
    const serve = /^\/api\/v1\/waiter\/orders\/(\d+)\/serve$/.exec(path);
    if (serve && method === 'PATCH') {
      const id = Number(serve[1]);
      mock.serveRequests.push({ id, auth: request.headers()['authorization'] });
      const order = mock.orders.find((o) => o.id === id);
      if (!order) return json(route, { code: 'NOT_FOUND', message: 'Order not found' }, 404);
      mock.orders = mock.orders.filter((o) => o.id !== id);
      return json(route, { ...order, status: 'COMPLETED' });
    }
    const one = /^\/api\/v1\/waiter\/orders\/(\d+)$/.exec(path);
    if (one) {
      const id = Number(one[1]);
      return json(route, {
        id,
        orderNumber: `260927-0${id}`,
        displayToken: id,
        status: 'CONFIRMED',
        orderType: 'DINE_IN',
        tableLabel: 'T1',
        items: [
          {
            itemId: 1,
            name: 'Masala Dosa',
            foodType: 'VEG',
            addons: [],
            unitPrice: 120,
            quantity: 2,
            gstPercent: 5,
            lineTotal: 240,
          },
        ],
        bill: { subtotal: 240, taxTotal: 12, cgst: 6, sgst: 6, grandTotal: 252, pricesIncludeGst: false },
        payment: { provider: 'OFFLINE', status: 'CAPTURED', method: 'CASH', amountPaise: 25200 },
        canRetryPayment: false,
        placedAt: minutesAgo(1),
        paidAt: minutesAgo(1),
      });
    }
    if (path.startsWith('/api/v1/images/')) return route.fulfill({ status: 404 });
    return json(route, { code: 'NOT_FOUND', message: `Unmocked ${method} ${path}` }, 404);
  });

  return mock;
}

/** Init script: sound preference chosen, so the "Enable sound" prompt does not cover content. */
export const soundOff = () => {
  localStorage.setItem('dinein.waiter.sound.v1', 'off');
};
