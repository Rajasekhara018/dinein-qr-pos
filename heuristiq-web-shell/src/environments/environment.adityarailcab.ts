export const environment = {
  production: false,
  // Country a phone number typed without a country code is read in (ISO 3166 alpha-2); hq-phone-number-input's default.
  defaultPhoneCountry: 'IN',
  /** Currency (ISO 4217) money fields start on when an amount names none: the organization's currency for this deployment. */
  defaultCurrency: 'INR',
  apiUrl: 'https://rider.thebridaldesire.com/api',
  // matrimonial-api (the matrimony ops portal remote, railcab-matrimony). Same value as matrimonial-web-ops/src/environments:
  // the auth interceptor refreshes an expired access token for calls to this base instead of signing the user out.
  matrimonyApiUrl: 'http://35.154.15.238:9004/api',
  gatewayUrl: 'https://rider.thebridaldesire.com/api',
  adminApiUrl: 'https://rider.thebridaldesire.com/api/v1/admin',
  uamApiUrl: 'https://rider.thebridaldesire.com/api/v1/uam',
  franchiseApiUrl: 'https://rider.thebridaldesire.com/api/v1/franchise',
  walletApiUrl: 'https://rider.thebridaldesire.com/api/v1/wallet',
  wsUrl: 'https://rider.thebridaldesire.com/api/v1/nms/in-app/ws',
  paymentApiUrl: 'https://rider.thebridaldesire.com/api/v1/payment',
  bookingApiUrl: 'http://35.154.15.238:9006/api/v1/booking',
  rideApiUrl: 'https://rider.thebridaldesire.com/api/v1/ride',
  dispatchApiUrl: 'https://rider.thebridaldesire.com/api/v1/dispatch',
  locationApiUrl: 'https://rider.thebridaldesire.com/api/v1/location',
  riderOnboardingApiUrl: 'https://rider.thebridaldesire.com/api/v1/onboarding',
  workflowApiUrl: 'https://rider.thebridaldesire.com/api/v1/workflow',
  apiDocsUrl: 'https://rider.thebridaldesire.com/swagger-ui.html',
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
  configFile: 'assets/config/adityarailcab/adityarailcab.json',
};
