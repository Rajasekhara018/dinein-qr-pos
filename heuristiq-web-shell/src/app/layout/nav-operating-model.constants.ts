import type { OperatingModel } from '../access-management/organization-operating-model/organization-operating-model.types';

/** UAM self-service lookup of the session organization's operating model (organization taken from the token). */
export const MY_OPERATING_MODEL_PATH = '/identity/me/operating-model';

/**
 * Models in which an organization works through franchises. Menu items tagged with these are franchise/broker features
 * (franchise proposals, agent ratings, moving customers between agents) and are left out for a company-run (DIRECT)
 * organization, whose own staff serve its customers.
 */
export const FRANCHISE_OPERATING_MODELS: ReadonlyArray<OperatingModel> = ['FRANCHISE', 'HYBRID'];
