/** Lets the i18n parity spec import the translation files (the app itself loads them over HTTP). */
declare module '*.json' {
  const content: Record<string, unknown>;
  export default content;
}
