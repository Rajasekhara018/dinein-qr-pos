export const environment = {
  production: false,
  // Country a phone number typed without a country code is read in (ISO 3166 alpha-2); hq-phone-number-input's default.
  defaultPhoneCountry: 'IN',
  /** Currency (ISO 4217) money fields start on when an amount names none: the organization's currency for this deployment. */
  defaultCurrency: 'INR',
  apiUrl: 'https://ops-dev.pasupukumkuma.net/api',
  // matrimonial-api (the matrimony ops portal remote, railcab-matrimony). Same value as matrimonial-web-ops/src/environments:
  // the auth interceptor refreshes an expired access token for calls to this base instead of signing the user out.
  matrimonyApiUrl: 'https://ops-dev.pasupukumkuma.net/api/v1/matrimony',
  gatewayUrl: 'https://ops-dev.pasupukumkuma.net/api',
  adminApiUrl: 'https://ops-dev.pasupukumkuma.net/api/v1/admin',
  uamApiUrl: 'https://ops-dev.pasupukumkuma.net/api/v1/uam',
  franchiseApiUrl: 'https://ops-dev.pasupukumkuma.net/api/v1/franchise',
  walletApiUrl: 'https://ops-dev.pasupukumkuma.net/api/v1/wallet',
  wsUrl: 'https://ops-dev.pasupukumkuma.net/api/v1/nms/in-app/ws',
  paymentApiUrl: 'https://ops-dev.pasupukumkuma.net/api/v1/payment',
  bookingApiUrl: 'https://ops-dev.pasupukumkuma.net/api/bookings/v1/booking',
  rideApiUrl: 'https://ops-dev.pasupukumkuma.net/api/v1/ride',
  dispatchApiUrl: 'https://ops-dev.pasupukumkuma.net/api/v1/dispatch',
  locationApiUrl: 'https://ops-dev.pasupukumkuma.net/api/v1/location',
  riderOnboardingApiUrl: 'https://ops-dev.pasupukumkuma.net/api/v1/onboarding',
  workflowApiUrl: 'https://ops-dev.pasupukumkuma.net/api/v1/workflow',
  apiDocsUrl: 'https://ops-dev.pasupukumkuma.net/swagger-ui.html',
  javaDocsUrl: 'https://ops-dev.pasupukumkuma.net/javadoc/index.html',
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
  configFile: 'assets/config/realboom/realboom.json',
  // The RealBoom profile does not run the ride-hailing or train sides: the "Admin" and "Train operations" sidebar sections and the
  // "Rides and drivers" card on Today are left out (the screens stay reachable by URL for anyone holding the privileges).
  // The codes the UAM application form lets you pick (one per application of this deployment; add a code here to register another).
  applicationCodes: ['PASUPU_KUNKUMA_MATRIMONY'],
  // The three Reports sections (dashboard, setup, run and review) are also left out of the sidebar and the app launcher.
  hiddenNavSections: ['nav.sections.adminTrain', 'nav.sections.reports', 'nav.sections.reportsSetup', 'nav.sections.reportsRun'],
  hiddenTodaySections: ['train'],
};
