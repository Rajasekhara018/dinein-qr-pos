import { mountedScreenGuard } from '../auth/mounted-screen-guard';
import { FOOD_PARTNER_SCREEN_PERMISSIONS } from './food-admin.constants';

/** Checks each partner screen's permission key when it opens; the join screen has none (open to any signed-in user). */
export const foodPartnerScreenGuard = mountedScreenGuard('food-partner', FOOD_PARTNER_SCREEN_PERMISSIONS);
