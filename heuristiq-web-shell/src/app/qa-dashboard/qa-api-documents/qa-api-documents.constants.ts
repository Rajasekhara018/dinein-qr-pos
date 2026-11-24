import { TableColumnSpec } from '../../shared/table-columns.helpers';
import { QA_DOCUMENTS_PATH } from '../../system-qa/system-qa.constants';
import { QaEndpointItem } from '../qa-endpoint-inventory/qa-endpoint-inventory.models';

/** Columns of the module's endpoint table; the cells are drawn by the screen's templates. */
export const QA_API_DOC_ENDPOINT_COLUMNS: readonly TableColumnSpec<QaEndpointItem>[] = [
  { key: 'method', labelKey: 'qaDashboard.endpointInventory.method' },
  { key: 'path', labelKey: 'qaDashboard.endpointInventory.endpoint' },
  { key: 'tested', labelKey: 'qaDashboard.apiDocuments.tested' },
];

/** The API documents live with the other QA documents, told apart by their category. */
export const QA_API_DOCUMENTS_PATH = QA_DOCUMENTS_PATH;
export const QA_API_DOC_CATEGORY = 'API';

/** An API document is called `api-<module>`; a module is a lower-case area tag or service name. */
export const QA_API_DOC_PREFIX = 'api-';
export const QA_API_MODULE_PATTERN = /^[a-z][a-z0-9-]*$/;

/** Privilege codes (exact names from privilege.json). */
export const QA_API_DOC_PRIVILEGES = {
  list: 'CONFIG_QA_DOCUMENT_LIST',
  read: 'CONFIG_QA_DOCUMENT_READ',
  create: 'CONFIG_QA_DOCUMENT_CREATE',
  delete: 'CONFIG_QA_DOCUMENT_DELETE',
} as const;

/** The largest markdown the API accepts (512 KB), counted in bytes. */
export const QA_API_DOC_MAX_BYTES = 512 * 1024;

/** The module's endpoint table: how many rows it shows, and how many module counts are asked for at the same time. */
export const QA_API_DOC_ENDPOINT_ROWS = 50;
export const QA_API_DOC_COUNT_CONCURRENCY = 4;

/** The existing Swagger and Javadoc screens of the portal. */
export const QA_API_DOC_LINKS = {
  swagger: '/api-docs',
  javadoc: '/api-docs/java-doc',
  inventory: '/qa/endpoint-inventory',
} as const;

export const QA_API_DOC_MESSAGE_KEYS = {
  saved: 'qaDashboard.apiDocuments.saved',
  saveFailed: 'qaDashboard.apiDocuments.saveFailed',
  deleteTitle: 'qaDashboard.apiDocuments.deleteTitle',
  deleteMessage: 'qaDashboard.apiDocuments.deleteMessage',
  deleteConfirm: 'qaDashboard.apiDocuments.deleteConfirm',
  deleted: 'qaDashboard.apiDocuments.deleted',
  deleteFailed: 'qaDashboard.apiDocuments.deleteFailed',
  discardTitle: 'qaDashboard.apiDocuments.discardTitle',
  discardMessage: 'qaDashboard.apiDocuments.discardMessage',
  discardConfirm: 'qaDashboard.apiDocuments.discardConfirm',
  titleFor: 'qaDashboard.apiDocuments.defaultTitle',
} as const;

/** Where a new document starts: the module's name as the heading. */
export const QA_API_DOC_TEMPLATE = (heading: string): string => `# ${heading}\n\n`;
