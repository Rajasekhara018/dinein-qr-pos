import { TableColumnSpec } from '../../shared/table-columns.helpers';
import { QA_BASE_PATH } from '../qa-dashboard.constants';
import { QaEndpointItem, QaEndpointQuery, QaEndpointQuick, QaEndpointTest } from './qa-endpoint-inventory.models';

/** Columns of the endpoint inventory; module is plain text, the rest are drawn by the screen's templates. */
export const QA_ENDPOINT_COLUMNS: readonly TableColumnSpec<QaEndpointItem>[] = [
  { key: 'method', labelKey: 'qaDashboard.endpointInventory.method' },
  { key: 'path', labelKey: 'qaDashboard.endpointInventory.endpoint' },
  { key: 'module', labelKey: 'qaDashboard.columns.module' },
  { key: 'subModule', labelKey: 'qaDashboard.columns.subModule' },
  { key: 'hits', labelKey: 'qaDashboard.endpointInventory.hits' },
  { key: 'mapped', labelKey: 'qaDashboard.endpointInventory.mappedScenariosColumn' },
];

/** Columns of the test cases that called one endpoint, in its detail dialog. */
export const QA_ENDPOINT_TEST_COLUMNS: readonly TableColumnSpec<QaEndpointTest>[] = [
  { key: 'tcId', labelKey: 'qaDashboard.columns.tcId' },
  { key: 'name', labelKey: 'qaDashboard.columns.testCase' },
  { key: 'status', labelKey: 'qaDashboard.columns.status' },
  { key: 'date', labelKey: 'qaDashboard.columns.date' },
];

/** Gateway paths (below environment.apiUrl) of the endpoint inventory. */
export const QA_ENDPOINTS_PATH = `${QA_BASE_PATH}/endpoints`;
export const QA_ENDPOINTS_EXPORT_PATH = `${QA_BASE_PATH}/endpoints/export`;
export const QA_ENDPOINTS_FILTERS_PATH = `${QA_BASE_PATH}/endpoints/filters`;

/** Rows per page the user can choose (the API caps a page at 50) and the one it starts with. */
export const QA_ENDPOINT_PAGE_SIZES: readonly number[] = [10, 20, 50];
export const QA_ENDPOINT_DEFAULT_PAGE_SIZE = 20;

/** How many mapped test cases a row lists before it says "+n". */
export const QA_ENDPOINT_CHIP_LIMIT = 3;

/** The quick filter choices, in the order they are shown, with the translation key of each. */
export const QA_ENDPOINT_QUICK_FILTERS: ReadonlyArray<{ value: QaEndpointQuick; labelKey: string }> = [
  { value: '', labelKey: 'qaDashboard.endpointInventory.quick.all' },
  { value: 'tested', labelKey: 'qaDashboard.endpointInventory.quick.tested' },
  { value: 'untested', labelKey: 'qaDashboard.endpointInventory.quick.untested' },
  { value: 'undocumented', labelKey: 'qaDashboard.endpointInventory.quick.undocumented' },
];

/** The look of each HTTP method's badge; the badge always shows the method's name too, so colour is never the only cue. */
export const QA_METHOD_CLASSES: Readonly<Record<string, string>> = {
  GET: 'qa-method--get',
  POST: 'qa-method--post',
  PUT: 'qa-method--put',
  PATCH: 'qa-method--patch',
  DELETE: 'qa-method--delete',
};
export const QA_METHOD_CLASS_OTHER = 'qa-method--other';

/** Name of the file the export saves. */
export const QA_ENDPOINTS_EXPORT_FILE_NAME = 'qa-api-endpoints.csv';

/** The empty query. */
export const QA_ENDPOINT_EMPTY_QUERY: Readonly<QaEndpointQuery> = { q: '', module: '', subModule: '', method: '', quick: '' };

/** The test case table opened from the details: where it lives and the query parameter it searches with. */
export const QA_ENDPOINT_TEST_CASE_SEARCH_PARAM = 'q';

/** The dialog that shows one endpoint. */
export const QA_ENDPOINT_DIALOG = { width: 'min(760px, 94vw)', maxWidth: '94vw', autoFocus: false } as const;

export const QA_ENDPOINT_MESSAGE_KEYS = {
  exportFailed: 'qaDashboard.endpointInventory.exportFailed',
} as const;
