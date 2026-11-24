import { initFederation } from '@angular-architects/native-federation-v4';

// Angular's code is shared between the host and the remotes as separately built files, and those are not given the
// build-time ngDevMode setting that normal app builds get, so some code paths (creating a rich-text editor, for one)
// failed with "ngDevMode is not defined". Define it before any Angular file loads; false is the production behaviour.
(globalThis as { ngDevMode?: unknown }).ngDevMode ??= false;

initFederation('federation.manifest.json')
  .then(_ => import('./bootstrap'))
  .catch(err => console.error('[FEDERATION INIT FAILED]', err));