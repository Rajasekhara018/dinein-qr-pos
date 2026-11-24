import { Component, inject } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { environment } from '../../environments/environment';
import { BrandingService } from '../shared/services/branding-service';

@Component({
  selector: 'app-api-docs',
  standalone: false,
  templateUrl: './api-docs.html',
  styleUrl: './api-docs.scss',
})
export class ApiDocs {
  /** Per-deployment branding (app name, logo, tagline), loaded at runtime by BrandingService. */
  readonly branding = inject(BrandingService).branding;
  // Angular sanitizes iframe [src] bindings by default -- bypassSecurityTrustResourceUrl is
  // required to actually load it. Safe here: the URL comes from our own build-time environment
  // config, never from user input.
  readonly swaggerUrl: SafeResourceUrl;

  constructor(sanitizer: DomSanitizer) {
    this.swaggerUrl = sanitizer.bypassSecurityTrustResourceUrl(environment.apiDocsUrl);
  }
}
