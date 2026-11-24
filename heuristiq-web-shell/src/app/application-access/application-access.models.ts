/** Who a grant applies to. */
export type AccessSubjectType = 'ROLE' | 'USER';

/** One grant, as GET /application-access?applicationCode= returns it. */
export interface ApplicationAccessGrant {
  id: string;
  applicationCode: string;
  subjectType: AccessSubjectType;
  /** User id for USER, role code for ROLE. */
  subjectId: string;
  active: boolean | null;
  note: string | null;
  grantedBy: string | null;
  grantedAt: string | null;
}

/** What a list call ended in. */
export type AccessListState = 'ready' | 'unavailable' | 'forbidden';

export interface AccessListResult {
  state: AccessListState;
  grants: ApplicationAccessGrant[];
}

/** Outcome of granting one subject (the status is the HTTP status of a failure). */
export interface GrantOutcome {
  subjectId: string;
  ok: boolean;
  status: number;
}
