import { AdminFormGuide, AdminFormOption } from './admin-form.types';

/*
 * The choices of the API client form and the guide shown beside it. Picking a client type fills in the authentication method and
 * grant types that kind of client normally uses; they can still be changed afterwards.
 */

const G = 'accessManagement.clients.guide';

/** The only authentication method UAM sets up in Keycloak today (the client gets a generated secret). */
export const CLIENT_SECRET_METHOD = 'CLIENT_SECRET';

/** Methods UAM stores but does not configure in Keycloak yet: listed, but not pickable. */
const UNSUPPORTED_AUTH_METHODS = ['PRIVATE_KEY_JWT', 'MTLS', 'JWT_ASSERTION'];

export const CLIENT_AUTH_METHOD_OPTIONS: AdminFormOption[] = [CLIENT_SECRET_METHOD, ...UNSUPPORTED_AUTH_METHODS]
  .map((v) => ({ value: v, label: v, disabled: UNSUPPORTED_AUTH_METHODS.includes(v) }));

export const CLIENT_GRANT_TYPE_OPTIONS: AdminFormOption[] = ['CLIENT_CREDENTIALS', 'AUTHORIZATION_CODE', 'REFRESH_TOKEN', 'PASSWORD', 'DEVICE_CODE']
  .map((v) => ({ value: v, label: v }));

/** Machine-to-machine: the system signs in as itself with its client id and secret. */
const SYSTEM_GRANTS = ['CLIENT_CREDENTIALS'];
/** A person signs in through the browser and the app keeps them signed in. */
const BROWSER_GRANTS = ['AUTHORIZATION_CODE', 'REFRESH_TOKEN'];

/** Client type → the grant types it normally uses (copied into the form when the type is picked). */
const CLIENT_TYPE_GRANTS: Record<string, string[]> = {
  INTEGRATION: SYSTEM_GRANTS,
  EXTERNAL: SYSTEM_GRANTS,
  MICROSERVICE: SYSTEM_GRANTS,
  BATCH: SYSTEM_GRANTS,
  INTERNAL: SYSTEM_GRANTS,
  WEB: BROWSER_GRANTS,
  MOBILE: BROWSER_GRANTS,
};

export const CLIENT_TYPE_OPTIONS: AdminFormOption[] = Object.entries(CLIENT_TYPE_GRANTS).map(([type, grants]) => ({
  value: type,
  label: type,
  fill: { authenticationMethod: CLIENT_SECRET_METHOD, grantTypes: [...grants] },
}));

export const CLIENT_FORM_GUIDE: AdminFormGuide = {
  title: `${G}.title`,
  intro: `${G}.intro`,
  sections: [
    { title: `${G}.steps.title`, text: `${G}.steps.text` },
    {
      title: `${G}.clientType.title`,
      text: `${G}.clientType.text`,
      items: [
        { term: 'INTEGRATION / EXTERNAL', text: `${G}.clientType.integration` },
        { term: 'MICROSERVICE / INTERNAL / BATCH', text: `${G}.clientType.internal` },
        { term: 'WEB / MOBILE', text: `${G}.clientType.browser` },
      ],
    },
    {
      title: `${G}.authMethod.title`,
      items: [
        { term: 'CLIENT_SECRET', text: `${G}.authMethod.clientSecret` },
        { term: 'PRIVATE_KEY_JWT / MTLS / JWT_ASSERTION', text: `${G}.authMethod.notYet` },
      ],
    },
    {
      title: `${G}.grantTypes.title`,
      items: [
        { term: 'CLIENT_CREDENTIALS', text: `${G}.grantTypes.clientCredentials` },
        { term: 'AUTHORIZATION_CODE', text: `${G}.grantTypes.authorizationCode` },
        { term: 'REFRESH_TOKEN', text: `${G}.grantTypes.refreshToken` },
        { term: 'PASSWORD', text: `${G}.grantTypes.password` },
        { term: 'DEVICE_CODE', text: `${G}.grantTypes.deviceCode` },
      ],
    },
    {
      title: `${G}.other.title`,
      items: [
        { term: 'accessManagement.fields.tenantId', text: `${G}.other.tenant` },
        { term: 'accessManagement.fields.applicationId', text: `${G}.other.application` },
        { term: 'accessManagement.fields.scopes', text: `${G}.other.scopes` },
        { term: 'accessManagement.fields.redirectUris', text: `${G}.other.redirectUris` },
      ],
    },
    { title: `${G}.token.title`, text: `${G}.token.text` },
  ],
};
