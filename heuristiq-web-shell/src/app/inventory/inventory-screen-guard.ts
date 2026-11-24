import { mountedScreenGuard } from '../auth/mounted-screen-guard';
import { INVENTORY_SCREEN_PERMISSIONS } from './inventory.constants';

/**
 * Checks each inventory screen's own permission key when it opens. The remote (Rider_IMSWeb) declares no `data.permission` on
 * its routes, so the mount's canActivateChild names them by their top-level path.
 */
export const inventoryScreenGuard = mountedScreenGuard('inventory', INVENTORY_SCREEN_PERMISSIONS);
