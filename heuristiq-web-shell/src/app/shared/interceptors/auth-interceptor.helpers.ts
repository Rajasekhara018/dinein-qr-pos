const ABSOLUTE_URL = /^[a-z][a-z0-9+.-]*:\/\//i;
const WEB_URL = /^https?:\/\//i;

/**
 * The origins of the platform's own servers: every http(s) address found in the environment settings (nested ones too), plus
 * the origin the page itself is served from.
 */
export function platformOrigins(settings: unknown, pageOrigin: string): ReadonlySet<string> {
  const origins = new Set<string>([pageOrigin]);
  const visit = (value: unknown): void => {
    if (typeof value === 'string' && WEB_URL.test(value)) {
      try {
        origins.add(new URL(value).origin);
      } catch {
        // not a usable address: nothing to allow
      }
    } else if (value && typeof value === 'object') {
      Object.values(value as Record<string, unknown>).forEach(visit);
    }
  };
  visit(settings);
  return origins;
}

/**
 * Whether a request may carry the signed-in person's access token: only calls to this site (a relative address) or to one of the
 * platform's own servers. An address anywhere else -- for example a signed document link on the storage service, which already
 * carries its own signature and is refused when a second credential is added -- never receives the token.
 */
export function carriesPlatformToken(url: string, origins: ReadonlySet<string>): boolean {
  if (!ABSOLUTE_URL.test(url)) {
    return true;
  }
  try {
    return origins.has(new URL(url).origin);
  } catch {
    return false;
  }
}
