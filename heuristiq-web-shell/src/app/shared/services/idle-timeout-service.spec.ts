import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { Idle } from '@ng-idle/core';
import { Router } from '@angular/router';
import { Mock, vi } from 'vitest';
import { BehaviorSubject, Subject, of } from 'rxjs';
import { AuthService } from './auth-service';
import { IdleTimeoutService } from './idle-timeout-service';
import { IDLE_TIMEOUT_CONFIG } from './idle-timeout.constants';

describe('IdleTimeoutService', () => {
  let idle: {
    setIdle: Mock; setTimeout: Mock; setInterrupts: Mock; clearInterrupts: Mock;
    watch: Mock; stop: Mock; isRunning: Mock;
    onIdleStart: Subject<void>; onTimeoutWarning: Subject<number>; onTimeout: Subject<void>;
  };
  let user$: BehaviorSubject<unknown>;
  let authenticated: boolean;
  let auth: { user$: BehaviorSubject<unknown>; isAuthenticated: Mock; logout: Mock };
  let dialogClose: Subject<'stay' | 'logout' | undefined>;
  let dialog: { open: Mock };

  beforeEach(() => {
    authenticated = true;
    idle = {
      setIdle: vi.fn(), setTimeout: vi.fn(),
      setInterrupts: vi.fn(), clearInterrupts: vi.fn(),
      watch: vi.fn(), stop: vi.fn(),
      isRunning: vi.fn().mockReturnValue(false),
      onIdleStart: new Subject<void>(), onTimeoutWarning: new Subject<number>(), onTimeout: new Subject<void>(),
    };
    user$ = new BehaviorSubject<unknown>({ id: 'u1' });
    auth = { user$, isAuthenticated: vi.fn(() => authenticated), logout: vi.fn() };
    dialogClose = new Subject();
    dialog = { open: vi.fn().mockReturnValue({ afterClosed: () => dialogClose.asObservable(), close: vi.fn() }) };

    TestBed.configureTestingModule({
      providers: [
        IdleTimeoutService,
        { provide: Idle, useValue: idle },
        { provide: AuthService, useValue: auth },
        { provide: MatDialog, useValue: dialog },
      ],
    });
  });

  it('configures the idle and warning periods and watches while signed in', () => {
    TestBed.inject(IdleTimeoutService).start();

    expect(idle.setIdle).toHaveBeenCalledWith(IDLE_TIMEOUT_CONFIG.idleSeconds);
    expect(idle.setTimeout).toHaveBeenCalledWith(IDLE_TIMEOUT_CONFIG.warningSeconds);
    expect(idle.watch).toHaveBeenCalledTimes(1);
  });

  it('does not watch while signed out', () => {
    authenticated = false;
    TestBed.inject(IdleTimeoutService).start();

    expect(idle.watch).not.toHaveBeenCalled();
    expect(idle.stop).toHaveBeenCalled();
  });

  it('does not restart a running watcher when the user is re-emitted (token refresh)', () => {
    TestBed.inject(IdleTimeoutService).start();
    idle.isRunning.mockReturnValue(true);
    idle.watch.mockClear();

    user$.next({ id: 'u1' });

    expect(idle.watch).not.toHaveBeenCalled();
  });

  it('opens the warning when idle starts and stops further activity from dismissing it', () => {
    TestBed.inject(IdleTimeoutService).start();

    idle.onIdleStart.next();

    expect(dialog.open).toHaveBeenCalledTimes(1);
    expect(idle.clearInterrupts).toHaveBeenCalled();
  });

  it('resumes watching when the user chooses to stay signed in', () => {
    TestBed.inject(IdleTimeoutService).start();
    idle.onIdleStart.next();
    idle.watch.mockClear();

    dialogClose.next('stay');

    expect(idle.watch).toHaveBeenCalledTimes(1);
    expect(auth.logout).not.toHaveBeenCalled();
  });

  it('logs out when the user chooses to log out', () => {
    TestBed.inject(IdleTimeoutService).start();
    idle.onIdleStart.next();

    dialogClose.next('logout');

    expect(auth.logout).toHaveBeenCalledTimes(1);
  });

  it('ends the session and shows the timed-out notice when the countdown runs out', () => {
    TestBed.inject(IdleTimeoutService).start();
    idle.onIdleStart.next();
    dialog.open.mockClear();

    idle.onTimeout.next();

    expect(auth.logout).toHaveBeenCalledTimes(1);
    expect(dialog.open).toHaveBeenCalledTimes(1);
    expect(dialog.open.mock.lastCall?.[1].data.mode).toBe('expired');
  });

  it('never warns or signs out from a public page (shared trip), it only restarts the countdown', () => {
    TestBed.overrideProvider(Router, { useValue: { url: '/track#token' } });
    TestBed.inject(IdleTimeoutService).start();
    idle.watch.mockClear();

    idle.onIdleStart.next();

    expect(dialog.open).not.toHaveBeenCalled();
    expect(idle.clearInterrupts).not.toHaveBeenCalled();
    expect(idle.watch).toHaveBeenCalledTimes(1);
    expect(auth.logout).not.toHaveBeenCalled();
  });

  it('does nothing on timeout when nobody is signed in', () => {
    TestBed.inject(IdleTimeoutService).start();
    authenticated = false;

    idle.onTimeout.next();

    expect(auth.logout).not.toHaveBeenCalled();
  });
});
