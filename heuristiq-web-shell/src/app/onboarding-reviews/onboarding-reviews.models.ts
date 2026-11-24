import { OnboardingSubmissionStatus } from '../onboarded-users/onboarded-users.models';

/** Mirrors com.heuristiq.onboardingservice.dto.ReviewItemResponse -- an open review task + its submission. */
export interface OnboardingReviewItem {
  taskId: string;
  submissionId: string;
  /** The short reference of the submission, e.g. DRV-2026-000042. */
  reference?: string | null;
  /** The workflow instance holding this submission's end-to-end history. */
  workflowInstanceId?: string | null;
  typeCode: string;
  subjectId: string | null;
  answers: Record<string, unknown>;
  status: OnboardingSubmissionStatus;
  createdAt?: string;
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

/** Mirrors com.heuristiq.onboardingservice.dto.RejectRequest. */
export interface RejectReviewRequest {
  reason: string;
}
