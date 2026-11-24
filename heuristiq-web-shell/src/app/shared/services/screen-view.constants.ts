import { PUBLIC_ROUTE_PREFIXES } from '../public-routes.constants';
import { INVENTORY_SCREEN_KEYS_BY_ROUTE } from '../../inventory/inventory.constants';

/**
 * Screen keys for the audit trail (Rider_AuditService SCREEN_VIEW events).
 *
 * Keys are queryable history: never rename one once released, never translate it. A key is
 * resolved per navigation in this order:
 *   1. `data: { screenKey: '...' }` on the leaf route (use this for new routes if you prefer to
 *      declare the key next to the route),
 *   2. {@link SCREEN_KEYS_BY_ROUTE}, keyed by the full route template (the routeConfig paths of
 *      the activated chain joined with '/', e.g. `access-management/users/:id/edit`),
 *   3. a sanitised dotted form of the URL path.
 */
export const SCREEN_KEYS_BY_ROUTE: Readonly<Record<string, string>> = {
  // Inventory portal (federation remote 'railcab-ims'): its routes declare no screenKey, so the host names them.
  ...INVENTORY_SCREEN_KEYS_BY_ROUTE,
  // auth / vendor
  'auth/login': 'auth.login',
  'auth/register': 'auth.register',
  'auth/forgot-password': 'auth.forgot_password',
  'change-password': 'auth.change_password',
  'vendor/register': 'vendor.register',
  'vendor/documents': 'vendor.documents',

  // home / shell
  'home': 'home.dashboard',
  'today': 'today.overview',
  'my-bookings': 'home.my_bookings',
  'notifications': 'notifications.inbox',
  'profile': 'profile.overview',

  // onboarding
  'onboarding-forms': 'onboarding.forms',
  'onboarding-reviews': 'onboarding.reviews',
  'onboarded-users': 'onboarded_users.list',
  'onboarded-users/:id': 'onboarded_users.detail',

  // workflow / service registry
  'workflow/tasks': 'workflow.tasks',
  'workflow/start': 'workflow.start',
  'workflow/definitions': 'workflow.definitions',
  'workflow/definitions/:key': 'workflow.definition',
  'workflow/reports': 'workflow.reports',
  'workflow/instances': 'workflow.instances',
  'workflow/instances/:id': 'workflow.instance',
  'service-registry': 'service_registry.list',

  // nms templates
  'nms-templates': 'nms.templates',
  'nms-templates/events': 'nms.template_events',
  'nms-templates/list': 'nms.template_list',
  'nms-templates/create': 'nms.template_create',
  'nms-templates/edit/:id': 'nms.template_edit',

  // document management
  'document-management/document-types': 'document_management.document_types',
  'document-management/document-types/new': 'document_management.document_type_create',
  'document-management/document-types/:id/edit': 'document_management.document_type_edit',
  'document-management/lifecycle': 'document_management.lifecycle',
  'document-management/lifecycle/:id': 'document_management.lifecycle_detail',

  // api docs
  'api-docs': 'api_docs.overview',
  'api-docs/java-doc': 'api_docs.javadoc',
  'api-docs/java-doc/:service': 'api_docs.javadoc_viewer',

  // payments
  'payments/make-payment': 'payments.make_payment',
  'payments/invoice/:paymentId': 'payments.invoice',

  // access management
  'access-management/users': 'access_management.users',
  'access-management/users/new': 'access_management.user_create',
  'access-management/users/:id/edit': 'access_management.user_edit',
  'access-management/users/:id': 'access_management.user_detail',
  'access-management/roles': 'access_management.roles',
  'access-management/roles/new': 'access_management.role_create',
  'access-management/roles/:id/edit': 'access_management.role_edit',
  'access-management/organizations': 'access_management.organizations',

  // organization structure (keys in organization-structure/org-structure.constants.ts ORG_SCREEN_KEYS)
  'org-structure/chart': 'org_structure.chart',
  'org-structure/builder': 'org_structure.builder',
  'org-structure/import': 'org_structure.import',
  'org-structure/logins': 'org_structure.logins',
  'access-management/organizations/new': 'access_management.organization_create',
  'access-management/organizations/:id/edit': 'access_management.organization_edit',
  'access-management/applications': 'access_management.applications',
  'access-management/applications/new': 'access_management.application_create',
  'access-management/applications/:id/edit': 'access_management.application_edit',
  'access-management/applications/:code/access': 'access_management.application_access',
  'access-management/clients': 'access_management.clients',
  'access-management/clients/new': 'access_management.client_create',
  'access-management/clients/:id/edit': 'access_management.client_edit',
  'access-management/security-policies': 'access_management.security_policies',
  'access-management/security-policies/new': 'access_management.security_policy_create',
  'access-management/security-policies/:id/edit': 'access_management.security_policy_edit',
  'access-management/notification-templates': 'access_management.notification_templates',
  'access-management/notification-templates/new': 'access_management.notification_template_create',
  'access-management/notification-templates/:id/edit': 'access_management.notification_template_edit',
};

/**
 * Routes not reported here: the federated Rider_AdminWeb remote reports its own screen views, and the public pages
 * (the shared-trip page `/track`) are anonymous, so nothing is sent for them even when a staff session exists.
 */
export const SCREEN_VIEW_EXCLUDED_PREFIXES: readonly string[] = ['/admin', ...PUBLIC_ROUTE_PREFIXES];

/** Master switch for screen-view reporting. */
export const SCREEN_VIEW_AUDIT_ENABLED = true;

/** Gateway path (below environment.gatewayUrl): Rider_AuditService serves `/api/v1/audit/...`. */
export const SCREEN_VIEWS_PATH = '/v1/audit/screen-views';

export const SCREEN_VIEW_APP_CHANNEL = 'WEB';

/** Where the session's access token lives (same key as the auth interceptor). */
export const ACCESS_TOKEN_STORAGE_KEY = 'access_token';

export const SCREEN_VIEW_MAX_BUFFERED = 200;
/** Events per request during normal operation. */
export const SCREEN_VIEW_BATCH_SIZE = 20;
/** Events per request when flushing on tab hide/unload (server maximum), to need fewer keepalive requests. */
export const SCREEN_VIEW_UNLOAD_BATCH_SIZE = 50;
export const SCREEN_VIEW_FLUSH_INTERVAL_MS = 15_000;
/** Stop sending for this long after the server answers 429. */
export const SCREEN_VIEW_RATE_LIMIT_PAUSE_MS = 60_000;
export const SCREEN_VIEW_MAX_KEY_LENGTH = 100;

/** Matches Rider_AuditService's screen key pattern. */
export const SCREEN_KEY_PATTERN = /^[a-z][a-z0-9_-]*(\.[a-z0-9_-]+)*$/;
