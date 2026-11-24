import { HqAmount } from 'hq-shared-lib';

export type OnboardingStatus = 'ONBOARDING' | 'ONBOARDED';
export type ReviewStatus = 'SUBMITTED' | 'VERIFIED' | 'REJECTED';
export type ChecklistState = 'MISSING' | 'DONE' | 'SUBMITTED' | 'VERIFIED' | 'REJECTED';
export type ReviewDecision = 'VERIFY' | 'REJECT';

export interface OnboardingChecklist {
  legalProfile: ChecklistState;
  kyc: ChecklistState;
  bankAccount: ChecklistState;
  agreement: ChecklistState;
}

export interface KycDocument {
  id: string;
  type: string;
  number: string | null;
  dmsDocumentId: string;
  fileName: string;
  contentType: string | null;
  status: ReviewStatus;
  rejectReason: string | null;
  submittedBy: string | null;
  submittedAt: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
}

export interface BankAccount {
  holderName: string;
  country: string;
  currency: string;
  bankName: string | null;
  accountNumber: string | null;
  iban: string | null;
  routingCode: string | null;
  routingType: string | null;
  proofDmsDocumentId: string;
  proofFileName: string;
  status: ReviewStatus;
  rejectReason: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  version: number | null;
  updatedAt: string | null;
  updatedBy: string | null;
}

export interface Agreement {
  agreementNumber: string;
  signedOn: string;
  startsOn: string;
  endsOn: string | null;
  renewal: string;
  exclusiveTerritory: boolean;
  securityDeposit: HqAmount | null;
  signedCopyDmsDocumentId: string;
  signedCopyFileName: string;
  version: number | null;
  recordedBy: string | null;
  recordedAt: string | null;
}

/** Everything about one franchise's onboarding, as the franchise service reads it. */
export interface FranchiseOnboarding {
  franchiseId: string;
  code: string;
  onboardingStatus: OnboardingStatus;
  activatedAt: string | null;
  activatedBy: string | null;
  checklist: OnboardingChecklist;
  canActivate: boolean;
  missing: string[];
  kycDocuments: KycDocument[];
  bankAccount: BankAccount | null;
  agreement: Agreement | null;
}

export interface KycDocumentRequest {
  type: string;
  number: string | null;
  dmsDocumentId: string;
  fileName: string;
  contentType: string | null;
}

export interface BankAccountRequest {
  holderName: string;
  country: string;
  currency: string;
  bankName: string | null;
  accountNumber: string | null;
  iban: string | null;
  routingCode: string | null;
  proofDmsDocumentId: string;
  proofFileName: string;
  version: number | null;
}

export interface AgreementRequest {
  agreementNumber: string;
  signedOn: string;
  startsOn: string;
  endsOn: string | null;
  renewal: string;
  exclusiveTerritory: boolean;
  securityDeposit: HqAmount | null;
  signedCopyDmsDocumentId: string;
  signedCopyFileName: string;
  version: number | null;
}

export interface ReviewRequest {
  decision: ReviewDecision;
  reason: string | null;
}
