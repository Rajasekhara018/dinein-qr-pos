import { map } from 'rxjs';
import { HqOption } from 'hq-shared-lib';
import { UamService } from '../shared/services/uam-service';
import { Application, ApplicationCatalogueItem, UamRole, UamUser } from '../shared/models/uam.models';
import { AdminFormOption } from './admin-form.types';
import { APPLICATION_CATALOGUE_PATH } from './access-management.constants';

// Shared `entity-picker`-style option loaders used by the dedicated Users screens
// (and their AdminFormService create/edit dialogs) to look up live roles/users instead of
// relying on free-text codes. Roles picks permissions directly from a filterable table in
// RoleFormDialog, so there's no loadPermissionOptions here -- permissions have no standalone
// management screen (see RoleFormDialog).
export const loadRoleOptions = (uam: UamService) => uam.list<UamRole>('/roles', 0, 200).pipe(
  map((page) => page.content.map((r: UamRole): HqOption => ({ value: r.code, label: r.name || r.code }))),
);

/** Most organizations / applications offered in a picker. */
const SCOPE_OPTIONS_LIMIT = 200;

/** An organization as the organizations list returns it (only what a picker needs). */
interface OrganizationRow {
  id: string;
  code: string;
  name?: string | null;
}

/** The organizations to scope an API client or a security policy to: the value is the organization id, shown as "Name (CODE)". */
export const loadOrganizationOptions = (uam: UamService) => uam.list<OrganizationRow>('/organizations', 0, SCOPE_OPTIONS_LIMIT).pipe(
  map((page) => page.content.map((o): AdminFormOption => ({ value: o.id, label: o.name ? `${o.name} (${o.code})` : o.code }))),
);

/** The applications to scope an API client or a security policy to: the value is the application id, shown as "Name (CODE)". */
export const loadApplicationScopeOptions = (uam: UamService) => uam.list<Application>('/applications', 0, SCOPE_OPTIONS_LIMIT).pipe(
  map((page) => page.content.map((a): AdminFormOption => ({ value: a.id, label: a.name ? `${a.name} (${a.code})` : a.code }))),
);

/** Most departments offered in a picker. */
const DEPARTMENT_OPTIONS_LIMIT = 200;

/** The departments of the organization the signed-in user is working in, by name (the organization's own org structure). */
export const loadDepartmentOptions = (uam: UamService) => uam
  .get<{ code: string; name: string }[]>(`/org-structure/units/search?limit=${DEPARTMENT_OPTIONS_LIMIT}`)
  .pipe(map((units) => (units ?? []).map((u): HqOption => ({ value: u.name, label: u.name }))));

export const loadUserOptions = (uam: UamService) => uam.listUsers(0, 200).pipe(
  map((page) => page.content.map((u: UamUser): HqOption => ({ value: u.userName, label: u.email ? `${u.userName} (${u.email})` : u.userName }))),
);

/**
 * The application catalogue as choices of the application form's code field: "Name (CODE)", codes the organization already
 * has are disabled, and picking one fills in the catalogue's name, description and type.
 */
export const loadApplicationCatalogueOptions = (uam: UamService) => uam
  .get<ApplicationCatalogueItem[]>(APPLICATION_CATALOGUE_PATH)
  .pipe(map((items) => (items ?? []).map((item): AdminFormOption => ({
    value: item.code,
    label: `${item.name} (${item.code})`,
    disabled: item.created,
    fill: { name: item.name, description: item.description ?? '', type: item.type },
    detail: { name: item.name, description: item.description ?? undefined, category: item.type, flagged: item.created },
  }))));
