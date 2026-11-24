import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, UrlTree } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HQ_PERMISSION_PRIVILEGES } from 'hq-shared-lib';
import { Observable, of } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../shared/services/auth-service';
import { NotificationService } from '../shared/services/notification-service';
import { permissionGuard } from './permission-guard';
import { privilegesReadyGuard } from './privileges-ready-guard';

describe('permissionGuard', () => {
  const auth = { ensurePrivileges: vi.fn(), canAccess: vi.fn() };
  const router = { createUrlTree: vi.fn((commands: string[]) => ({ commands }) as unknown as UrlTree) };
  const notify = { error: vi.fn() };

  beforeEach(() => {
    TestBed.resetTestingModule();
    vi.clearAllMocks();
    auth.ensurePrivileges.mockReturnValue(of(undefined));
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
        { provide: NotificationService, useValue: notify },
      ],
    });
  });

  const run = (permission?: string) => TestBed.runInInjectionContext(() =>
    permissionGuard({ data: permission ? { permission } : {} } as unknown as ActivatedRouteSnapshot, {} as never));

  it('lets a route without a permission key through without waiting for privileges', () => {
    expect(run()).toBe(true);
    expect(auth.ensurePrivileges).not.toHaveBeenCalled();
  });

  it('lets the user in when the privileges allow the screen', () => {
    auth.canAccess.mockReturnValue(true);
    let result: unknown;

    (run('view_customers') as Observable<unknown>).subscribe((r) => (result = r));

    expect(auth.canAccess).toHaveBeenCalledWith('view_customers');
    expect(result).toBe(true);
    expect(notify.error).not.toHaveBeenCalled();
  });

  it('sends the user home and names the privileges the screen needs', () => {
    auth.canAccess.mockReturnValue(false);
    const key = Object.keys(HQ_PERMISSION_PRIVILEGES)[0];
    let result: unknown;

    (run(key) as Observable<unknown>).subscribe((r) => (result = r));

    expect(notify.error).toHaveBeenCalledWith('shared.errors.noAccess');
    expect(router.createUrlTree).toHaveBeenCalledWith(['/today']);
    expect(result).toEqual({ commands: ['/today'] });
  });

  it('still redirects for a key with no privilege mapping', () => {
    auth.canAccess.mockReturnValue(false);
    let result: unknown;

    (run('unmapped_key') as Observable<unknown>).subscribe((r) => (result = r));

    expect(result).toEqual({ commands: ['/today'] });
  });
});

describe('privilegesReadyGuard', () => {
  it('waits for the privileges and then always lets the route open', () => {
    TestBed.resetTestingModule();
    const ensure = vi.fn(() => of(undefined));
    TestBed.configureTestingModule({ providers: [{ provide: AuthService, useValue: { ensurePrivileges: ensure } }] });
    let result: unknown;

    (TestBed.runInInjectionContext(() => privilegesReadyGuard({} as never, {} as never)) as Observable<boolean>).subscribe((r) => (result = r));

    expect(ensure).toHaveBeenCalled();
    expect(result).toBe(true);
  });
});
