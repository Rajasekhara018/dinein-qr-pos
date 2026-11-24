import { environment } from '../../environments/environment';

/** The per-profile hide lists an environment file may carry (absent = nothing is hidden). */
interface ProfileVisibility {
  /** Sidebar sections (their i18n keys) left out of the sidebar and the quick-navigate search. */
  hiddenNavSections?: readonly string[];
  /** Ids of the Today cards left out. */
  hiddenTodaySections?: readonly string[];
  /** The application codes this deployment's apps are registered under in UAM; the application form offers exactly these. */
  applicationCodes?: readonly string[];
}

const visibility = environment as ProfileVisibility;

/** True when the deployment profile hides this sidebar section (the routes themselves stay reachable). */
export function isNavSectionHidden(section: string): boolean {
  return visibility.hiddenNavSections?.includes(section) ?? false;
}

/** True when the deployment profile hides this Today card. */
export function isTodaySectionHidden(id: string): boolean {
  return visibility.hiddenTodaySections?.includes(id) ?? false;
}

/** The application codes of this profile, or none when the profile does not restrict them. */
export function profileApplicationCodes(): readonly string[] {
  return visibility.applicationCodes ?? [];
}
