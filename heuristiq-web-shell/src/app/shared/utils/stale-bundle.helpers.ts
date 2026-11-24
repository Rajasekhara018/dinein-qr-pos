/** Browser messages for a code chunk that could not be fetched (a 404 after a redeploy, or a dropped connection). */
const STALE_CHUNK_PATTERNS: readonly RegExp[] = [
  /Failed to fetch dynamically imported module/i,
  /error loading dynamically imported module/i,
  /Importing a module script failed/i,
  /Loading chunk [\w-]+ failed/i,
  /ChunkLoadError/i,
  /\b404\b.*\.m?js\b/i,
];

/**
 * Whether a navigation failed because a lazy-loaded code file no longer exists on the server. That happens to a
 * tab opened before a new version was deployed: it still asks for the old file names, and a reload fixes it.
 */
export function isStaleChunkError(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error ?? '');
  return STALE_CHUNK_PATTERNS.some((pattern) => pattern.test(message));
}
