export const environment = {
  production: false,
  // Country a phone number typed without a country code is read in (ISO 3166 alpha-2); hq-phone-number-input's default.
  defaultPhoneCountry: 'IN',
  /** Currency (ISO 4217) money fields start on when an amount names none: the organization's currency for this deployment. */
  defaultCurrency: 'INR',
  // Confirmed: the real RailCab API Gateway (2026_RailCab_ApiGateway) listens on 8999
  // and exposes per-service routes at /api/<service> (e.g. /api/dms -> DMS, /api/bookings -> Bookings).
  apiUrl: 'http://localhost:8999/api',
  // matrimonial-api (the matrimony ops portal remote, railcab-matrimony). Same value as matrimonial-web-ops/src/environments:
  // the auth interceptor refreshes an expired access token for calls to this base instead of signing the user out.
  matrimonyApiUrl: 'http://localhost:8080/api',
  // hq-shared-lib (HqSharedLibModule.forRoot): DMS/UAM via the gateway, AdminAPI via the
  // gateway's admin route (/api/v1/admin/** -> Rider_AdminAPI /api/v1/**).
  gatewayUrl: 'http://localhost:8999/api',
  adminApiUrl: 'http://localhost:8999/api/v1/admin',
  // Confirmed: UAM is routed through the gateway's uam-api route (/api/uam/** ->
  // rewritten to UAM's /api/v1/uam/** — see 2026_RailCab_ApiGateway RouteConfig). Version made
  // explicit here (rather than relying on the gateway's implicit v1 default) -- every
  // UamService/AuthService call appends its own suffix to this one base, so this single change
  // versions all of them.
  // /api/v{N}/<service> ordering (version before the service name), matching every other base
  // below -- RouteConfig's routes accept it in either position, this is just this app's choice.
  uamApiUrl: 'http://localhost:8999/api/v1/uam',
  franchiseApiUrl: 'http://localhost:8999/api/v1/franchise',
  walletApiUrl: 'http://localhost:8999/api/v1/wallet',
  // NMS's in-app-notifications WebSocket endpoint, proxied through the gateway's existing
  // no-rewrite /api/v1/nms/** route (2026_RailCab_ApiGateway RouteConfig). NMS dropped its
  // legacy unversioned /api/nms/** alias when it adopted ApiVersionConfig.
  wsUrl: 'http://localhost:8999/api/v1/nms/in-app/ws',
  // The original RailCab PaymentAPI (/api/payment/**) doesn't exist in bestride-rider-app —
  // this repo's real payment service is Rider_PaymentService, routed through Rider_ApiGateway's
  // rider-payment-api route (/api/rider/payment/** -> rewritten to /api/v1/** — see RouteConfig).
  // Version made explicit (see uamApiUrl above for why). NOTE: payment-service.ts's create-session
  // and check-status paths don't correspond to any method on Rider_PaymentService's real
  // PaymentController -- flagged separately, not something this version change fixes.
  paymentApiUrl: 'http://localhost:8999/api/v1/payment',
  // Rider_BookingsAPI's real port in this repo is 9006 (not 9001 — that was the upstream
  // RailCab platform's BookingsAPI port), matching Rider_AdminWeb's own environment.ts.
  bookingApiUrl: 'http://localhost:9006/api/v1/booking',
  // Remaining Rider_* microservices routed through the gateway (rider-ride-api, rider-dispatch-api,
  // rider-location-api, rider-onboarding-api, rider-workflow-api -- see RouteConfig), each versioned
  // explicitly for the same reason as uamApiUrl/paymentApiUrl above. Not yet consumed by any service
  // in this app (added so a future feature has a ready-made, correctly-versioned base to build on,
  // same as how bookingApiUrl above is already defined but unreferenced).
  rideApiUrl: 'http://localhost:8999/api/v1/ride',
  dispatchApiUrl: 'http://localhost:8999/api/v1/dispatch',
  locationApiUrl: 'http://localhost:8999/api/v1/location',
  riderOnboardingApiUrl: 'http://localhost:8999/api/v1/onboarding',
  workflowApiUrl: 'http://localhost:8999/api/v1/workflow',
  // The centralized Swagger aggregator hosted by the gateway itself (springdoc.swagger-ui.urls,
  // see 2026_RailCab_ApiGateway) -- embedded inline via iframe in the ApiDocs component. The
  // gateway's swaggerSecurityWebFilterChain permits framing from this app's own origin via CSP
  // frame-ancestors (railcab.gateway.swagger.frame-ancestors).
  apiDocsUrl: 'http://localhost:8999/swagger-ui.html',
  javaDocsUrl: 'https://rider.thebridaldesire.com/javadoc/index.html',
  firebase: {
    apiKey: 'AIzaSyBwOLyYBoJi56Bs5DLGDCEfHvAZT1A0g7g',
    authDomain: 'gateway-2a04c.firebaseapp.com',
    projectId: 'gateway-2a04c',
    storageBucket: 'gateway-2a04c.firebasestorage.app',
    messagingSenderId: '60866980833',
    appId: '1:60866980833:web:b43ecded94dbcfc52680a0',
    // Same Firebase project (gateway-2a04c) as AdminWeb — VAPID keys are project-level,
    // shared across web-app registrations within a project.
    vapidKey: 'BDl1VxlnyJu4_Pp-b1wQpI44fvl5hjV71ktSHFCXQ1XxU58DgQHVRtr1tLRYTzHWVhMpl7QFhFrPkMGdw_E6IqQ',
  },
  // Per-deployment branding (app name, logo, tagline, colors) is NOT baked into the build: the
  // root AppModule's provideAppInitializer (BrandingService) fetches this JSON at startup. To
  // rebrand a deployment, edit/swap the JSON file -- no rebuild needed.
  configFile: 'assets/config/default/default.json',
};
