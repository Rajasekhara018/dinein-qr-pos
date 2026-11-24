import { utf8Length } from '../qa-test-case-studio/qa-studio.helpers';
import {
  QA_API_DOC_MAX_BYTES,
  QA_API_DOC_PREFIX,
  QA_API_MODULE_PATTERN,
} from './qa-api-documents.constants';
import { QaApiDocInfo, QaApiDocument, QaApiModule } from './qa-api-documents.models';

/** The name of a module's API document. */
export function documentNameOf(module: string): string {
  return `${QA_API_DOC_PREFIX}${module}`;
}

/** The module an API document belongs to: the one it says, or the one in its name; null for a document that is not an API document. */
export function moduleOfDocument(doc: Pick<QaApiDocInfo, 'name' | 'module'>): string | null {
  if (doc.module && QA_API_MODULE_PATTERN.test(doc.module)) return doc.module;
  if (!doc.name.startsWith(QA_API_DOC_PREFIX)) return null;
  const fromName = doc.name.slice(QA_API_DOC_PREFIX.length);
  return QA_API_MODULE_PATTERN.test(fromName) ? fromName : null;
}

/** The module code an inventory module name becomes: lower case, anything but letters and digits turned into a dash (`ADMINISTRATION` is `administration`). */
export function moduleCodeOf(inventoryModule: string): string {
  return inventoryModule.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
}

/** True when a module name is one the API accepts (a lower-case area tag or service name). */
export function isValidModule(module: string): boolean {
  return QA_API_MODULE_PATTERN.test(module);
}

/**
 * The left-hand list: every module the inventory knows plus every module that has a document, each with its count and
 * its document, in alphabetical order. A module with no document has `document: null` ("No document yet").
 */
export function buildModuleList(
  inventoryModules: readonly string[],
  documents: readonly QaApiDocInfo[],
  counts: ReadonlyMap<string, number>,
): QaApiModule[] {
  const byModule = new Map<string, QaApiDocInfo>();
  documents.forEach((doc) => {
    const module = moduleOfDocument(doc);
    if (module) byModule.set(module, doc);
  });
  // The catalog writes its modules in capitals (ADMINISTRATION): the API document of a module uses the lower-case code, and the
  // inventory is still asked with the catalog's own name.
  const inventoryByCode = new Map<string, string>();
  inventoryModules.forEach((name) => {
    const code = moduleCodeOf(name);
    if (isValidModule(code) && !inventoryByCode.has(code)) inventoryByCode.set(code, name);
  });
  const names = new Set<string>([...inventoryByCode.keys(), ...byModule.keys()]);
  return [...names]
    .sort((a, b) => a.localeCompare(b))
    .map((module) => ({
      module,
      inventoryModule: inventoryByCode.get(module) ?? null,
      endpointCount: counts.get(module) ?? null,
      document: byModule.get(module) ?? null,
    }));
}

/** The problem with a draft: nothing written, or more than the API accepts. */
export function markdownIssue(markdown: string): 'empty' | 'tooLarge' | null {
  if (!markdown.trim()) return 'empty';
  return utf8Length(markdown) > QA_API_DOC_MAX_BYTES ? 'tooLarge' : null;
}

export function normalizeApiDocInfo(raw: Partial<QaApiDocInfo>): QaApiDocInfo {
  return {
    name: raw.name ?? '',
    title: raw.title ?? '',
    updatedAt: raw.updatedAt ?? '',
    sizeBytes: raw.sizeBytes ?? 0,
    category: raw.category ?? 'QA',
    module: raw.module ?? null,
  };
}

export function normalizeApiDocument(raw: Partial<QaApiDocument> | null | undefined): QaApiDocument {
  return { ...normalizeApiDocInfo(raw ?? {}), markdown: raw?.markdown ?? '' };
}
