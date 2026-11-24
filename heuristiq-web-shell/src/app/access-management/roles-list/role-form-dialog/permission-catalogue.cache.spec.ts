import { beforeEach, describe, expect, it } from 'vitest';
import { Permission } from '../../../shared/models/uam.models';
import {
  PERMISSION_CATALOGUE_TTL_MS, cachedPermissionCatalogue, forgetPermissionCatalogue, rememberPermissionCatalogue,
} from './permission-catalogue.cache';

describe('permission catalogue cache', () => {
  const catalogue = [{ name: 'USER_LIST' } as unknown as Permission];

  beforeEach(() => forgetPermissionCatalogue());

  it('has nothing until a catalogue is remembered', () => {
    expect(cachedPermissionCatalogue()).toBeNull();
  });

  it('hands back what was remembered while it is fresh, and nothing once it is stale', () => {
    rememberPermissionCatalogue(catalogue, 1_000);

    expect(cachedPermissionCatalogue(1_000 + PERMISSION_CATALOGUE_TTL_MS - 1)).toEqual(catalogue);
    expect(cachedPermissionCatalogue(1_000 + PERMISSION_CATALOGUE_TTL_MS)).toBeNull();
  });

  it('forgets on request', () => {
    rememberPermissionCatalogue(catalogue);
    forgetPermissionCatalogue();

    expect(cachedPermissionCatalogue()).toBeNull();
  });
});
