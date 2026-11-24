import { Injectable } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Observable } from 'rxjs';
import { HqLookupConfig, HqLookupDialog } from './hq-lookup-dialog/hq-lookup-dialog';

/**
 * Opens the search-and-pick dialog for choosing records from a source too large to list in a dropdown.
 * The records are fetched from the server a page at a time, so the cost does not grow with the table.
 * Resolves the chosen records (selection kept across pages and searches), or null when cancelled.
 *   this.lookup.open<User>({
 *     title: 'Select users', multiple: true, idOf: (u) => u.id,
 *     columns: [{ key: 'name', label: 'Name' }, { key: 'email', label: 'Email' }],
 *     fetch: (q, page, size) => this.api.searchUsers(q, page, size),   // -> { items, total }
 *   }).subscribe((users) => ...);
 */
@Injectable({ providedIn: 'root' })
export class HqLookupService {
  constructor(private dialog: MatDialog) {}

  open<T extends object>(config: HqLookupConfig<T>): Observable<T[] | null> {
    return this.dialog.open<HqLookupDialog<T>, HqLookupConfig<T>, T[] | null>(HqLookupDialog, {
      width: '920px',
      maxWidth: '96vw',
      maxHeight: '92vh',
      autoFocus: false,
      restoreFocus: true,
      panelClass: 'app-dialog-panel',
      data: config,
    }).afterClosed() as Observable<T[] | null>;
  }
}
