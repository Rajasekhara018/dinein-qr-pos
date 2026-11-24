/** Path of the session organization's applications under the UAM base (signed-in callers only, no privilege). */
export const HQ_APPLICATIONS_LOOKUP_PATH = '/identity/applications';

/** How long a loaded list is reused before the next read fetches it again (matches the server's Cache-Control max-age). */
export const HQ_APPLICATIONS_CACHE_TTL_MS = 60_000;
