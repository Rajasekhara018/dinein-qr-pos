export interface User {
  id: string;
  role: string;
  userType?: 'OPS' | 'SUPER_ADMIN' | 'ADMIN' | 'CUSTOMER' | 'DRIVER' | 'VENDOR';
  email?: string;
  name?: string;
  avatarUrl?: string;
  mustChangePassword: boolean;
  totpEnabled: boolean;
}

export interface LoginResponse {
  userId: string;
  token?: string;
  accessToken: string;
  refreshToken?: string;
  username?: string;
  role: string;
  roles?: string[];
  email?: string;
  fullName?: string;
  userType?: 'OPS' | 'SUPER_ADMIN' | 'ADMIN' | 'CUSTOMER' | 'DRIVER' | 'VENDOR';
  forcePasswordChange?: boolean;
  /** The user's saved IANA timezone; null or absent when none (display hint, UAM sign-in/refresh). */
  timezone?: string | null;
  /** The session organization's IANA timezone; null or absent when none. */
  organizationTimeZone?: string | null;
  /** The session organization's id / code / name (informational; the token's tenantId claim stays authoritative). Null or absent when none. */
  organizationId?: string | null;
  organizationCode?: string | null;
  organizationName?: string | null;
}

export interface Pagination<T> {
  items: T[];
  total: number;
  page: number;
  limit: number;
}

// TODO: confirm the customer-facing notification type set once the backend
// event catalog for this app is defined — this mirrors AdminWeb's shape.
export type InAppNotificationType =
  | 'BOOKING_CONFIRMED' | 'BOOKING_CANCELLED'
  | 'DRIVER_ASSIGNED' | 'DRIVER_ARRIVED'
  | 'RIDE_COMPLETED' | 'REFUND_REQUESTED' | 'SYSTEM_ALERT';

export interface InAppNotification {
  id: string;
  adminUserId: string;
  title: string;
  message: string;
  type: InAppNotificationType;
  referenceId?: string;
  referenceType?: string;
  read: boolean;
  metadata?: Record<string, unknown>;
  createdAt: string;
}

export interface InAppNotificationPage {
  content: InAppNotification[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  last: boolean;
}
