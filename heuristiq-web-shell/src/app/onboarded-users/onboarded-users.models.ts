/** Mirrors com.heuristiq.onboardingservice.domain.SubmissionStatus exactly. */
export type OnboardingSubmissionStatus =
  | 'IN_PROGRESS'
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'ACCOUNT_CREATION_FAILED'
  | 'NEEDS_CORRECTION';

/** Mirrors com.heuristiq.onboardingservice.dto.DocumentRef -- a DOCUMENT field + its file in Rider_DMS. */
export interface OnboardingDocumentRef {
  fieldKey: string;
  label: string;
  documentCategory: string | null;
  documentId: string | null;
  fileName: string | null;
  contentType: string | null;
  sizeBytes: number | null;
  /** false = the answer isn't a document in the store (placeholder from an older app); null = unknown. */
  stored?: boolean | null;
}

/** Mirrors com.heuristiq.onboardingservice.dto.SubmissionResponse. */
export interface OnboardingSubmission {
  id: string;
  /** The short reference the submission is quoted by, e.g. DRV-2026-000042. */
  reference?: string | null;
  typeCode: string;
  schemaVersion: number;
  subjectId: string | null;
  status: OnboardingSubmissionStatus;
  answers: Record<string, unknown>;
  workflowInstanceId: string | null;
  rejectionReason: string | null;
  /** What the applicant has to fix, while the status is NEEDS_CORRECTION. */
  returnReason?: string | null;
  /** Workflow instances of earlier review rounds, oldest first. */
  previousWorkflowInstanceIds?: string[];
  /** Where the submission places the applicant (driver sign-up "Where will you drive?"). */
  tenantId?: string | null;
  organizationCode?: string | null;
  organizationName?: string | null;
  franchiseId?: string | null;
  franchiseCode?: string | null;
  franchiseName?: string | null;
  placeId?: string | null;
  placeName?: string | null;
  createdAt: string;
  updatedAt: string;
  /** Only present on the single-submission (detail) response. */
  documents?: OnboardingDocumentRef[] | null;
}

/** The placement part of a submission or review row (organization, franchise, place), with names when the owners answered. */
export interface OnboardingPlacement {
  /** Where the submission places the applicant (driver sign-up "Where will you drive?"). */
  tenantId?: string | null;
  organizationCode?: string | null;
  organizationName?: string | null;
  franchiseId?: string | null;
  franchiseCode?: string | null;
  franchiseName?: string | null;
  placeId?: string | null;
  placeName?: string | null;
}

/** Mirrors com.heuristiq.onboardingservice.dto.FranchiseRelinkResult. */
export interface FranchiseRelinkResult {
  submissionId: string;
  userId: string;
  franchiseId: string;
  memberId: string | null;
  /** false when the driver was already a member (idempotent repeat). */
  created: boolean;
}

/** Mirrors com.heuristiq.onboardingservice.dto.PagedResponse. */
export interface OnboardingPage<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Response envelope of Rider_OnboardingService (boolean `status`, unlike UAM's `success`). */
export interface OnboardingResponse<T> {
  status: boolean;
  data?: T;
  message?: string;
}

export interface OnboardingSubmissionFilters {
  type: string;
  status: string;
  query: string;
}

/** Independent backend gates; these do not replace the saved submission status. */
export interface SubmissionProgress {
  workflowName: string | null;
  overallStatus: string;
  reviewStatus: string;
  reviews: { taskId: string; name: string; status: string; actorId: string | null; decidedAt: string | null }[];
  documentsStatus: string;
  externalVerificationStatus: string;
  documents: { fieldKey: string; method: string | null; provider: string | null; status: string; missingInputs: string[] }[];
  accountStatus: string;
  blockers: string[];
}
