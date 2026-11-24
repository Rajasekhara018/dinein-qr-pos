import { platformBrowser } from '@angular/platform-browser';

type BrowserGlobal = typeof globalThis & {
  global?: typeof globalThis;
};

const browserGlobal = globalThis as BrowserGlobal;
browserGlobal.global ??= globalThis;

void import('./app/app-module').then(({ AppModule }) => {
  platformBrowser()
    .bootstrapModule(AppModule, {})
    .catch((err) => console.error(err));
});
