/** A DMS document id as the QA publish stores it (the backend checks the same pattern). */
export const QA_EVIDENCE_ID_PATTERN = /^[A-Za-z0-9-]{1,100}$/;

/** At most this many screenshots hang on one scenario. */
export const QA_EVIDENCE_MAX = 3;

/** The text of each kind of screenshot link; an unknown kind reads as a plain screenshot. */
export const QA_EVIDENCE_KIND_KEYS: Readonly<Record<string, string>> = {
  SCREEN: 'qaDashboard.evidence.screen',
  FAILURE: 'qaDashboard.evidence.failure',
};

export const QA_EVIDENCE_MESSAGE_KEYS = {
  openFailed: 'qaDashboard.evidence.openFailed',
} as const;

/** The viewer's options (the same as the run PDF's). */
export const QA_EVIDENCE_DIALOG = { maxWidth: '95vw', autoFocus: false } as const;

/** The inline screenshot preview: how many downloads may run at once on a page, the biggest file shown inline, and its box. */
export const QA_THUMBNAIL = {
  maxConcurrent: 3,
  maxBytes: 3 * 1024 * 1024,
  width: 220,
  height: 140,
  /** Start loading a little before the preview scrolls into view. */
  rootMargin: '200px',
  /** A picture that has scrolled further away than this gives its object URL back (it is downloaded again if it returns). */
  unloadMargin: '1500px',
  /** Most object URLs alive at once on a page; the least recently used one nobody shows is revoked beyond it. */
  maxObjectUrls: 40,
} as const;

export const QA_THUMBNAIL_KEYS = {
  alt: 'qaDashboard.evidence.thumbnailAlt',
  loading: 'qaDashboard.evidence.thumbnailLoading',
} as const;

/** The screenshot kinds shown as a preview right in the row: the failure one and the final state of a passed scenario. */
export const QA_THUMBNAIL_KINDS: readonly string[] = ['FAILURE', 'SCREEN'];
