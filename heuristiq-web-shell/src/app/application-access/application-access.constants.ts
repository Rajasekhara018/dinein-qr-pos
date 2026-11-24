/** Privilege codes (exact `name`s in privilege.json) of the application-access calls. */
export const APPLICATION_ACCESS_PRIVILEGES = {
  list: 'APPLICATION_ACCESS_LIST',
  grant: 'APPLICATION_ACCESS_GRANT',
  revoke: 'APPLICATION_ACCESS_REVOKE',
} as const;

/** Path below the UAM base URL (environment.uamApiUrl = .../api/v1/uam). */
export const APPLICATION_ACCESS_PATH = '/application-access';
export const applicationAccessRevokePath = (id: string): string => `${APPLICATION_ACCESS_PATH}/${encodeURIComponent(id)}`;

/** Who a grant applies to (the backend enum). */
export const ACCESS_SUBJECT_ROLE = 'ROLE';
export const ACCESS_SUBJECT_USER = 'USER';

/** HTTP statuses the section answers with a specific sentence. */
export const ACCESS_STATUS = { badRequest: 400, forbidden: 403, notFound: 404, conflict: 409 } as const;

/** Translation keys of the failure sentences (applicationAccess.errors.*). */
export const ACCESS_TEXT_KEYS = {
  errorForbidden: 'applicationAccess.errors.forbidden',
  errorDuplicate: 'applicationAccess.errors.duplicate',
  errorNotFound: 'applicationAccess.errors.notFound',
  errorInvalid: 'applicationAccess.errors.invalid',
  errorFailed: 'applicationAccess.errors.failed',
} as const;
