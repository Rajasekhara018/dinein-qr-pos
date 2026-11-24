/** One audit event of the signed-in user, as Rider_AuditService returns it (only the fields the profile shows). */
export interface MyActivityEvent {
  id: number;
  eventType: string;
  occurredAt: string;
  screenKey?: string | null;
  method?: string | null;
  path?: string | null;
  statusCode?: number | null;
  outcome?: string | null;
  ipAddress?: string | null;
  feature?: string | null;
}

export interface MyActivityPage {
  content: MyActivityEvent[];
  totalElements?: number;
}

/** `CommonResponseObject` of Rider_AuditService. */
export interface MyActivityEnvelope<T> {
  success: boolean;
  message?: string;
  responseObject: T | null;
}
