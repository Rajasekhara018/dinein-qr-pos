import { ChangeDetectionStrategy, Component, ElementRef, inject, model, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { debounceTime, distinctUntilChanged, filter, Subject, switchMap } from 'rxjs';
import { AdminMenuApi, AdminOrdersApi } from '../../../../core/api/admin.api';
import { AuthStore } from '../../../../core/auth/auth.store';
import { visibleNav } from '../../data/admin-nav';

interface PaletteResult {
  id: string;
  label: string;
  sublabel?: string;
  section: 'Pages' | 'Items' | 'Orders';
  path: string;
}

/**
 * Ctrl+K / Cmd+K command palette (also opened by clicking the header's "Quick navigate…" search box) — jump to any
 * admin page, or search loaded items/orders by name/token. Keyboard: ↑/↓ to move, Enter to go, Escape to close.
 * Kept intentionally simple: a substring match over the known static routes, plus a small debounced live lookup —
 * not a fuzzy-search engine.
 */
@Component({
  selector: 'app-command-palette',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './command-palette.html',
})
export class CommandPalette {
  private readonly router = inject(Router);
  private readonly auth = inject(AuthStore);
  private readonly itemsApi = inject(AdminMenuApi);
  private readonly ordersApi = inject(AdminOrdersApi);

  readonly open = model(false);

  protected readonly query = signal('');
  protected readonly activeIndex = signal(0);
  protected readonly pageResults = signal<PaletteResult[]>([]);
  protected readonly liveResults = signal<PaletteResult[]>([]);
  protected readonly loading = signal(false);

  protected readonly inputRef = viewChild<ElementRef<HTMLInputElement>>('paletteInput');

  private readonly search$ = new Subject<string>();

  constructor() {
    this.search$
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        filter((q) => q.trim().length >= 2),
        switchMap((q) => {
          this.loading.set(true);
          return this.runLiveSearch(q);
        }),
        takeUntilDestroyed(),
      )
      .subscribe((results) => {
        this.liveResults.set(results);
        this.loading.set(false);
      });
  }

  protected onOpenChange(open: boolean): void {
    this.open.set(open);
    if (open) {
      this.query.set('');
      this.activeIndex.set(0);
      this.liveResults.set([]);
      this.pageResults.set(this.matchPages(''));
      setTimeout(() => this.inputRef()?.nativeElement.focus(), 0);
    }
  }

  protected close(): void {
    this.onOpenChange(false);
  }

  protected onQuery(value: string): void {
    this.query.set(value);
    this.activeIndex.set(0);
    this.pageResults.set(this.matchPages(value));
    if (value.trim().length < 2) this.liveResults.set([]);
    this.search$.next(value);
  }

  protected readonly results = () => [...this.pageResults(), ...this.liveResults()];

  protected onKeydown(event: KeyboardEvent): void {
    const results = this.results();
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.activeIndex.set(Math.min(this.activeIndex() + 1, results.length - 1));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.activeIndex.set(Math.max(this.activeIndex() - 1, 0));
    } else if (event.key === 'Enter') {
      event.preventDefault();
      const result = results[this.activeIndex()];
      if (result) this.go(result);
    } else if (event.key === 'Escape') {
      this.close();
    }
  }

  protected go(result: PaletteResult): void {
    void this.router.navigateByUrl(result.path);
    this.close();
  }

  private matchPages(query: string): PaletteResult[] {
    const q = query.trim().toLowerCase();
    const items = visibleNav(this.auth.isOwner(), this.auth.isPlatformAdmin());
    return items
      .filter((item) => !q || item.label.toLowerCase().includes(q))
      .map((item) => ({ id: `page-${item.path}`, label: item.label, section: 'Pages' as const, path: item.path }));
  }

  private runLiveSearch(q: string) {
    return new Promise<PaletteResult[]>((resolve) => {
      let itemHits: PaletteResult[] = [];
      let orderHits: PaletteResult[] = [];
      let pending = 2;
      const done = () => {
        pending -= 1;
        if (pending === 0) resolve([...itemHits, ...orderHits]);
      };
      this.itemsApi.items({ q, size: 5 }).subscribe({
        next: (page) => {
          itemHits = page.content.map((it) => ({
            id: `item-${it.id}`,
            label: it.name,
            sublabel: it.categoryName,
            section: 'Items' as const,
            path: `/admin/menu/items/${it.id}`,
          }));
        },
        error: () => (itemHits = []),
        complete: done,
      });
      this.ordersApi.list({ q, size: 5 }).subscribe({
        next: (page) => {
          orderHits = page.content.map((o) => ({
            id: `order-${o.id}`,
            label: `#${o.displayToken} · ${o.orderNumber}`,
            sublabel: o.customerName || o.tableLabel,
            section: 'Orders' as const,
            path: `/admin/orders/${o.id}`,
          }));
        },
        error: () => (orderHits = []),
        complete: done,
      });
    });
  }
}
