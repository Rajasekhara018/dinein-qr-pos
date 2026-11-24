import { describe, expect, it } from 'vitest';
import { isStaleChunkError } from './stale-bundle.helpers';

describe('isStaleChunkError', () => {
  it('recognises a lazy chunk that is gone after a redeploy', () => {
    expect(isStaleChunkError(new TypeError('404 Not Found https://example.com/chunk-CD3QLMRN.js'))).toBe(true);
    expect(isStaleChunkError(new TypeError('Failed to fetch dynamically imported module: https://example.com/chunk-A.js'))).toBe(true);
    expect(isStaleChunkError(new Error('Loading chunk 12 failed.'))).toBe(true);
  });

  it('leaves ordinary errors alone', () => {
    expect(isStaleChunkError(new Error('Cannot read properties of undefined'))).toBe(false);
    expect(isStaleChunkError(new Error('Request failed with 404 for /api/users'))).toBe(false);
    expect(isStaleChunkError(null)).toBe(false);
  });
});
