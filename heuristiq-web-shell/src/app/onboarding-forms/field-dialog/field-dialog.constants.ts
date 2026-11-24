import { FieldType } from '../onboarding-forms.models';

/** Types that support the numeric range inputs (minValue/maxValue). */
export const NUMERIC_TYPES: readonly FieldType[] = ['NUMBER', 'CURRENCY'];

/** Types that support the length range inputs (minLength/maxLength) and the custom-pattern
 *  escape hatch (TEXT only enforces a pattern when one is explicitly set; PHONE/EMAIL have a
 *  built-in default the pattern would override). */
export const TEXT_LIKE_TYPES: readonly FieldType[] = ['TEXT', 'PHONE', 'EMAIL'];

/** Active document types offered for a DOCUMENT field (the Document types API caps a page at 100). */
export const DOCUMENT_TYPE_OPTIONS_PAGE_SIZE = 100;

/** A document type a DOCUMENT field can name: its code, its category (the field's category follows it) and its name. */
export interface DocumentTypeChoice {
  code: string;
  category: string;
  label: string;
}
