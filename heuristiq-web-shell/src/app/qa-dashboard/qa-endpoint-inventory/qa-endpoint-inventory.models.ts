/** Shapes of the API endpoint inventory (Rider_ConfigService, /api/v1/config/qa/endpoints), section 7 of docs/QA_DASHBOARD_DESIGN.md. */

import { QaModuleFacet } from '../qa-dashboard.models';

/** The quick filter above the table: '' = every endpoint. */
export type QaEndpointQuick = '' | 'tested' | 'untested' | 'undocumented';

export interface QaEndpointCoverage {
  /** Endpoints in the gateway privilege catalog. */
  documented: number;
  /** Documented endpoints at least one scenario called. */
  tested: number;
  untested: number;
  coveragePct: number;
  /** Endpoints that were called but are not in the catalog. */
  undocumentedTested: number;
  totalEndpoints: number;
  modulesCovered: number;
  subModulesCovered: number;
  /** Distinct test cases that called at least one endpoint. */
  mappedScenarios: number;
  /** When the Jenkins publish last synced the catalog; null when it never did. */
  lastSyncedAt: string | null;
}

export interface QaEndpointItem {
  id: string;
  method: string;
  path: string;
  privilegeName: string | null;
  module: string;
  subModule: string;
  documented: boolean;
  /** Scenario executions that called it. */
  hitCount: number;
  /** Calls made in total. */
  callCount: number;
  lastHitAt: string | null;
  /** Test case keys that called it, newest first. */
  mappedTestKeys: string[];
  mappedCount: number;
}

export interface QaEndpointPage {
  coverage: QaEndpointCoverage;
  items: QaEndpointItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** A test case that called an endpoint. */
export interface QaEndpointTest {
  /** The test case id (not the test case number). */
  id: string;
  tcId: string | null;
  testKey: string;
  name: string;
  status: string;
  lastRunAt: string | null;
}

export interface QaEndpointDetail extends QaEndpointItem {
  description: string;
  operation: string;
  tests: QaEndpointTest[];
}

export interface QaEndpointFilterOptions {
  modules: QaModuleFacet[];
  methods: string[];
}

/** What the toolbar sends ('' = not filtered). */
export interface QaEndpointQuery {
  q: string;
  module: string;
  subModule: string;
  method: string;
  quick: QaEndpointQuick;
}

/** The first test case keys of a row and how many more there are. */
export interface QaMappedChips {
  visible: string[];
  extra: number;
}
