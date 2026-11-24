import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { vi } from 'vitest';
import { AuthService } from '../shared/services/auth-service';
import { authGuard } from './auth-guard';

describe('authGuard', () => {
  let authenticated: boolean;
  const router = { createUrlTree: vi.fn((commands: string[]) => ({ commands }) as unknown as UrlTree) };

  beforeEach(() => {
    authenticated = false;
    router.createUrlTree.mockClear();
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { isAuthenticated: () => authenticated } },
        { provide: Router, useValue: router },
      ],
    });
  });

  const run = () => TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));

  it('lets a signed-in user through', () => {
    authenticated = true;

    expect(run()).toBe(true);
    expect(router.createUrlTree).not.toHaveBeenCalled();
  });

  it('sends a visitor without a session to the login page', () => {
    const result = run();

    expect(router.createUrlTree).toHaveBeenCalledWith(['/auth/login']);
    expect(result).not.toBe(true);
  });
});
