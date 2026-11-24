// Thin per-app wrapper: the search logic lives in hq-shared-lib (HqWebSearchApiService). Each app keeps its own
// root-provided subclass so the host and every federated remote use their own model catalogue and base URL.
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { HqWebSearchApiService } from 'hq-shared-lib';
import { WEB_SEARCH_MODELS } from './web-search-models';
import { environment } from '../../../environments/environment';

export type { HqWebSearchPage as WebSearchPage } from 'hq-shared-lib';

/** Explicit screen-service calls to the same Elasticsearch GetAll API used by UAM and Franchise. */
@Injectable({ providedIn: 'root' })
export class WebSearchApiService extends HqWebSearchApiService {
  constructor() {
    super(inject(HttpClient), {
      models: WEB_SEARCH_MODELS,
      baseUrl: () => environment.gatewayUrl.replace(/\/$/, '') + '/v1/search/elastic',
    });
  }
}
