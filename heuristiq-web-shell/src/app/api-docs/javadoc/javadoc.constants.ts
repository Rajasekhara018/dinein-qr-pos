/** The services whose generated Java documentation the gateway serves, grouped as the docs index shows them. */

export type JavadocGroup = 'platform' | 'rider';

export interface JavadocService {
  /** URL-safe id used in the route: /api-docs/java-doc/:id */
  id: string;
  name: string;
  /** Path on the gateway origin that serves the service's Javadoc. */
  path: string;
  group: JavadocGroup;
}

/** Translation keys for the group headings. */
export const JAVADOC_GROUP_LABEL_KEYS: Record<JavadocGroup, string> = {
  platform: 'apiDocs.javaDocs.groups.platform',
  rider: 'apiDocs.javaDocs.groups.rider',
};

export const JAVADOC_GROUPS: readonly JavadocGroup[] = ['platform', 'rider'];

export const JAVADOC_SERVICES: readonly JavadocService[] = [
  { id: 'gateway', name: 'API Gateway', path: '/javadoc/gateway/index.html', group: 'platform' },
  { id: 'registry', name: 'Service Registry', path: '/api/registry/javadoc/', group: 'platform' },
  { id: 'uam', name: 'UAM', path: '/api/uam/javadoc/', group: 'platform' },
  { id: 'admin', name: 'Admin API', path: '/api/admin/javadoc/', group: 'platform' },
  { id: 'dms', name: 'DMS', path: '/api/dms/javadoc/', group: 'platform' },
  { id: 'nms', name: 'NMS', path: '/api/nms/javadoc/', group: 'platform' },
  { id: 'search', name: 'Search API', path: '/api/search/javadoc/', group: 'platform' },

  { id: 'pricing', name: 'Pricing', path: '/api/rider/pricing/javadoc/', group: 'rider' },
  { id: 'location', name: 'Location', path: '/api/rider/location/javadoc/', group: 'rider' },
  { id: 'dispatch', name: 'Dispatch', path: '/api/rider/dispatch/javadoc/', group: 'rider' },
  { id: 'ride', name: 'Ride', path: '/api/rider/ride/javadoc/', group: 'rider' },
  { id: 'payment', name: 'Payment', path: '/api/rider/payment/javadoc/', group: 'rider' },
  { id: 'customer', name: 'Customer', path: '/api/rider/customer/javadoc/', group: 'rider' },
  { id: 'workflow', name: 'Workflow', path: '/api/rider/workflow/javadoc/', group: 'rider' },
  { id: 'onboarding', name: 'Onboarding', path: '/api/rider/onboarding/javadoc/', group: 'rider' },
  { id: 'driver-earnings', name: 'Driver Earnings', path: '/api/rider/driver-earnings/javadoc/', group: 'rider' },
];

/** Route of the services list; the viewer's Back button returns here. */
export const JAVADOC_LIST_ROUTE = '/api-docs/java-doc';
