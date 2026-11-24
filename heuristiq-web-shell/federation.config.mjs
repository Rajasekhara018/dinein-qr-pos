import { withNativeFederation, shareAll } from '@angular-architects/native-federation-v4/config';

export default withNativeFederation({
  name: 'realboom-web',


  shared: {
    ...shareAll(
      { singleton: true, strictVersion: true, requiredVersion: 'auto', build: 'package' },
      {
        overrides: {
          // ng-apexcharts loads /client dynamically. Keep it and the opt-in renderer
          // on the same shared core so Icicle registers on the instance that draws it.
          apexcharts: { singleton: true, strictVersion: true, requiredVersion: 'auto', build: 'package', includeSecondaries: { keepAll: true } },
          // includeSecondaries is an opt-out of ignoreUnusedDeps, so all of
          // @angular/core is shared to prevent mismatches.
          '@angular/core': { singleton: true, strictVersion: true, requiredVersion: 'auto', build: 'package', includeSecondaries: { keepAll: true } },
          // Same reasoning for @angular/platform-browser — its /animations secondary
          // (BrowserAnimationsModule) must be in the shared import map or the host
          // fails with "Unable to resolve specifier '@angular/platform-browser/animations'".
          '@angular/platform-browser': { singleton: true, strictVersion: true, requiredVersion: 'auto', build: 'package', includeSecondaries: { keepAll: true } },
        },
      },
    ),
  },

  skip: [
    'rxjs/ajax',
    'rxjs/fetch',
    'rxjs/testing',
    'rxjs/webSocket',
    // quill/ngx-quill/quill-delta aren't consumed by other federation participants and
    // quill-delta's CJS/ESM interop breaks esbuild's separate-bundle sharing mode (#nf-issue).
    'quill',
    'quill-delta',
    'ngx-quill',
    // d3-org-chart (the org chart preview) and its d3-* dependencies are plain ES modules only this app uses;
    // bundle them into the app instead of sharing them with the federation remotes.
    /^d3-/,
    // leaflet (the public live-trip map, lazy-loaded by /track) is a CommonJS/UMD build that cannot be shared; Rider_AdminWeb
    // skips it the same way and bundles its own copy.
    'leaflet',
    // Add further packages you don't need at runtime
  ],

  // Please read our FAQ about sharing libs:
  // https://shorturl.at/jmzH0

  features: {
    // ignoreUnusedDeps is enabled by default now
    // ignoreUnusedDeps: true,

    // Opt-in: groups chunks in remoteEntry.json for smaller metadata file
    denseChunking: true
  }
});
