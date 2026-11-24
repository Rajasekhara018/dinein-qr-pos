import { HqStatusTone } from 'hq-shared-lib';
/** Route segments of an agent's KYC page: `franchises/:id/agents/:memberId/kyc` (administrator) and the same under `my-franchises` (owner). */
export const AGENT_ROUTE = 'agents';
export const AGENT_KYC_ROUTE = 'kyc';

/** Audit keys of the page; keys are queryable history, so never renamed or translated. */
export const AGENT_KYC_SCREEN_KEY = 'access_management.agent_kyc';
export const MY_AGENT_KYC_SCREEN_KEY = 'access_management.my_agent_kyc';

/** Privileges (exact names in privilege.json) behind the page, for an administrator and for an owner. */
export const AGENT_KYC_PRIVILEGES = {
  franchise: { add: 'FRANCHISE_AGENT_KYC_DOCUMENT_CREATE', review: 'FRANCHISE_AGENT_KYC_DOCUMENT_REVIEW_UPDATE' },
  myFranchise: { add: 'FRANCHISE_MINE_AGENT_KYC_DOCUMENT_CREATE', review: null },
} as const;

/** Tone of each KYC status chip. */
export const AGENT_KYC_STATUS_TONE: Readonly<Record<string, HqStatusTone>> = { VERIFIED: 'success', PENDING: 'warning', NONE: 'neutral' };
