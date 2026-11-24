import { AdminFormConfig } from '../admin-form.types';
import { USER_TYPES } from '../../shared/models/uam.models';
import { userTypeOf } from './users-list.helpers';
import { loadRoleOptions } from '../uam-option-loaders';
import { USER_FORM_GUIDE } from '../admin-form-guides.constants';
import {
  RULE_ERROR_KEYS,
  USER_EMAIL_MAX_LENGTH,
  USER_EMAIL_PATTERN,
  USER_PASSWORD_MAX_LENGTH,
  USER_PASSWORD_PATTERN,
} from '../access-management-rules.constants';

// NOTE: entityName and each field's label/hint are *translation keys*, not display text --
// AdminFormService resolves them through TranslateService when the dialog opens, since this
// config is a static object built once at module load, before TranslateService has an active language.
export const USER_FORM_CONFIG: AdminFormConfig = {
  resourcePath: '/users',
  entityName: 'accessManagement.users.entityName',
  columns: [],
  fields: [
    // Write side (UpsertUserRequest) uses `username`, but the GET response (UamUser) spells it
    // `userName` -- without readKey, editing an existing user shows this field blank.
    { key: 'username', readKey: 'userName', label: 'accessManagement.users.fields.username', type: 'text', required: true },
    { key: 'password', label: 'accessManagement.users.fields.password.label', type: 'password', requiredOnCreate: true, maxLength: USER_PASSWORD_MAX_LENGTH,
      pattern: USER_PASSWORD_PATTERN, patternMessage: RULE_ERROR_KEYS.userPassword, hint: 'accessManagement.users.fields.password.hint' },
    { key: 'fullName', label: 'profile.form.fullName.label', type: 'text' },
    { key: 'userType', readValue: userTypeOf, label: 'accessManagement.users.fields.userType', type: 'select', options: USER_TYPES.map((v) => ({ value: v, label: v })) },
    { key: 'email', label: 'profile.form.email.label', type: 'text', maxLength: USER_EMAIL_MAX_LENGTH, pattern: USER_EMAIL_PATTERN, patternMessage: RULE_ERROR_KEYS.userEmail },
    { key: 'phoneNumber', label: 'profile.form.phoneNumber.label', type: 'phone', requiredOnCreate: true },
    { key: 'roles', label: 'accessManagement.users.fields.roles.label', type: 'multiselect', loadOptions: loadRoleOptions, hint: 'accessManagement.users.fields.roles.hint' },
    { key: 'status', label: 'accessManagement.users.fields.status', type: 'select', options: ['ACTIVE', 'INACTIVE', 'PENDING', 'LOCKED', 'SUSPENDED', 'DISABLED'].map((v) => ({ value: v, label: v })) },
  ],
  guide: USER_FORM_GUIDE,
};
