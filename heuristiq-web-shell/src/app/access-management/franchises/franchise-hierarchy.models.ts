/** What `GET /franchise/{id}/hierarchy` answers: where a franchise sits and what is below it. */
export interface FranchiseHierarchy {
  franchiseId: string;
  parentFranchiseId: string | null;
  childFranchiseIds: string[];
  descendantFranchiseIds: string[];
}
