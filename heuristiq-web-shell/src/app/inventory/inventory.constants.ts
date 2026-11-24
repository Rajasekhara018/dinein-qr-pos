/** Where the inventory portal (a native-federation remote of Rider_IMSWeb) is mounted in Rider_Web. */
export const INVENTORY_ROUTE = '/inventory';

/** Federation names of the remote: its name in federation.manifest.json and the module key it exposes. */
export const INVENTORY_REMOTE_NAME = 'railcab-ims';
export const INVENTORY_REMOTE_EXPOSED_MODULE = './InventoryRoutes';

/**
 * Screen permission key of the whole portal (mapped to privilege codes in hq-shared-lib's hq-privilege-map.ts): any one
 * inventory screen opens the mount; each screen then checks its own key (INVENTORY_SCREEN_PERMISSIONS).
 */
export const INVENTORY_PERMISSION = 'view_inventory';

/**
 * Screen permission key per top-level route of the remote (its inventory-remote-routes.ts). The remote's own routes carry no
 * `data.permission`, so the mount checks them here (inventoryScreenGuard) before a screen opens.
 */
export const INVENTORY_SCREEN_PERMISSIONS: Readonly<Record<string, string>> = {
  dashboard: 'view_inventory_dashboard',
  items: 'view_inventory_items',
  locations: 'view_inventory_locations',
  receiving: 'view_inventory_receiving',
  transfers: 'view_inventory_transfers',
  ledger: 'view_inventory_ledger',
};

/**
 * Audit screen keys of the inventory screens, by route template (the mount path joined with the remote's route paths), merged
 * into SCREEN_KEYS_BY_ROUTE. The remote declares no `data.screenKey`, so the host names them. Never rename or translate one
 * once released.
 */
export const INVENTORY_SCREEN_KEYS_BY_ROUTE: Readonly<Record<string, string>> = {
  'inventory/dashboard': 'inventory.dashboard',
  'inventory/items': 'inventory.items',
  'inventory/locations': 'inventory.locations',
  'inventory/receiving': 'inventory.receiving',
  'inventory/receiving/:id': 'inventory.receiving-detail',
  'inventory/transfers': 'inventory.transfers',
  'inventory/transfers/:id': 'inventory.transfers-detail',
  'inventory/ledger': 'inventory.ledger',
};
