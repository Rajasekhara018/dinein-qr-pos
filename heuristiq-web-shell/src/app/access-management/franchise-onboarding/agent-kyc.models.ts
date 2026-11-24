import { KycDocument } from './franchise-onboarding.models';

/** What `GET /franchise/{id}/agents/{memberId}/kyc` answers: the agent's documents (numbers masked) and where their KYC stands. */
export interface AgentKyc {
  memberId: string;
  /** VERIFIED once an identity proof is verified, PENDING while documents wait or were rejected, NONE when there are none. */
  status: 'VERIFIED' | 'PENDING' | 'NONE';
  documents: KycDocument[];
}
