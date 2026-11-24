import { QaApiDocInfo } from './qa-api-documents.models';
import {
  buildModuleList,
  moduleCodeOf,
  documentNameOf,
  isValidModule,
  markdownIssue,
  moduleOfDocument,
  normalizeApiDocInfo,
  normalizeApiDocument,
} from './qa-api-documents.helpers';

const doc = (name: string, module: string | null = null): QaApiDocInfo => ({ name, title: name, updatedAt: '2026-10-26T10:00:00Z', sizeBytes: 10, category: 'API', module });

describe('API documents helpers', () => {
  it('names the document of a module', () => {
    expect(documentNameOf('uam')).toBe('api-uam');
    expect(documentNameOf('driver-earnings')).toBe('api-driver-earnings');
  });

  it('reads the module of a document from its module, or from its name', () => {
    expect(moduleOfDocument(doc('api-uam', 'uam'))).toBe('uam');
    expect(moduleOfDocument(doc('api-wallet'))).toBe('wallet');
    expect(moduleOfDocument(doc('api-wallet', 'BAD MODULE'))).toBe('wallet');
    expect(moduleOfDocument(doc('test-plan'))).toBeNull();
    expect(moduleOfDocument(doc('api-Bad Name'))).toBeNull();
    expect(moduleOfDocument(doc('api-'))).toBeNull();
  });

  it.each([
    ['uam', true],
    ['driver-earnings', true],
    ['a1', true],
    ['', false],
    ['Uam', false],
    ['1uam', false],
    ['u am', false],
    ['u_am', false],
  ])('checks the module name %j', (module, valid) => {
    expect(isValidModule(module)).toBe(valid);
  });

  it('lists the modules of the inventory and of the documents once each, in order, with count and document', () => {
    const modules = buildModuleList(['wallet', 'uam', '***'], [doc('api-uam', 'uam'), doc('api-train', 'train'), doc('notes')], new Map([['uam', 12], ['train', 3]]));
    expect(modules.map((m) => m.module)).toEqual(['train', 'uam', 'wallet']);
    expect(modules[0]).toMatchObject({ endpointCount: 3, inventoryModule: null, document: { name: 'api-train' } });
    expect(modules[1]).toMatchObject({ endpointCount: 12, inventoryModule: 'uam', document: { name: 'api-uam' } });
    expect(modules[2]).toEqual({ module: 'wallet', inventoryModule: 'wallet', endpointCount: null, document: null });
  });

  it('turns the capitals of the privilege catalog into module codes and keeps the catalog name to ask the inventory with', () => {
    expect(moduleCodeOf('ADMINISTRATION')).toBe('administration');
    expect(moduleCodeOf('Driver Earnings')).toBe('driver-earnings');
    expect(moduleCodeOf('--x--')).toBe('x');
    const modules = buildModuleList(['ADMINISTRATION', 'BUSINESS', 'Business', 'undocumented'], [doc('api-business', 'business')], new Map([['business', 7]]));
    expect(modules.map((m) => m.module)).toEqual(['administration', 'business', 'undocumented']);
    expect(modules[0]).toEqual({ module: 'administration', inventoryModule: 'ADMINISTRATION', endpointCount: null, document: null });
    expect(modules[1]).toMatchObject({ module: 'business', inventoryModule: 'BUSINESS', endpointCount: 7, document: { name: 'api-business' } });
  });

  it('gives an empty list when nothing is known', () => {
    expect(buildModuleList([], [], new Map())).toEqual([]);
  });

  it('checks a draft: something written, and at most 512 KB in bytes', () => {
    expect(markdownIssue('')).toBe('empty');
    expect(markdownIssue('  \n ')).toBe('empty');
    expect(markdownIssue('# Hello')).toBeNull();
    expect(markdownIssue('x'.repeat(512 * 1024))).toBeNull();
    expect(markdownIssue('x'.repeat(512 * 1024 + 1))).toBe('tooLarge');
    // 200 000 euro signs are 200 000 characters but 600 000 bytes.
    expect(markdownIssue('€'.repeat(200_000))).toBe('tooLarge');
  });

  it('fills what the API leaves out', () => {
    expect(normalizeApiDocInfo({ name: 'api-uam' })).toEqual({ name: 'api-uam', title: '', updatedAt: '', sizeBytes: 0, category: 'QA', module: null });
    expect(normalizeApiDocument(null)).toMatchObject({ name: '', markdown: '' });
    expect(normalizeApiDocument({ name: 'api-uam', markdown: '# x', module: 'uam', category: 'API' })).toMatchObject({ markdown: '# x', module: 'uam', category: 'API' });
  });
});
