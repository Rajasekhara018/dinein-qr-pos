/** Shapes of the API documents (section 10 of docs/QA_DASHBOARD_DESIGN.md): one markdown document per API area (module). */

/** A document in the list (no markdown). */
export interface QaApiDocInfo {
  /** `api-<module>` for an API document. */
  name: string;
  title: string;
  updatedAt: string;
  sizeBytes: number;
  category: string;
  module: string | null;
}

/** One document with its markdown. */
export interface QaApiDocument extends QaApiDocInfo {
  markdown: string;
}

/** What is sent to create or replace a document. */
export interface QaApiDocInput {
  name: string;
  title: string;
  markdown: string;
  category: 'API';
  module: string;
}

/** A module of the left-hand list: how many endpoints it has and its document, if there is one. */
export interface QaApiModule {
  /** The code of the module as the API document names it: lower case, like `uam` or `administration`. */
  module: string;
  /**
   * The module's exact name in the endpoint inventory (the privilege catalog writes them in capitals, like `ADMINISTRATION`), used to ask
   * for its endpoints; null for a module known only from a document.
   */
  inventoryModule: string | null;
  /** null while it is not known (counts are asked for after the list shows, or the user may not read them). */
  endpointCount: number | null;
  document: QaApiDocInfo | null;
}
