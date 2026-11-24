import { Permission } from '../../../shared/models/uam.models';

/** How long the permission catalogue read for the role editor is reused, in milliseconds. */
export const PERMISSION_CATALOGUE_TTL_MS = 5 * 60 * 1000;

let cached: { at: number; permissions: readonly Permission[] } | null = null;

/** The catalogue read recently, or null when there is none or it is stale. Opening a role no longer re-reads about 2,300 rows each time. */
export function cachedPermissionCatalogue(now: number = Date.now()): readonly Permission[] | null {
  return cached && now - cached.at < PERMISSION_CATALOGUE_TTL_MS ? cached.permissions : null;
}

/** Remembers a completely read catalogue (never a partial one). */
export function rememberPermissionCatalogue(permissions: readonly Permission[], now: number = Date.now()): void {
  cached = { at: now, permissions };
}

/** Forgets the catalogue, so the next editor reads it again (after a sign-out, or in tests). */
export function forgetPermissionCatalogue(): void {
  cached = null;
}
