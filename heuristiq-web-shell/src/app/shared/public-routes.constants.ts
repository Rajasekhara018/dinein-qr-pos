/**
 * Public pages of Rider_Web: open routes outside the signed-in layout that never use the session. The shared-trip page
 * (`/track#<token>`) and the franchise's shared driver onboarding draft are opened without signing in.
 */
export const PUBLIC_ROUTE_PREFIXES: readonly string[] = ['/track', '/driver-onboarding'];
