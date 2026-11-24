/**
 * Former audit key of the /food mount, when the console was one screen with its own menu. Each Food screen now reports its own key
 * (food.<screen>, see FOOD_SCREEN_PERMISSIONS); kept so the released key stays documented. Never rename or translate it.
 */
export const FOOD_ADMIN_SCREEN_KEY = 'food.admin';

/** Screen permission key of the whole Food portal: any one Food screen opens the mount; each screen then checks its own key. */
export const FOOD_PERMISSION = 'view_food';

/** Where the food admin portal (a native-federation remote of food-delivery-platform/admin-web) is mounted in Rider_Web. */
export const FOOD_ADMIN_ROUTE = '/food';

/** Federation names of the remote: its name in federation.manifest.json and the module key it exposes. */
export const FOOD_REMOTE_NAME = 'railcab-food';
export const FOOD_REMOTE_EXPOSED_MODULE = './FoodRoutes';

/**
 * Screen permission key per Food screen, by the first URL segment under /food (the remote's `:resource` and static paths). The
 * remote's routes carry no `data.permission`, so the mount checks them here (foodScreenGuard). `notifications/send` and a
 * record's detail, new and edit pages are covered by their list's key. The same keys gate the sidebar items.
 */
export const FOOD_SCREEN_PERMISSIONS: Readonly<Record<string, string>> = {
  dashboard: 'view_food_dashboard',
  restaurants: 'view_food_restaurants',
  categories: 'view_food_categories',
  foods: 'view_food_foods',
  customers: 'view_food_customers',
  'admin-users': 'view_food_admin_users',
  orders: 'view_food_orders',
  payments: 'view_food_payments',
  refunds: 'view_food_refunds',
  coupons: 'view_food_coupons',
  offers: 'view_food_offers',
  reviews: 'view_food_reviews',
  'delivery-partners': 'view_food_delivery_partners',
  'support-tickets': 'view_food_support_tickets',
  notifications: 'view_food_notifications',
  reports: 'view_food_reports',
  analytics: 'view_food_analytics',
  settings: 'view_food_settings',
  // Operations screens (the remote's ops module).
  'live-board': 'view_food_live_board',
  'refund-requests': 'view_food_refund_requests',
  'restaurant-approvals': 'view_food_restaurant_approvals',
  'menu-reviews': 'view_food_menu_reviews',
  settlements: 'view_food_settlements',
};

/** The restaurant-partner portal (the same remote's partner module), mounted on its own: partners hold no staff privilege. */
export const FOOD_PARTNER_ROUTE = '/food-partner';
export const FOOD_PARTNER_PERMISSION = 'view_restaurant_partner';

/**
 * Screen permission key per partner screen (first URL segment under /food-partner). `join` (applying to become a partner) has
 * none: it is open to any signed-in user, like its API.
 */
export const FOOD_PARTNER_SCREEN_PERMISSIONS: Readonly<Record<string, string>> = {
  dashboard: FOOD_PARTNER_PERMISSION,
  menu: FOOD_PARTNER_PERMISSION,
  hours: FOOD_PARTNER_PERMISSION,
  statements: FOOD_PARTNER_PERMISSION,
  staff: FOOD_PARTNER_PERMISSION,
};
