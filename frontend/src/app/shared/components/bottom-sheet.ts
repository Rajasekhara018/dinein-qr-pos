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
  templateUrl: './bottom-sheet.html',
})
export class BottomSheet {
  readonly title = input.required<string>();
  readonly subtitle = input('');
  readonly closed = output<void>();

  readonly titleId = `app-sheet-title-${nextId++}`;
  protected readonly handset = inject(BreakpointService).isHandset;
}
