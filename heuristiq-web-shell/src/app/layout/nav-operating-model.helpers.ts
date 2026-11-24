import type { OperatingModel } from '../access-management/organization-operating-model/organization-operating-model.types';
import type { HomeLoadStatus } from '../home/home.models';

/**
 * The items that fit how the session's organization runs. Items that name no models always stay; the others stay only once
 * the model is known ('ready') and is one of theirs. While the lookup loads or after it failed they are left out (fail
 * closed, as for subscriptions), so a company-run organization never flashes franchise menus. Only a hint: the screens and
 * endpoints behind the items keep their own privilege checks.
 */
export function filterByOperatingModel<T extends { operatingModels?: ReadonlyArray<OperatingModel> }>(
  items: ReadonlyArray<T>,
  status: HomeLoadStatus,
  model: OperatingModel | null,
): T[] {
  return items.filter((item) => {
    if (!item.operatingModels?.length) return true;
    return status === 'ready' && model !== null && item.operatingModels.includes(model);
  });
}
