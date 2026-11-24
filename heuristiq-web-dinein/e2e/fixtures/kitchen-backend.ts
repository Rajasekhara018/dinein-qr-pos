import { Page, Route, WebSocketRoute } from '@playwright/test';

/** Kitchen API + STOMP mocks (shapes mirror `KitchenOrderView`, `KitchenConfig`, `DeviceTokenResponse`). */

const minutesAgo = (m: number) => new Date(Date.now() - m * 60_000).toISOString();

export function kitchenOrders() {
  return [
    {
      id: 1,
      orderNumber: '260926-001',
      displayToken: 101,
      status: 'CONFIRMED',
      tableLabel: 'T1',
      notes: 'Birthday — bring together',
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
      paidAt: minutesAgo(25),
    },
    {
      id: 2,
      orderNumber: '260926-002',
      displayToken: 102,
      status: 'CONFIRMED',
      tableLabel: 'T4',
      items: [{ name: 'Filter Coffee', foodType: 'VEG', addons: [], quantity: 3 }],
      paidAt: minutesAgo(12),
    },
    {
      id: 3,
      orderNumber: '260926-003',
      displayToken: 103,
      status: 'PREPARING',
      tableLabel: 'T2',
      items: [{ name: 'Egg Bhurji', foodType: 'EGG', addons: [], quantity: 1 }],
      paidAt: minutesAgo(6),
      preparingAt: minutesAgo(4),
    },
    {
      id: 4,
      orderNumber: '260926-004',
      displayToken: 104,
      status: 'READY',
      tableLabel: 'T7',
      items: [{ name: 'Paneer Tikka', foodType: 'VEG', addons: [], quantity: 2 }],
      paidAt: minutesAgo(9),
      preparingAt: minutesAgo(7),
      readyAt: minutesAgo(1),
    },
  ];
}

export const deviceSession = {
  deviceToken: 'dvc_test_token',
  expiresAt: '2030-01-01T00:00:00Z',
  user: {
    id: 5,
    username: 'cook',
    displayName: 'Cook',
    role: 'KITCHEN',
    mustChangePassword: false,
  },
  deviceName: 'Pass screen',
};

export interface KitchenMock {
  orders: ReturnType<typeof kitchenOrders>;
  loginRequests: unknown[];
  statusRequests: { id: number; body: unknown; auth?: string }[];
  connectHeaders: string[];
  /** Pushes a STOMP MESSAGE on /topic/kitchen/orders to every subscriber. */
  push(event: unknown): void;
}

const json = (route: Route, body: unknown, status = 200) =>
  route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });

export async function mockKitchenBackend(page: Page): Promise<KitchenMock> {
  const subscribers: { ws: WebSocketRoute; id: string }[] = [];
  let messageId = 0;
  const mock: KitchenMock = {
    orders: kitchenOrders(),
    loginRequests: [],
    statusRequests: [],
    connectHeaders: [],
    push(event) {
      const body = JSON.stringify(event);
      for (const s of subscribers) {
        s.ws.send(
          `MESSAGE\ndestination:/topic/kitchen/orders\nsubscription:${s.id}\nmessage-id:${++messageId}\n` +
            `content-type:application/json\ncontent-length:${new TextEncoder().encode(body).length}\n\n${body}\u0000`,
        );
      }
    },
  };

  // Minimal STOMP broker: CONNECT → CONNECTED, remembers SUBSCRIBE ids so tests can push events.
  await page.routeWebSocket(/\/ws$/, (ws) => {
    ws.onMessage((message) => {
      for (const frame of String(message).split('\u0000')) {
        const clean = frame.replace(/^\n+/, '');
        if (clean.startsWith('CONNECT') || clean.startsWith('STOMP')) {
          mock.connectHeaders.push(clean);
          ws.send('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\u0000');
        } else if (clean.startsWith('SUBSCRIBE')) {
          const id = /\nid:([^\n]+)/.exec(clean)?.[1] ?? 'sub-0';
          subscribers.push({ ws, id });
        }
      }
    });
  });

  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

    if (path === '/api/v1/auth/csrf') return route.fulfill({ status: 204 });
    if (path === '/api/v1/auth/kitchen-device' && method === 'POST') {
      const body = request.postDataJSON() as { username: string; pin?: string };
      mock.loginRequests.push(body);
      if (body.pin !== '1234') {
        return json(route, { code: 'INVALID_CREDENTIALS', message: 'Invalid credentials' }, 401);
      }
      return json(route, {
        deviceToken: deviceSession.deviceToken,
        expiresAt: deviceSession.expiresAt,
        user: deviceSession.user,
      });
    }
    if (path === '/api/v1/kitchen/config') {
      return json(route, {
        restaurantName: 'Spice Route',
        warnMinutes: 10,
        alertMinutes: 20,
        readyAutoHideMinutes: 10,
      });
    }
    if (path === '/api/v1/kitchen/orders' && method === 'GET') return json(route, mock.orders);
    const statusMatch = /^\/api\/v1\/kitchen\/orders\/(\d+)\/status$/.exec(path);
    if (statusMatch && method === 'PATCH') {
      const id = Number(statusMatch[1]);
      const body = request.postDataJSON() as { status: string };
      mock.statusRequests.push({ id, body, auth: request.headers()['authorization'] });
      const order = mock.orders.find((o) => o.id === id);
      if (!order) return json(route, { code: 'NOT_FOUND', message: 'Order not found' }, 404);
      Object.assign(order, { status: body.status });
      if (body.status === 'PREPARING')
        Object.assign(order, { preparingAt: new Date().toISOString() });
      if (body.status === 'READY') Object.assign(order, { readyAt: new Date().toISOString() });
      const response = { ...order };
      if (body.status === 'COMPLETED') mock.orders = mock.orders.filter((o) => o.id !== id);
      return json(route, response);
    }
    return json(route, { code: 'NOT_FOUND', message: `Unmocked ${method} ${path}` }, 404);
  });

  return mock;
}

/** Init script: a remembered kitchen device (skips the login). */
export const rememberDevice = (session: typeof deviceSession) => {
  localStorage.setItem('dinein.kitchen.device.v1', JSON.stringify(session));
  localStorage.setItem('dinein.kitchen.sound.v1', 'off');
};
