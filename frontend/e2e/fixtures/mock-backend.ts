import { Page, Route } from '@playwright/test';

/** Canned backend responses (shapes mirror the Spring Boot DTOs). */
export const session = {
  table: { id: 3, label: 'T3' },
  restaurant: {
    name: 'Spice Route',
    address: '12 MG Road, Bengaluru',
    gstin: '29ABCDE1234F1Z5',
    fssaiNo: '12345678901234',
    brandColor: '#b45309',
    acceptingOrders: true,
    openNow: true,
    openingTime: '11:00:00',
    closingTime: '23:00:00',
    pricesIncludeGst: false,
  },
  expiresAt: '2030-01-01T00:00:00Z',
};

export const menu = {
  version: 'v1',
  pricesIncludeGst: false,
  categories: [
    {
      id: 10,
      name: 'Starters',
      items: [
        {
          id: 1,
          name: 'Masala Dosa',
          description: 'Crispy rice crepe with potato masala',
          foodType: 'VEG',
          basePrice: 120,
          displayPrice: 120,
          gstPercent: 5,
          available: true,
          variants: [],
          addons: [],
        },
        {
          id: 3,
          name: 'Egg Bhurji',
          foodType: 'EGG',
          basePrice: 90,
          displayPrice: 90,
          gstPercent: 5,
          available: false,
          variants: [],
          addons: [],
        },
      ],
    },
    {
      id: 20,
      name: 'Biryani',
      items: [
        {
          id: 2,
          name: 'Chicken Biryani',
          description: 'Dum-cooked basmati rice with chicken',
          foodType: 'NON_VEG',
          displayPrice: 180,
          gstPercent: 5,
          available: true,
          variants: [
            { id: 21, name: 'Half', price: 180, isDefault: true },
            { id: 22, name: 'Full', price: 320, isDefault: false },
          ],
          addons: [{ id: 31, name: 'Extra raita', price: 30 }],
        },
      ],
    },
  ],
};

export const paidOrder = {
  id: 42,
  orderNumber: '260926-042',
  displayToken: 42,
  status: 'CONFIRMED',
  tableLabel: 'T3',
  customerName: 'Asha',
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
      taxAmount: 12,
    },
    {
      itemId: 2,
      variantId: 22,
      name: 'Chicken Biryani',
      variantName: 'Full',
      foodType: 'NON_VEG',
      addons: [{ name: 'Extra raita', price: 30 }],
      unitPrice: 350,
      quantity: 1,
      gstPercent: 5,
      lineTotal: 350,
      taxAmount: 17.5,
    },
  ],
  bill: {
    subtotal: 590,
    taxTotal: 29.5,
    cgst: 14.75,
    sgst: 14.75,
    grandTotal: 619.5,
    pricesIncludeGst: false,
  },
  payment: { provider: 'RAZORPAY', status: 'CAPTURED', method: 'upi' },
  canRetryPayment: false,
  placedAt: '2026-09-26T07:30:00Z',
  paidAt: '2026-09-26T07:31:00Z',
};

export interface MockBackend {
  placeOrderRequests: { headers: Record<string, string>; body: unknown }[];
  verifyRequests: unknown[];
}

const json = (route: Route, body: unknown, status = 200) =>
  route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });

export interface MockBackendOptions {
  /** Session `restaurant.takeawayEnabled` (omitted by default, like an older backend). */
  takeawayEnabled?: boolean;
  /** Order type echoed by the order views. */
  orderType?: 'DINE_IN' | 'TAKEAWAY';
}

/** Installs API + WebSocket mocks on the page. */
export async function mockBackend(page: Page, options: MockBackendOptions = {}): Promise<MockBackend> {
  const sessionBody =
    options.takeawayEnabled === undefined
      ? session
      : { ...session, restaurant: { ...session.restaurant, takeawayEnabled: options.takeawayEnabled } };
  const orderBody = options.orderType ? { ...paidOrder, orderType: options.orderType } : paidOrder;
  const state: MockBackend = { placeOrderRequests: [], verifyRequests: [] };

  // Minimal STOMP broker: acknowledge CONNECT so the realtime service reports "connected".
  await page.routeWebSocket(/\/ws$/, (ws) => {
    ws.onMessage((message) => {
      const frame = String(message);
      if (frame.startsWith('CONNECT') || frame.startsWith('STOMP')) {
        ws.send('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\u0000');
      }
    });
  });

  await page.route('https://checkout.razorpay.com/**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/javascript', body: '' }),
  );

  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

    if (path === '/api/v1/auth/csrf') return route.fulfill({ status: 204 });
    if (path === '/api/v1/public/session') {
      if (url.searchParams.has('t') && url.searchParams.get('t') !== 'abc') {
        return json(
          route,
          { code: 'INVALID_TABLE', message: 'Please scan the QR code on your table' },
          404,
        );
      }
      return json(route, sessionBody);
    }
    if (path === '/api/v1/public/menu') return json(route, menu);
    if (path === '/api/v1/public/orders' && method === 'POST') {
      state.placeOrderRequests.push({ headers: request.headers(), body: request.postDataJSON() });
      return json(route, {
        orderId: 42,
        orderNumber: '260926-042',
        displayToken: 42,
        status: 'PENDING_PAYMENT',
        provider: 'RAZORPAY',
        mode: 'SDK',
        amountPaise: 61950,
        currency: 'INR',
        restaurantName: 'Spice Route',
        checkout: {
          key: 'rzp_test_123',
          order_id: 'order_TEST42',
          amount: 61950,
          currency: 'INR',
          name: 'Spice Route',
          description: 'Order 260926-042',
          theme: { color: '#b45309' },
          scriptUrl: 'https://checkout.razorpay.com/v1/checkout.js',
        },
      });
    }
    if (path === '/api/v1/public/payments/verify') {
      state.verifyRequests.push(request.postDataJSON());
      return json(route, orderBody);
    }
    if (path === '/api/v1/public/orders/42') return json(route, orderBody);
    if (path === '/api/v1/public/orders')
      return json(route, [
        {
          id: 42,
          orderNumber: '260926-042',
          displayToken: 42,
          status: 'CONFIRMED',
          grandTotal: 619.5,
          itemCount: 3,
          placedAt: paidOrder.placedAt,
        },
      ]);
    if (path.startsWith('/api/v1/images/')) return route.fulfill({ status: 404 });
    return json(route, { code: 'NOT_FOUND', message: `Unmocked ${method} ${path}` }, 404);
  });

  return state;
}

/** Stub of Razorpay Checkout.js whose modal "succeeds" immediately. */
export const razorpayStub = () => {
  class RazorpayStub {
    constructor(private readonly options: { order_id: string; handler: (r: unknown) => void }) {
      (window as unknown as { __rzpOptions: unknown }).__rzpOptions = options;
    }
    on(): void {
      /* no failures in the happy path */
    }
    close(): void {
      /* noop */
    }
    open(): void {
      setTimeout(() =>
        this.options.handler({
          razorpay_order_id: this.options.order_id,
          razorpay_payment_id: 'pay_TEST123',
          razorpay_signature: 'sig_test',
        }),
      );
    }
  }
  (window as unknown as { Razorpay: unknown }).Razorpay = RazorpayStub;
};
