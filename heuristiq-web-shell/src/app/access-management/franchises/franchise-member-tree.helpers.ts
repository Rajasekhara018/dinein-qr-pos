import { Franchise, FranchiseApplication, FranchiseMember } from './franchise.models';
import { isDriverApplication, placesUsed } from './franchise-application.helpers';

/** One franchise under an application: the franchise and its terms for that application (places used and allowed). */
export interface TreeFranchise {
  franchise: Franchise;
  application: FranchiseApplication;
}

/** An application with the franchises that run it and the places they use in total. */
export interface TreeApplication {
  applicationCode: string;
  franchises: TreeFranchise[];
  used: number;
  limit: number;
}

/** The agents (or, for the driver application, the drivers) of one franchise, read a page at a time when its branch is opened. */
export interface TreeBranch {
  loading: boolean;
  failed: boolean;
  members: FranchiseMember[];
  total: number;
  page: number;
}

/** The key of a franchise's branch under an application. */
export function branchKey(applicationCode: string, franchiseId: string): string {
  return `${applicationCode}|${franchiseId}`;
}

/**
 * The franchises grouped by application (an application with several franchises lists each once), applications ordered by
 * `nameOf`, franchises by code. A franchise matching `query` (its code or owner, case-insensitive) is kept; `applicationCode`
 * keeps one application only.
 */
export function groupByApplication(
  franchises: readonly Franchise[], nameOf: (code: string) => string, query = '', applicationCode = '',
): TreeApplication[] {
  const needle = query.trim().toLowerCase();
  const matches = (f: Franchise) => !needle
    || [f.code, f.name, f.ownerName, f.ownerEmail].some((v) => (v ?? '').toLowerCase().includes(needle));
  const groups = new Map<string, TreeApplication>();
  for (const franchise of franchises) {
    if (!matches(franchise)) continue;
    for (const application of franchise.applications ?? []) {
      if (applicationCode && application.applicationCode !== applicationCode) continue;
      const group = groups.get(application.applicationCode)
        ?? { applicationCode: application.applicationCode, franchises: [], used: 0, limit: 0 };
      group.franchises.push({ franchise, application });
      group.used += placesUsed(application);
      group.limit += application.agentLimit ?? 0;
      groups.set(application.applicationCode, group);
    }
  }
  const byName = (a: TreeApplication, b: TreeApplication) => nameOf(a.applicationCode).localeCompare(nameOf(b.applicationCode));
  return [...groups.values()]
    .map((g) => ({ ...g, franchises: [...g.franchises].sort((a, b) => a.franchise.code.localeCompare(b.franchise.code)) }))
    .sort(byName);
}

/** Every application code the franchises run, for the application filter. */
export function applicationCodesOf(franchises: readonly Franchise[]): string[] {
  return [...new Set(franchises.flatMap((f) => (f.applications ?? []).map((a) => a.applicationCode)))];
}

/** True when one of the franchises runs the driver application (its places used are then read live). */
export function hasDriverApplication(franchises: readonly Franchise[]): boolean {
  return franchises.some((f) => (f.applications ?? []).some((a) => isDriverApplication(a.applicationCode)));
}

/**
 * The franchises with each driver application's `driverCount` taken from a live read of the same franchises (matched by franchise
 * and application), so the places used agree with the live driver list; agents' figures are left as they are.
 */
export function withLiveDriverCounts(franchises: readonly Franchise[], live: readonly Franchise[]): Franchise[] {
  const counts = new Map<string, number>();
  for (const franchise of live) {
    for (const application of franchise.applications ?? []) {
      if (isDriverApplication(application.applicationCode)) {
        counts.set(branchKey(application.applicationCode, franchise.unitId), application.driverCount ?? 0);
      }
    }
  }
  return franchises.map((franchise) => ({
    ...franchise,
    applications: (franchise.applications ?? []).map((application) => {
      const live = counts.get(branchKey(application.applicationCode, franchise.unitId));
      return live === undefined ? application : { ...application, driverCount: live };
    }),
  }));
}
