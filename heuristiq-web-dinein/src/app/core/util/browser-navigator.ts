import { DOCUMENT, inject, Injectable } from '@angular/core';

/**
 * Thin wrapper around full-page navigations (leaving the SPA), so payment strategies can be unit-tested.
 */
@Injectable({ providedIn: 'root' })
export class BrowserNavigator {
  private readonly document = inject(DOCUMENT);

  /** `location.assign(url)` — leaves the SPA. */
  assign(url: string): void {
    this.document.defaultView?.location.assign(url);
  }

  /** Builds a hidden form and submits it (hosted-checkout providers such as PayU). */
  submitForm(action: string, fields: Record<string, string | number>, method = 'POST'): void {
    const form = this.document.createElement('form');
    form.method = method.toUpperCase() === 'GET' ? 'GET' : 'POST';
    form.action = action;
    form.style.display = 'none';
    form.setAttribute('aria-hidden', 'true');
    for (const [name, value] of Object.entries(fields)) {
      const input = this.document.createElement('input');
      input.type = 'hidden';
      input.name = name;
      input.value = String(value ?? '');
      form.appendChild(input);
    }
    this.document.body.appendChild(form);
    form.submit();
  }

  print(): void {
    this.document.defaultView?.print();
  }
}
