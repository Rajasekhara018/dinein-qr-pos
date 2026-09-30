import {
  KioskUpsellPlacement,
  KioskUpsellRequest,
  KioskUpsellView,
} from '../../../core/api/models';

export type UpsellTriggerKind = 'ANY' | 'CATEGORY' | 'ITEM';

export const PLACEMENT_OPTIONS: { value: KioskUpsellPlacement; label: string }[] = [
  { value: 'ITEM_ADDED', label: 'Right after an item is added' },
  { value: 'CHECKOUT', label: 'At checkout' },
];

export const TRIGGER_OPTIONS: { value: UpsellTriggerKind; label: string }[] = [
  { value: 'ANY', label: 'Any order' },
  { value: 'CATEGORY', label: 'A category' },
  { value: 'ITEM', label: 'A specific item' },
];

export const MESSAGE_MAX = 80;

export function triggerKind(
  rule: Pick<KioskUpsellView, 'triggerItemId' | 'triggerCategoryId'>,
): UpsellTriggerKind {
  if (rule.triggerItemId != null) return 'ITEM';
  if (rule.triggerCategoryId != null) return 'CATEGORY';
  return 'ANY';
}

/** Names used in the sentence; missing ones (deleted items) show as `item #12`. */
export interface NameLookup {
  item(id: number): string;
  category(id: number): string;
}

export function nameLookup(
  items: readonly { id: number; name: string }[],
  categories: readonly { id: number; name: string }[],
): NameLookup {
  const itemNames = new Map(items.map((i) => [i.id, i.name]));
  const categoryNames = new Map(categories.map((c) => [c.id, c.name]));
  return {
    item: (id) => itemNames.get(id) ?? `item #${id}`,
    category: (id) => categoryNames.get(id) ?? `category #${id}`,
  };
}

/** "When Classic Burger is added, suggest Cold Coffee: 'Add a drink?'" */
export function describeUpsell(rule: KioskUpsellView, names: NameLookup): string {
  const suggest = `suggest ${names.item(rule.suggestedItemId)}`;
  const message = rule.message?.trim() ? `: '${rule.message.trim()}'` : '';
  const kind = triggerKind(rule);
  if (rule.placement === 'CHECKOUT') {
    const condition =
      kind === 'ITEM'
        ? `if the order has ${names.item(rule.triggerItemId as number)}, `
        : kind === 'CATEGORY'
          ? `if the order has an item from ${names.category(rule.triggerCategoryId as number)}, `
          : '';
    return `At checkout, ${condition}${suggest}${message}`;
  }
  const when =
    kind === 'ITEM'
      ? `${names.item(rule.triggerItemId as number)} is added`
      : kind === 'CATEGORY'
        ? `an item from ${names.category(rule.triggerCategoryId as number)} is added`
        : 'any item is added';
  return `When ${when}, ${suggest}${message}`;
}

export interface UpsellFormValue {
  triggerKind: UpsellTriggerKind;
  triggerCategoryId: number | null;
  triggerItemId: number | null;
  suggestedItemId: number | null;
  placement: KioskUpsellPlacement;
  message: string;
  active: boolean;
}

/** Only the id matching the chosen trigger kind is sent (they are mutually exclusive on the server). */
export function toUpsellRequest(
  v: UpsellFormValue & { suggestedItemId: number },
  sortOrder?: number | null,
): KioskUpsellRequest {
  return {
    triggerItemId: v.triggerKind === 'ITEM' ? v.triggerItemId : null,
    triggerCategoryId: v.triggerKind === 'CATEGORY' ? v.triggerCategoryId : null,
    suggestedItemId: v.suggestedItemId,
    placement: v.placement,
    message: v.message.trim() || null,
    sortOrder: sortOrder ?? null,
    active: v.active,
  };
}

/** Client-side mirror of the server rule: a trigger kind needs its picker filled. */
export function triggerProblem(v: UpsellFormValue): string | null {
  if (v.triggerKind === 'CATEGORY' && v.triggerCategoryId == null) return 'Choose a category.';
  if (v.triggerKind === 'ITEM' && v.triggerItemId == null) return 'Choose an item.';
  return null;
}
