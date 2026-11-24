# Rider-angular-common

Angular workspace for **hq-shared-lib**, the reusable component library shared by
`Rider_Web` and `Rider_AdminWeb`. It is the Rider counterpart of `tou-angular-common`
(`tou-shared-lib`), and it is built and consumed the same way: build, `npm pack`, and the apps
depend on the packed `.tgz`.

## Contents

| Export | Selector / API | Ported from |
| --- | --- | --- |
| `HqDocUpload` | `<hq-doc-upload>` | `tou-doc-upload` |
| `HqMultiDocUpload` | `<hq-multi-doc-upload>` | `tou-multi-doc-upload` |
| `HqCommonModel` | lookup dialog, opened via `HqCommonSearchService.openLookup()` | `tou-common-model` |
| `HqCommonSearchService` | lookup-table registry + search helpers | `tou-common-search` (service) |
| `HqClose`, `HqOpenFile` | confirm dialog, image/PDF preview dialog | `tou-close`, `tou-open-file` |
| `HqDocumentService` | Rider_DMS client (upload, list, open, download, delete) | `TouPostService.postFile/doGetFile` |
| `HqThemeService`, `HQ_THEME_CATALOG`, `theme/*.scss` | BEST RIDE theme (light / dark / ocean / forest) | `TouThemeService` |

Documents go to **Rider_DMS through Rider_ApiGateway** (`/api/v1/dms/documents`). Each one is
filed under a `category` plus an `ownerId`, for example `driver-license` plus a driver id. With
`[autoLoad]="true"`, a screen shows whatever is already filed under that pair.

## Build

```bash
./build.sh        # or build.bat on Windows
```

This installs dependencies, runs `ng build`, packs `dist/hq-shared-lib/hq-shared-lib-0.0.1.tgz`,
and copies the `.tgz` into `../Rider_Web` and `../Rider_AdminWeb`. Then run
`npm install hq-shared-lib-0.0.1.tgz` in each app.

The `.tgz` is **not committed**: each app's `.gitignore` lists `hq-shared-lib-0.0.1.tgz`, the same
as `tou-web-bank`. Each app's `Jenkinsfile` follows `tou-web-bank`'s flow instead:

1. **Build Shared Library** builds and packs this workspace, then copies the `.tgz` into the app.
2. **Build Main App** runs `npm install hq-shared-lib-0.0.1.tgz` and `ng build`.
3. **Build Docker Image** runs `docker build`. The Dockerfile copies the `.tgz` in before `npm install`.

Rebuild the package whenever the library changes.

## Use in an app

`package.json`:

```json
"hq-shared-lib": "file:hq-shared-lib-0.0.1.tgz"
```

Import it once with the app's endpoints. Rider_Web and Rider_AdminWeb do this in their `SharedModule`:

```ts
HqSharedLibModule.forRoot({
  gatewayUrl: environment.gatewayUrl,          // e.g. http://host:8999/api
  adminApiUrl: environment.adminApiUrl,        // Rider_AdminAPI base
  currentUserId: () => readStoredUserId(),     // default DMS ownerId
})
```

```html
<hq-doc-upload label="Driving licence" category="driver-license" [ownerId]="driverId"
                [autoLoad]="true" [deleteOnRemove]="true"></hq-doc-upload>

<hq-multi-doc-upload category="vehicle-other" [ownerId]="vehicleId" [autoLoad]="true"></hq-multi-doc-upload>
```

```ts
this.lookups.openLookup({ lookupTable: 'VENDORS' }).subscribe((res) => {
  if (res?.modelKey) this.form.patchValue({ vendorId: res.modelKey });
});
```

The built-in lookup tables are `UAM_USERS`, `UAM_ROLES`, `VENDORS` and `VEHICLES`. Add more with
`HqCommonSearchService.register({...})`.

## Theming

The library ships the BEST RIDE theme. It contains a Material M2 theme and CSS custom properties
for four `data-theme` variants: `light`, `dark`, `ocean` and `forest`. Add both stylesheets to an
app's `angular.json` `styles`:

```json
"node_modules/hq-shared-lib/theme/styles.scss",
"node_modules/hq-shared-lib/theme/typography.scss"
```

`HqThemeService` switches the variant by setting `<html data-theme="…">` and remembers the choice
in localStorage. The default key is `bestride_theme`; provide `HQ_THEME_STORAGE_KEY` to use a
different one. `HQ_THEME_CATALOG` holds the swatches for a theme picker. This replaces the old
repo-root `shared/theme-web` folder and Rider_Web's `sync-theme` script.

Image cropping is optional. An app that bundles a cropper provides `HQ_IMAGE_CROPPER`; Rider_Web
does this using ngx-image-cropper.

## Slide toggle

Import `HqSharedLibModule` to use the Material-backed `hq-slide-toggle`.

```html
<hq-slide-toggle [(ngModel)]="enabled" [loading]="saving" size="compact">
  Enable notifications
</hq-slide-toggle>
<hq-slide-toggle [formControl]="enabledControl" labelPosition="before">
  Enabled
</hq-slide-toggle>
<hq-slide-toggle [checked]="enabled" (checkedChange)="saveEnabled($event)" [disabled]="!canEdit">
  Enabled
</hq-slide-toggle>
```

Sizes are `regular` (default) and `compact`; both retain a 44px minimum row height.
Loading blocks interaction without disabling the parent form control or changing its value.
Use projected label text or `ariaLabel`/`ariaLabelledby`; `ariaDescribedby` links hint text.
Set `hideIcon="false"` to show Material's state icons. Use `Validators.requiredTrue`
for switches that must be on. Model writes do not emit `checkedChange`.

Customize per instance or container with CSS variables: `--hq-toggle-active`,
`--hq-toggle-inactive`, `--hq-toggle-handle`, `--hq-toggle-label-color`,
`--hq-toggle-font-size`, and `--hq-toggle-gap`. Defaults follow the shared theme.

After changes, rebuild and pack this library, then update each consuming app's local
`hq-shared-lib-0.0.1.tgz` and installed dependency before building that app.
