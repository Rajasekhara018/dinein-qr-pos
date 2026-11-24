import { TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NavigationEnd, NavigationStart, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { HqIdleTimeoutDialog } from 'hq-shared-lib';
import { vi } from 'vitest';

import { SessionOverlayService } from './session-overlay.service';

describe('SessionOverlayService', () => {
  const events = new Subject<unknown>();
  let openDialogs: MatDialogRef<unknown>[];
  const snackBar = { dismiss: vi.fn() };

  const ref = (componentInstance: unknown): MatDialogRef<unknown> =>
    ({ componentInstance, close: vi.fn() }) as unknown as MatDialogRef<unknown>;

  beforeEach(() => {
    openDialogs = [];
    snackBar.dismiss.mockClear();
    TestBed.configureTestingModule({
      providers: [
        { provide: Router, useValue: { events } },
        { provide: MatDialog, useValue: { get openDialogs() { return openDialogs; } } },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
  });

  it('closes every dialog and the snackbar when the app reaches the sign-in page, keeping the idle notice', () => {
    const review = ref({ title: 'Review driver' });
    const confirm = ref({});
    const idleNotice = ref(Object.create(HqIdleTimeoutDialog.prototype));
    openDialogs = [review, confirm, idleNotice];
    TestBed.inject(SessionOverlayService).start();

    events.next(new NavigationEnd(1, '/admin/drivers', '/auth/login'));

    expect(review.close).toHaveBeenCalled();
    expect(confirm.close).toHaveBeenCalled();
    expect(idleNotice.close).not.toHaveBeenCalled();
    expect(snackBar.dismiss).toHaveBeenCalled();
  });

  it('leaves dialogs alone while the session goes on or before navigation ends', () => {
    const review = ref({});
    openDialogs = [review];
    TestBed.inject(SessionOverlayService).start();

    events.next(new NavigationEnd(1, '/admin/drivers', '/admin/rides'));
    events.next(new NavigationStart(2, '/auth/login'));

    expect(review.close).not.toHaveBeenCalled();
    expect(snackBar.dismiss).not.toHaveBeenCalled();
  });
});
