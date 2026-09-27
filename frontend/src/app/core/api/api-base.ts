/**
 * Versioned root of every backend REST endpoint. Build API URLs from this (e.g. `${API_BASE}/admin/items`) rather
 * than hard-coding `/api/...`. The STOMP endpoint `/ws` is not versioned.
 */
export const API_BASE = '/api/v1';
