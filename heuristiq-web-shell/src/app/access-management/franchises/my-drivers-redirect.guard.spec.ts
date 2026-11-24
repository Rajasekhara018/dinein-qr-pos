import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, ROUTES, Route, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { Observable, firstValueFrom, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { permissionGuard } from '../../auth/permission-guard';
import { AccessManagementRoutingModule } from '../access-management-routing-module';
import { FranchiseApi } from './franchise-api';
import { FRANCHISE_DRIVERS_PERMISSION, MY_DRIVERS_ROUTE } from './franchise-drivers.constants';
import { Franchise } from './franchise.models';
import { myDriversRedirectGuard } from './my-drivers-redirect.guard';

describe('myDriversRedirectGuard', () => {
  const api = { mine: vi.fn() };
  const router = { createUrlTree: vi.fn((commands: string[]) => ({ commands }) as unknown as UrlTree) };

  beforeEach(() => {
    TestBed.resetTestingModule();
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        { provide: FranchiseApi, useValue: api },
        { provide: Router, useValue: router },
      ],
    });
  });

  const franchise = (unitId: string) => ({ unitId, code: unitId.toUpperCase(), name: unitId }) as Franchise;
  const run = async (): Promise<string[]> => {
    const result = TestBed.runInInjectionContext(() =>
      myDriversRedirectGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)) as Observable<UrlTree>;
    return ((await firstValueFrom(result)) as unknown as { commands: string[] }).commands;
  };

  it('opens the drivers of the owner\'s only franchise', async () => {
    api.mine.mockReturnValue(of([franchise('f-1')]));

    expect(await run()).toEqual(['/access-management', 'franchises', 'f-1', 'drivers']);
  });

  it('opens My franchises to pick one when the owner has several', async () => {
    api.mine.mockReturnValue(of([franchise('f-1'), franchise('f-2')]));

    expect(await run()).toEqual(['/access-management', 'my-franchises']);
  });

  it('opens My franchises when the owner has none', async () => {
    api.mine.mockReturnValue(of([]));

    expect(await run()).toEqual(['/access-management', 'my-franchises']);
  });

  it('opens My franchises when the list cannot be read, instead of failing the navigation', async () => {
    api.mine.mockReturnValue(throwError(() => new Error('franchise service down')));

    expect(await run()).toEqual(['/access-management', 'my-franchises']);
  });
});

describe('My drivers route', () => {
  it('checks the drivers permission first, then redirects; it has no screen of its own', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ imports: [AccessManagementRoutingModule] });
    const routes = (TestBed.inject(ROUTES) as Route[][]).flat();
    const route = routes.find((r) => r.path === MY_DRIVERS_ROUTE);

    expect(route?.component).toBeUndefined();
    expect(route?.children).toEqual([]);
    expect(route?.canActivate).toEqual([permissionGuard, myDriversRedirectGuard]);
    expect(route?.data).toMatchObject({ permission: FRANCHISE_DRIVERS_PERMISSION });
    expect(route?.data?.['screenKey']).toBeUndefined();
  });
});
