export const environment = {
  production: true,
  // Country a phone number typed without a country code is read in (ISO 3166 alpha-2); hq-phone-number-input's default.
  defaultPhoneCountry: 'IN',
  /** Currency (ISO 4217) money fields start on when an amount names none: the organization's currency for this deployment. */
  defaultCurrency: 'INR',
  // Copied from 2026_RailCab_Web into bestride-rider-app as Rider_Web — repointed from the real
  // RailCab platform's production host (35.154.15.238) to this repo's own services. Update these
  // to your actual deployed host/ports when this app is deployed for real; 35.154.15.238 values below
  // match Rider_ApiGateway/Rider_BookingsAPI's actual ports in this repo, same as the .development
  // variant, so a "production" build still points somewhere real during local testing.
  apiUrl: 'https://rider.thebridaldesire.com/api',
  // matrimonial-api (the matrimony ops portal remote, railcab-matrimony). Same value as matrimonial-web-ops/src/environments:
  // the auth interceptor refreshes an expired access token for calls to this base instead of signing the user out.
  matrimonyApiUrl: 'http://35.154.15.238:9004/api',
  // hq-shared-lib (HqSharedLibModule.forRoot): DMS/UAM via the gateway, AdminAPI via the
  // gateway's admin route (/api/v1/admin/** -> Rider_AdminAPI /api/v1/**).
  gatewayUrl: 'https://rider.thebridaldesire.com/api',
  adminApiUrl: 'https://rider.thebridaldesire.com/api/v1/admin',
  // UAM is routed through the gateway's uam-api route (/api/uam/** -> rewritten to
  // Rider_UAM's /api/v1/uam/** — see Rider_ApiGateway's RouteConfig). Version made explicit here
  // (rather than relying on the gateway's implicit v1 default) -- every UamService/AuthService
  // call appends its own suffix to this one base, so this single change versions all of them.
  // /api/v{N}/<service> ordering (version before the service name), matching every other base
  // below -- RouteConfig's routes accept it in either position, this is just this app's choice.
  uamApiUrl: 'https://rider.thebridaldesire.com/api/v1/uam',
  franchiseApiUrl: 'https://rider.thebridaldesire.com/api/v1/franchise',
  walletApiUrl: 'https://rider.thebridaldesire.com/api/v1/wallet',
  // NMS's in-app-notifications WebSocket endpoint, proxied through the gateway's existing
  // no-rewrite /api/v1/nms/** route (Rider_ApiGateway's RouteConfig) -> Rider_NMS (real port 9015
  // in this repo, corrected from the upstream 9005 which collided with Rider_AdminAPI here).
  // NMS dropped its legacy unversioned /api/nms/** alias when it adopted ApiVersionConfig.
  wsUrl: 'https://rider.thebridaldesire.com/api/v1/nms/in-app/ws',
  // The original RailCab PaymentAPI (/api/payment/**) doesn't exist in bestride-rider-app —
  // this repo's real payment service is Rider_PaymentService, routed through Rider_ApiGateway's
  // rider-payment-api route (/api/rider/payment/** -> rewritten to /api/v1/** — see RouteConfig).
  // Version made explicit (see uamApiUrl above for why). NOTE: payment-service.ts's create-session
  // and check-status paths don't correspond to any method on Rider_PaymentService's real
  // PaymentController -- flagged separately, not something this version change fixes.
  paymentApiUrl: 'https://rider.thebridaldesire.com/api/v1/payment',
  // Rider_BookingsAPI's real port in this repo is 9006 (not 9001 — that was the upstream
  // RailCab platform's BookingsAPI port), matching Rider_AdminWeb's own environment.ts.
  bookingApiUrl: 'http://35.154.15.238:9006/api/v1/booking',
  // Remaining Rider_* microservices routed through the gateway (rider-ride-api, rider-dispatch-api,
  // rider-location-api, rider-onboarding-api, rider-workflow-api -- see RouteConfig), each versioned
  // explicitly for the same reason as uamApiUrl/paymentApiUrl above. Not yet consumed by any service
  // in this app (added so a future feature has a ready-made, correctly-versioned base to build on,
  // same as how bookingApiUrl above is already defined but unreferenced).
  rideApiUrl: 'https://rider.thebridaldesire.com/api/v1/ride',
  dispatchApiUrl: 'https://rider.thebridaldesire.com/api/v1/dispatch',
  locationApiUrl: 'https://rider.thebridaldesire.com/api/v1/location',
  riderOnboardingApiUrl: 'https://rider.thebridaldesire.com/api/v1/onboarding',
  workflowApiUrl: 'https://rider.thebridaldesire.com/api/v1/workflow',
  // The centralized Swagger aggregator hosted by the gateway itself (springdoc.swagger-ui.urls,
  // see Rider_ApiGateway) -- embedded inline via iframe in the ApiDocs component. The gateway's
  // swaggerSecurityWebFilterChain permits framing from this app's own origin via CSP
  // frame-ancestors (railcab.gateway.swagger.frame-ancestors).
  apiDocsUrl: 'https://rider.thebridaldesire.com/swagger-ui/index.html',
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
