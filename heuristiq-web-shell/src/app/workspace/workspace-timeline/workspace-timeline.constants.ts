import { TICKET_STATUS_LABEL_KEYS } from '../service-desk/service-desk.constants';

/** Translation key of a status in a history line (a ticket's statuses, and "Done" of a to-do or task). */
export const TIMELINE_STATUS_KEYS: Readonly<Record<string, string>> = { ...TICKET_STATUS_LABEL_KEYS, DONE: 'workspace.timeline.done' };
