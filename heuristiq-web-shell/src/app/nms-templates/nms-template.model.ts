import { NmsChannel } from './event-attributes.config';

export interface NmsTemplate {
  id?: string;
  event: string;
  channel: NmsChannel;
  name: string;
  subject?: string;
  body: string;
  externalTemplateId?: string;
  active: boolean;
  /** The organization that owns the template; absent for a shared one. */
  tenantId?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface NmsTemplateForm {
  event: string;
  channel: NmsChannel | '';
  name: string;
  subject: string;
  body: string;
  externalTemplateId: string;
  active: boolean;
  /** Only for the organization the user is working in, instead of shared by every organization. */
  organizationOnly: boolean;
}

/** One test message from the editor; the content is what is typed, not what is saved. */
export interface NmsTemplateTestSendRequest {
  channel: NmsChannel;
  recipient: string;
  subject?: string;
  body: string;
  variables: Record<string, string>;
}

export interface NmsTemplateTestSendResult {
  sent: boolean;
  provider?: string | null;
  errorCode?: string | null;
  message?: string | null;
}

export interface NmsLog {
  id?: string;
  event: string;
  channel: NmsChannel;
  recipient: string;
  success: boolean;
  errorMessage?: string;
  renderedBody?: string;
  sentAt?: string;
}

export interface NmsLogStats {
  totalSent: number;
  totalSuccess: number;
  totalFailed: number;
  byChannel: Record<string, { sent: number; success: number; failed: number }>;
}
