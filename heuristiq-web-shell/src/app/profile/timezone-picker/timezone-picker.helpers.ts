/**
 * Zones matching what the person typed, best matches first: names starting with the text, then names containing it.
 * Case, spaces and underscores are ignored ("new york" finds America/New_York). An empty query lists the first `limit` zones.
 */
export function filterTimezones(zones: readonly string[], query: string, limit: number): string[] {
  const needle = normalize(query);
  if (!needle) {
    return zones.slice(0, limit);
  }
  const starts: string[] = [];
  const contains: string[] = [];
  for (const zone of zones) {
    const haystack = normalize(zone);
    if (haystack.startsWith(needle) || haystack.split('/').some((part) => part.startsWith(needle))) {
      starts.push(zone);
    } else if (haystack.includes(needle)) {
      contains.push(zone);
    }
    if (starts.length >= limit) {
      break;
    }
  }
  return [...starts, ...contains].slice(0, limit);
}

function normalize(text: string): string {
  return text.trim().toLowerCase().replace(/[\s_]+/g, '');
}
