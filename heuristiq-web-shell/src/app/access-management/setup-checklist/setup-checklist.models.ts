/** One subscribed application's set-up; null means the organization's model does not need that item. */
export interface SetupApplication {
  applicationCode: string;
  commissionPlan: boolean;
  packageTemplate: boolean | null;
  franchisePlan: boolean | null;
  complete: boolean;
}

/** A franchise application that covers no place; {@code locationBased} means it gets no sale until one is set. */
export interface SetupPlacelessApplication {
  franchiseId: string;
  franchiseCode: string;
  franchiseStatus: string;
  applicationCode: string;
  locationBased: boolean;
}

/** An operating city (Config) not linked to a usable place; {@code locationId} is the stale link, null when there is none. */
export interface SetupCityWithoutPlace {
  cityCode: string;
  cityName: string;
  locationId: string | null;
}

/** What the organization has and still lacks for its operating model (GET /api/v1/franchise/setup-checklist). */
export interface SetupChecklist {
  operatingModel: string;
  franchisesUsed: boolean;
  placesLoaded: boolean;
  applications: SetupApplication[];
  complete: boolean;
  /** Absent from an older service: treated as empty. */
  applicationsWithoutPlace?: SetupPlacelessApplication[];
  citiesWithoutPlace?: SetupCityWithoutPlace[];
}

/** The state of one checklist item on the screen. */
export type SetupItemState = 'done' | 'missing' | 'notNeeded';

/** The application items the screen shows, in order. */
export type SetupApplicationItem = 'commissionPlan' | 'packageTemplate' | 'franchisePlan';
