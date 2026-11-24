import { mountedScreenGuard } from '../auth/mounted-screen-guard';
import { FOOD_SCREEN_PERMISSIONS } from './food-admin.constants';

/**
 * Checks each Food screen's own permission key when it opens (a direct URL included). The remote (food admin-web) has no UAM
 * session of its own and declares no `data.permission`, so the mount's canActivateChild names the screens by their URL segment.
 */
export const foodScreenGuard = mountedScreenGuard('food', FOOD_SCREEN_PERMISSIONS);
