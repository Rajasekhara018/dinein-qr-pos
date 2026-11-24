/** A selectable choice: what the backend receives (`value`) and what the user sees (`label`, already translated). */
export interface HqOption {
  value: string;
  label: string;
  /** Shown but cannot be picked (greyed out in a `hq-searchable-select`). */
  disabled?: boolean;
}
