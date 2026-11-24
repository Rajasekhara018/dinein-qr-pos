/**
 * A default an app provides to the library: a fixed value (from its environment), or a function the library calls each time it
 * needs it, so a value that is known only after sign-in (the current organization's currency or country) is picked up.
 */
export type HqAppSetting = string | (() => string | null | undefined);

/** The setting's current value, or {@code fallback} when it is not provided or gives nothing. */
export function hqReadSetting(setting: HqAppSetting | null | undefined, fallback: string): string {
  const value = typeof setting === 'function' ? setting() : setting;
  return value && value.trim() ? value.trim() : fallback;
}
