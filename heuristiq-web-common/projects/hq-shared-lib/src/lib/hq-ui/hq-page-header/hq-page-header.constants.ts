/** One step of the breadcrumb trail above a page title. `label` is already translated; without `link` it is plain text. */
export interface HqBreadcrumb {
  label: string;
  /** routerLink target (string or command array); null/undefined renders plain text. */
  link?: string | readonly unknown[] | null;
}
