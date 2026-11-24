import { ApplicationType } from '../../shared/models/uam.models';

/** UAM path (below environment.uamApiUrl) of the session organization's applications, paged. */
export const APPLICATIONS_LIST_PATH = '/applications';

/** The list's filters, sent as query parameters (empty ones are left out). */
export interface ApplicationsListFilters {
  code: string;
  name: string;
  type: ApplicationType | '';
}

export const EMPTY_APPLICATIONS_FILTERS: Readonly<ApplicationsListFilters> = { code: '', name: '', type: '' };

/** Types offered by the type filter (backend codes, shown as they are). */
export const APPLICATION_TYPE_FILTER_VALUES: readonly ApplicationType[] = ['WEB', 'MOBILE', 'DESKTOP', 'API', 'MICROSERVICE', 'BATCH', 'INTERNAL', 'EXTERNAL'];

/** `?application=<CODE>` opens the list filtered on that code. */
export const APPLICATION_QUERY_PARAM = 'application';
