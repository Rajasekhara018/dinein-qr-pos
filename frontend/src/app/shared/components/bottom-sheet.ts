import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { BreakpointService } from '../../core/ui/breakpoint.service';

let nextId = 0;

/**
 * Chrome for content opened via `SheetService`: bottom sheet (rounded top, grab handle) on phones, centered dialog
 * on ≥ 768px. Scrollable body; sticky footer (`[sheetFooter]`) padded for the iPhone home indicator.
 *
 * ```html
 * <app-bottom-sheet [title]="item.name" (closed)="ref.close()">
 *   …body…
 *   <div sheetFooter>…actions…</div>
 * </app-bottom-sheet>
 * ```
 * Pass `ariaLabelledBy: sheet.titleId` is not needed: the dialog uses `autoFocus: 'first-heading'` and the heading
 * id is exposed as `titleId` for aria wiring.
 */
@Component({
  selector: 'app-bottom-sheet',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class:
      'flex max-h-[inherit] w-full min-w-0 flex-col overflow-hidden bg-surface text-ink shadow-raised',
    '[class.rounded-t-sheet]': 'handset()',
    '[class.animate-sheet-up]': 'handset()',
    '[class.rounded-sheet]': '!handset()',
    '[class.animate-pop]': '!handset()',
    '[attr.aria-labelledby]': 'titleId',
  },
  template: `
    @if (handset()) {
      <div class="flex justify-center pt-2" aria-hidden="true">
        <span class="h-1.5 w-10 rounded-full bg-line-strong"></span>
      </div>
    }
    <header class="flex items-start gap-3 border-b border-line px-4 py-3 sm:px-6">
      <div class="min-w-0 flex-1">
        <h2 [id]="titleId" tabindex="-1" class="font-display text-lg font-semibold leading-tight outline-none sm:text-xl">
          {{ title() }}
        </h2>
        @if (subtitle()) {
          <p class="mt-0.5 text-sm text-ink-muted">{{ subtitle() }}</p>
        }
        <ng-content select="[sheetHeader]" />
      </div>
      <button appIconButton type="button" class="-mr-2 -mt-1" aria-label="Close" (click)="closed.emit()">
        <svg viewBox="0 0 20 20" class="size-5 fill-current" aria-hidden="true"><path d="M5.3 4 10 8.6 14.7 4 16 5.3 11.4 10l4.6 4.7-1.3 1.3-4.7-4.6L5.3 16 4 14.7 8.6 10 4 5.3 5.3 4Z"/></svg>
      </button>
    </header>
    <div class="min-h-0 flex-1 overflow-y-auto overscroll-contain px-4 py-4 sm:px-6">
      <ng-content />
    </div>
    <footer class="border-t border-line bg-surface px-4 pt-3 pb-[calc(0.75rem+env(safe-area-inset-bottom))] sm:px-6 sm:pb-4 empty:hidden">
      <ng-content select="[sheetFooter]" />
    </footer>
  `,
})
export class BottomSheet {
  readonly title = input.required<string>();
  readonly subtitle = input('');
  readonly closed = output<void>();

  readonly titleId = `app-sheet-title-${nextId++}`;
  protected readonly handset = inject(BreakpointService).isHandset;
}
