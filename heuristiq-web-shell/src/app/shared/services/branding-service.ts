import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';

/** Per-deployment branding, loaded at runtime from the JSON file named by environment.configFile. */
export interface BrandingConfig {
  appName: string;
  tagline: string;
  poweredBy: string;
  logoImagePath: string;
  favicon: string;
  /** Replaces the sign-in page's story panel (English copy of this deployment); absent = the translated default copy. */
  loginStory?: LoginStory;
}

/** The sign-in page's left panel for a deployment whose business is not the default cab/train one. */
export interface LoginStory {
  badge: string;
  headline: string;
  copy: string;
  features: string[];
  stats: { value: string; label: string }[];
  /** Optional picture above the badge on the sign-in page (asset path); none when left out. */
  illustration?: string;
}

/**
 * Last-resort branding used only when environment.configFile can't be fetched or parsed.
 * The JSON files under src/assets/config/** are the source of truth -- this is the only place
 * default branding values live in code.
 */
const FALLBACK_BRANDING: BrandingConfig = {
  appName: 'RailCab',
  tagline: 'Your ride, on track.',
  poweredBy: 'Powered by Heuristiq Gateway Solutions',
  logoImagePath: 'assets/config/realboom/logo.png',
  favicon: 'assets/config/realboom/logo.png',
};

/**
 * Loads the deployment's branding JSON once at startup (via provideAppInitializer in AppModule).
 * Bootstrap waits for the initializer, so every component/service can read `branding`
 * synchronously afterwards.
 */
@Injectable({ providedIn: 'root' })
export class BrandingService {
  private _branding: BrandingConfig = FALLBACK_BRANDING;

  get branding(): BrandingConfig {
    return this._branding;
  }

  async load(): Promise<void> {
    const url = environment.configFile;
    try {
      // Plain fetch(), not HttpClient -- this runs inside an app initializer, before/during
      // bootstrap, and importing @angular/common/http at that exact point raced with native
      // federation's import-map setup for shared packages in dev-serve mode ("Unsupported
      // Content-Type 'text/html'" errors resolving the module). fetch() is a native global with
      // no module resolution involved, so it sidesteps the issue entirely.
      // no-cache: revalidate with the server so an edited/swapped JSON takes effect post-deploy
      // without users being stuck on a cached copy.
      const response = await fetch(url, { headers: { 'Cache-Control': 'no-cache' } });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const json: Partial<BrandingConfig> = await response.json();
      if (!json || typeof json !== 'object') throw new Error('branding JSON is not an object');
      // Merge over the fallback so a JSON missing a field degrades gracefully instead of rendering "undefined".
      this._branding = { ...FALLBACK_BRANDING, ...json };
    } catch (err) {
      console.warn(`[BrandingService] Failed to load branding from "${url}"; using default branding.`, err);
      this._branding = FALLBACK_BRANDING;
    }
  }
}
