import { isAppSubscribed } from '../home/home.helpers';
import type { HomeLoadStatus } from '../home/home.models';
import { CORE_SUITE_APP_KEYS } from '../home/home.constants';
import { appOfSection } from './nav-areas.helpers';
import type { NavSection } from './nav-items.constants';

/**
 * Whether a section belongs to an application that needs a subscription (an application that is not core tooling). A
 * persona-workspace section follows the application it was taken from (WORKSPACE_SECTION_APPS).
 */
export function sectionNeedsSubscription(section: NavSection): boolean {
  const app = appOfSection(section);
  return !!app && !CORE_SUITE_APP_KEYS.includes(app);
}

/**
 * The items whose application the organization may use. Sections outside every area and core applications always stay; the
 * others stay only once the subscriptions are known ('ready') and include their application. While the lookup loads or
 * after it failed they are left out (fail closed), so no flash of every application.
 */
export function filterBySubscription<T extends { section: NavSection }>(
  items: ReadonlyArray<T>,
  status: HomeLoadStatus,
  subscribedCodes: ReadonlyArray<string>,
): T[] {
  return items.filter((item) => {
    const app = appOfSection(item.section);
    if (!app || CORE_SUITE_APP_KEYS.includes(app)) return true;
    return status === 'ready' && isAppSubscribed(app, subscribedCodes);
  });
}

/** Whether some of the items wait on the subscription lookup (drives the sidebar's loading placeholder). */
export function awaitsSubscription(items: ReadonlyArray<{ section: NavSection }>, status: HomeLoadStatus): boolean {
  return status === 'loading' && items.some((item) => sectionNeedsSubscription(item.section));
}
