/**
 * How an organization runs its sales: by itself (DIRECT), through franchises (FRANCHISE), or through a franchise where one
 * covers the place and by itself elsewhere (HYBRID). Backend code, never translated.
 */
export type OperatingModel = 'DIRECT' | 'FRANCHISE' | 'HYBRID';

/** One change of model, as UAM answers it. */
export interface OperatingModelChange {
  model: OperatingModel;
  /** First day of the model, YYYY-MM-DD, in the organization's time zone. */
  effectiveFrom: string;
  /** True once it has started; such a change can no longer be changed. */
  inForce: boolean;
}

/** GET/PUT /organizations/{id}/operating-model. */
export interface OperatingModelView {
  organizationId: string;
  /** Today in the organization's time zone, YYYY-MM-DD. */
  today: string;
  currentModel: OperatingModel;
  schedule: OperatingModelChange[];
}

/** One change as sent to UAM. */
export interface ScheduledOperatingModelChange {
  model: OperatingModel;
  effectiveFrom: string;
}
