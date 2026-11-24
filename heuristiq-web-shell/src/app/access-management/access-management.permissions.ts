/** Screen permission key per Access Management area (the first segment of its route), see the privilege map in hq-shared-lib. */
export const ACCESS_MANAGEMENT_PERMISSIONS: Readonly<Record<string, string>> = {
  users: 'view_users',
  roles: 'view_roles',
  organizations: 'view_organizations',
  applications: 'view_applications',
  clients: 'view_api_clients',
  'security-policies': 'view_security_policies',
};
