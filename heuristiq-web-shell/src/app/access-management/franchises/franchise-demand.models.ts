import { LocationType } from './franchise.models';

/** One place of the demand planning view: its votes and those of every place below it, and whether a franchise covers it. */
export interface PlaceDemand {
  locationId: string;
  code: string;
  name: string;
  type: LocationType;
  total: number;
  customers: number;
  providers: number;
  partners: number;
  covered: boolean;
  /** Code of the active franchise covering the place (it or a place above it); null when none does. */
  coveringFranchiseCode: string | null;
}

/** GET /demand-votes/summary: one application, the places under a parent (the top places without one), one page. */
export interface DemandQuery {
  applicationCode: string;
  parentId?: string;
  page: number;
  size: number;
}

/** One step of the drill-down path shown above the table. */
export interface DemandTrailStep {
  locationId: string;
  name: string;
}
