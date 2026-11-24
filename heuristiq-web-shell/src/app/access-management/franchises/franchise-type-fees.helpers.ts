import { hqAmountCode, hqAmountValue } from 'hq-shared-lib';
import { FranchisePlan, LocationType } from './franchise.models';
import { MIN_AGENT_LIMIT } from './franchises.constants';
import { roundFee } from './franchises.helpers';
import { PAISE_PER_UNIT } from './franchise-type-fees.constants';

/** One line of the grid: the max agents and fee for every place of one type, and the plan already saved for it (if any). */
export interface TypeFeeRow {
  type: LocationType;
  /** The id of the saved type-default plan this line edits; null when the line would create one. */
  planId: string | null;
  agentLimit: number | null;
  fee: number | null;
  currency: string;
  savedAgentLimit: number | null;
  savedFee: number | null;
  savedCurrency: string;
}

/** What saving would do with a line. */
export type TypeFeeRowState = 'empty' | 'unchanged' | 'new' | 'changed' | 'incomplete';

/**
 * The grid for one application, country and parent (a state or district; '' = the whole country): a line per type, filled from the
 * type-default plans already saved for exactly that area.
 */
export function buildTypeFeeRows(
  types: readonly LocationType[], plans: readonly FranchisePlan[], countryCode: string, scopeLocationId: string, defaultCurrency: string,
): TypeFeeRow[] {
  return types.map((type) => {
    const saved = plans.find((plan) => plan.scope === 'TYPE_DEFAULT' && plan.scopeType === type
      && (plan.scopeLocationId ?? '') === scopeLocationId && (scopeLocationId !== '' || plan.countryCode === countryCode));
    // The saved fee is `{ code, value }` (an older bare number is read in the default currency).
    const fee = hqAmountValue(saved?.fee);
    const currency = hqAmountCode(saved?.fee, defaultCurrency);
    return {
      type, planId: saved?.id ?? null,
      agentLimit: saved?.agentLimit ?? null, fee, currency,
      savedAgentLimit: saved?.agentLimit ?? null, savedFee: fee, savedCurrency: currency,
    };
  });
}

function hasAgents(row: TypeFeeRow): boolean {
  return row.agentLimit !== null && Number.isFinite(row.agentLimit);
}

function hasFee(row: TypeFeeRow): boolean {
  return row.fee !== null && Number.isFinite(row.fee);
}

/** What saving does with a line: nothing (empty or as saved), create, change, or refuse because only one of the two values is given. */
export function typeFeeRowState(row: TypeFeeRow): TypeFeeRowState {
  const agents = hasAgents(row);
  const fee = hasFee(row);
  if (!agents && !fee) {
    return 'empty';
  }
  if (!agents || !fee || (row.agentLimit as number) < MIN_AGENT_LIMIT || (row.fee as number) < 0) {
    return 'incomplete';
  }
  if (row.planId === null) {
    return 'new';
  }
  const same = row.agentLimit === row.savedAgentLimit && roundFee(row.fee as number) === roundFee(row.savedFee as number)
    && row.currency === row.savedCurrency;
  return same ? 'unchanged' : 'changed';
}

/** The lines to create, the lines to change and the lines that cannot be saved yet. An empty line never deletes a saved plan. */
export function typeFeeChanges(rows: readonly TypeFeeRow[]): { create: TypeFeeRow[]; change: TypeFeeRow[]; incomplete: TypeFeeRow[] } {
  const states = rows.map((row) => ({ row, state: typeFeeRowState(row) }));
  return {
    create: states.filter((s) => s.state === 'new').map((s) => s.row),
    change: states.filter((s) => s.state === 'changed').map((s) => s.row),
    incomplete: states.filter((s) => s.state === 'incomplete').map((s) => s.row),
  };
}

/**
 * The franchise's wallet allocation for a line: the fee times the multiplier, to the paisa (rounded half up, as the backend does);
 * null while there is no fee.
 */
export function walletAllocation(fee: number | null, multiplier: number): number | null {
  if (fee === null || fee === undefined || !(fee > 0) || !(multiplier >= 0)) {
    return null;
  }
  return Math.round(fee * PAISE_PER_UNIT * multiplier) / PAISE_PER_UNIT;
}

/**
 * One agent's share of it: the allocation divided equally between the agent places, rounded down to the paisa so the shares never
 * add up to more (the amount credited to each new agent's wallet); null while the fee or the places are missing.
 */
export function walletPerAgent(fee: number | null, agentLimit: number | null, multiplier: number): number | null {
  const total = walletAllocation(fee, multiplier);
  if (total === null || agentLimit === null || agentLimit === undefined || !(agentLimit >= 1)) {
    return null;
  }
  return Math.floor(Math.round(total * PAISE_PER_UNIT) / agentLimit) / PAISE_PER_UNIT;
}
