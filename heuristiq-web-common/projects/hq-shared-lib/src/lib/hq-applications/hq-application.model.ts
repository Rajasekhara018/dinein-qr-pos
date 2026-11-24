/**
 * One active application of the session organization, as UAM's lookup returns it
 * (GET /api/v1/uam/identity/applications). Codes are fixed catalogue codes and are never translated.
 */
export interface HqApplication {
  code: string;
  name: string;
  /** UAM ApplicationType code (WEB, MOBILE, ...). */
  type: string;
  displayOrder: number;
  description?: string;
  /** Generic icon name (Material icon) the app may show next to the name. */
  iconHint?: string;
}
