/** Where the hub's subscription lookup stands. */
export type HomeLoadStatus = 'loading' | 'ready' | 'failed';

/**
 * One feature an application offers, described on its card. `privileges` are exact names from privilege.json; the feature is
 * shown to a user who holds any one of them, so a card never advertises something the person cannot use.
 */
export interface AppFeature {
  key: string;
  privileges: ReadonlyArray<string>;
}
