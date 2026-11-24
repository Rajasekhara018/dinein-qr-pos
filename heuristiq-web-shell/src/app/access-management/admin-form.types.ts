import { Observable } from 'rxjs';
import { HqAppDialogFormValues, HqAppDialogValue, HqOption } from 'hq-shared-lib';
import { UamService } from '../shared/services/uam-service';

/** How a field of an admin-managed UAM entity is captured in the create / edit form dialog. */
export type AdminFormFieldType = 'text' | 'textarea' | 'number' | 'checkbox' | 'select' | 'multiselect' | 'tags' | 'entity-picker' | 'phone' | 'password';

/**
 * A choice of a `select` field. Beyond the shared {@link HqOption} it may be unavailable (shown, but not pickable) and may
 * carry values that picking it copies into other fields of the form (for example a catalogue entry's name and type).
 */
export interface AdminFormOption extends HqOption {
  disabled?: boolean;
  /** Field key → value written into the form when this option is picked. */
  fill?: Record<string, HqAppDialogValue>;
  /** What the option stands for, for the information panel of its field (data from the API, never translated). */
  detail?: AdminFormOptionDetail;
}

/** One entry of an information panel: the option's code (its `value`) is shown next to this. */
export interface AdminFormOptionDetail {
  name: string;
  /** Short purpose of the entry. */
  description?: string;
  /** Kind of entry, shown as-is (for an application: WEB, MOBILE ...). */
  category?: string;
  /** The entry is already taken; the panel marks it with its `flaggedBadge`. */
  flagged?: boolean;
}

/** Information-only panel listing every choice of a `select` field with its detail (translation keys only). */
export interface AdminFormOptionPanel {
  title: string;
  /** One-line note under the title. */
  note: string;
  /** Label before the entry's category. */
  categoryLabel: string;
  /** Badge shown on flagged entries. */
  flaggedBadge: string;
  /** Message shown when the choices could not be loaded or there are none. */
  empty: string;
}

/** Prominent read-only box showing the value picked in a `select` field, with a copy-to-clipboard button (translation keys only). */
export interface AdminFormCodeBox {
  label: string;
  /** Accessible name of the copy button; receives `{{code}}`. */
  copyLabel: string;
  copied: string;
  copyFailed: string;
}

/** When a field is shown but cannot be changed: always (set by the server or by another field), or only when editing. */
export type AdminFormReadOnly = 'always' | 'edit';

export interface AdminFormField {
  /** Tri-state policy setting: empty inherits, the other options serialize as JSON booleans. */
  booleanSelect?: boolean;
  /** Optional text values whose empty representation is JSON null. */
  emptyAsNull?: boolean;
  /** Dot-path into the create/update payload, e.g. "code" or "passwordPolicy.minimumLength". */
  key: string;
  /** Translation key of the field label (resolved when the dialog opens). */
  label: string;
  type: AdminFormFieldType;
  /** A `select` with a long list (countries, currencies, time zones): drawn as a searchable drop-down, not a native select. */
  searchable?: boolean;
  /** Static choices of a `select` / `multiselect` field (the backend codes, shown as-is). */
  options?: AdminFormOption[];
  /** Live choices of a `select` / `entity-picker` / `multiselect` field, loaded before the page opens. */
  loadOptions?: (uam: UamService) => Observable<AdminFormOption[]>;
  /** Not drawn on the page, but still part of the form: its default or stored value is loaded and sent with the rest, unchanged. */
  hidden?: boolean;
  /** Shown read-only (its value is still sent): always, or only on the edit page. */
  readOnly?: AdminFormReadOnly;
  /** Optional information panel listing all choices of this field (shown while the field is editable). */
  optionPanel?: AdminFormOptionPanel;
  /** Optional read-only box with a copy button showing the picked value (shown on create once picked, and on edit). */
  codeBox?: AdminFormCodeBox;
  /** Translation key shown after the label of a disabled option (e.g. "already created"). */
  disabledOptionHint?: string;
  /** Translation key shown when a field that loads its choices ends up with none (the list could not be loaded). */
  emptyOptionsMessage?: string;
  /** Translation key shown when every choice is disabled. */
  allDisabledMessage?: string;
  required?: boolean;
  /** Required on the create page only (a password is kept as it is when left blank on edit). */
  requiredOnCreate?: boolean;
  /** Longest value the server accepts; the page stops typing there and shows how much is left. */
  maxLength?: number;
  /** Translation key of the helper text under the control. */
  hint?: string;
  /** Dot-path to read the field's initial value from when editing, if it differs from `key` on
   *  the GET response (e.g. Users: the create/update payload field is `username`, but the GET
   *  response's field is `userName`). Defaults to `key`. */
  readKey?: string;
  /** Works out the initial value from the whole entity when it is not stored as a field of its own (wins over `readKey`). */
  readValue?: (entity: AdminFormEntity) => unknown;
  /** Value the field starts with on the create page (otherwise empty, or unticked for a checkbox). */
  defaultValue?: HqAppDialogValue;
  /** A value that is typed must match this; `patternMessage` is the translation key of the error shown. */
  pattern?: RegExp;
  patternMessage?: string;
  /** Smallest value a `number` field accepts when one is typed. */
  min?: number;
  /** Translation key of the error shown when the value is below `min`. */
  minMessage?: string;
  /** Largest value a `number` field accepts when one is typed. */
  max?: number;
  /** Translation key of the error shown when the value is above `max`. */
  maxMessage?: string;
  /**
   * A rule that needs more than the field's own text (another field, or a list in a `tags` field). Runs only once the field has a
   * value; returns the translation key of the error, or null when the value is fine.
   */
  rule?: (values: HqAppDialogFormValues) => string | null;
  /** The field is shown (and sent) only when the user holds at least one of these privilege codes; absent means always. */
  privileges?: readonly string[];
}

/** A list column of an admin-managed entity; `label` is a translation key. */
export interface AdminFormColumn {
  key: string;
  label: string;
}

/** Everything a list screen needs to show and edit one admin-managed UAM entity. */
export interface AdminFormConfig {
  /** UAM resource path, e.g. "/organizations". */
  resourcePath: string;
  /** Translation key of the singular entity name, used in dialog titles and toasts. */
  entityName: string;
  columns: AdminFormColumn[];
  fields: AdminFormField[];
  /** Optional help panel shown beside the form: what the screen does and what each choice means. */
  guide?: AdminFormGuide;
}

/** One term of a guide section: `term` is a translation key or a backend code (shown as-is), `text` is a translation key. */
export interface AdminFormGuideItem {
  term: string;
  text: string;
}

/** A titled block of the guide (translation keys), with a short text and/or a list of terms. */
export interface AdminFormGuideSection {
  title: string;
  text?: string;
  items?: AdminFormGuideItem[];
}

/** The help panel beside a form (translation keys). */
export interface AdminFormGuide {
  title: string;
  intro: string;
  sections: AdminFormGuideSection[];
}

export type AdminFormMode = 'create' | 'edit';

/** The create/update request body built from the dialog values (nested along each field's dot-path). */
export type AdminFormPayload = Record<string, unknown>;

/** An entity as the GET endpoint returns it. */
export type AdminFormEntity = Record<string, unknown>;
