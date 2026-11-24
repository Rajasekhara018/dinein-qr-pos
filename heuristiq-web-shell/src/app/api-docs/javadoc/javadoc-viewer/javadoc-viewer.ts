import { Component, OnInit, inject } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { JAVADOC_LIST_ROUTE, JAVADOC_SERVICES, JavadocService } from '../javadoc.constants';

/**
 * One service's Javadoc inside the app shell, at /api-docs/java-doc/:service.
 *
 * Back navigation: opening a service is a normal router navigation, so the browser's Back button
 * returns to the list. The toolbar's Back button goes to the list explicitly as well, because
 * links clicked inside the embedded docs push their own history entries and browser Back would
 * otherwise step through those first.
 */
@Component({
  selector: 'app-javadoc-viewer',
  standalone: false,
  templateUrl: './javadoc-viewer.html',
  styleUrl: './javadoc-viewer.scss',
})
export class JavadocViewer implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly sanitizer = inject(DomSanitizer);

  service: JavadocService | null = null;
  /** Absolute URL, for "open in a new tab". */
  docUrl = '';
  frameUrl: SafeResourceUrl | null = null;
  loading = true;
  /** The frame loaded an error response (e.g. the gateway's JSON 500) instead of Javadoc. */
  unavailable = false;

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => this.open(params.get('service')));
  }

  back(): void {
    void this.router.navigateByUrl(JAVADOC_LIST_ROUTE);
  }

  reload(): void {
    // Re-assigning a new SafeResourceUrl object makes the iframe load its source again.
    this.open(this.service?.id ?? null);
  }

  onFrameLoad(event: Event): void {
    this.loading = false;
    this.unavailable = this.isErrorDocument(event.target as HTMLIFrameElement);
  }

  /**
   * The docs are served from our own origin, so the frame's document can be inspected: Javadoc is
   * HTML, while a service that is down or unreachable makes the gateway answer with a JSON error
   * (or a Whitelabel error page), which would otherwise be shown to the user raw.
   */
  private isErrorDocument(frame: HTMLIFrameElement): boolean {
    try {
      const doc = frame.contentDocument;
      if (!doc) return false;
      if (doc.contentType && doc.contentType !== 'text/html') return true;
      return /Whitelabel Error Page/i.test(doc.title || '') || !!doc.querySelector('body > h1')?.textContent?.match(/Whitelabel Error Page/i);
    } catch {
      // A cross-origin frame cannot be read; treat it as loaded.
      return false;
    }
  }

  private open(id: string | null): void {
    const service = JAVADOC_SERVICES.find((s) => s.id === id) ?? null;
    if (!service) {
      // An unknown id (stale bookmark) is not an error page: show the list.
      this.back();
      return;
    }
    this.service = service;
    this.loading = true;
    this.unavailable = false;
    // The Javadoc is served from the gateway's origin, which is the origin of the configured index URL.
    this.docUrl = new URL(service.path, new URL(environment.javaDocsUrl).origin).href;
    // Safe to trust: the URL is built from our own constants and environment config, never from user input
    // (the route parameter only selects an entry of JAVADOC_SERVICES).
    this.frameUrl = this.sanitizer.bypassSecurityTrustResourceUrl(this.docUrl);
  }
}
