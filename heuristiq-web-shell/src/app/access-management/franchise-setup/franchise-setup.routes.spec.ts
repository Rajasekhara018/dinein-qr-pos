import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ROUTES, Route, Router, provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { pendingChangesGuard } from '../../shared/guards/pending-changes.guard';
import { AccessManagementRoutingModule } from '../access-management-routing-module';
import { FranchiseSetupPage } from './franchise-setup';
import {
  FRANCHISE_SETUP_PERMISSION, FRANCHISE_SETUP_SCREEN_KEY, MY_FRANCHISE_SETUP_PERMISSION, MY_FRANCHISE_SETUP_SCREEN_KEY,
} from './franchise-setup.constants';

@Component({ template: '', standalone: true })
class Landing {}

function accessManagementRoutes(): Route[] {
  TestBed.configureTestingModule({ imports: [AccessManagementRoutingModule] });
  return (TestBed.inject(ROUTES) as Route[][]).flat();
}

const find = (routes: Route[], path: string): Route | undefined => routes.find((r) => r.path === path);

describe('franchise setup routes', () => {
  it('opens the setup of any franchise (administrators) and of their own (owners), guarded, with its screen key', () => {
    const routes = accessManagementRoutes();
    const admin = find(routes, 'franchises/:id/setup/:step');
    expect(admin?.component).toBe(FranchiseSetupPage);
    expect(admin?.canDeactivate).toEqual([pendingChangesGuard]);
    expect(admin?.canActivate?.length).toBe(1);
    expect(admin?.data).toMatchObject({ permission: FRANCHISE_SETUP_PERMISSION, screenKey: FRANCHISE_SETUP_SCREEN_KEY, subject: 'franchise' });
    const owner = find(routes, 'my-franchises/:id/setup/:step');
    expect(owner?.component).toBe(FranchiseSetupPage);
    expect(owner?.data).toMatchObject({ permission: MY_FRANCHISE_SETUP_PERMISSION, screenKey: MY_FRANCHISE_SETUP_SCREEN_KEY, subject: 'myFranchise' });
  });

  it('opens a new franchise on the same setup screen, and gives no redirect a guard', () => {
    const routes = accessManagementRoutes();
    expect(find(routes, 'franchises/new')).toMatchObject({ component: FranchiseSetupPage, data: expect.objectContaining({ subject: 'franchise' }) });
    expect(find(routes, 'franchises/new')?.canDeactivate).toEqual([pendingChangesGuard]);
    expect(routes.filter((r) => !!r.redirectTo && !!r.canActivate)).toEqual([]);
  });

  it('lands the old edit, legal-profile and onboarding addresses on their step', async () => {
    const redirects = accessManagementRoutes().filter((r) => !!r.redirectTo && /^(my-)?franchises\/:id\//.test(r.path ?? ''));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideRouter([
        ...redirects,
        { path: 'franchises/:id/setup/:step', component: Landing },
        { path: 'my-franchises/:id/setup/:step', component: Landing },
      ])],
    });
    const router = TestBed.inject(Router);
    const lands = async (from: string, to: string) => {
      await router.navigateByUrl(from);
      expect(router.url).toBe(to);
    };
    await lands('/franchises/f1/edit', '/franchises/f1/setup/details');
    await lands('/franchises/f1/legal-profile', '/franchises/f1/setup/legal');
    await lands('/franchises/f1/onboarding', '/franchises/f1/setup/kyc');
    await lands('/franchises/f1/setup', '/franchises/f1/setup/details');
    await lands('/my-franchises/f1/legal-profile', '/my-franchises/f1/setup/legal');
    await lands('/my-franchises/f1/onboarding', '/my-franchises/f1/setup/kyc');
    await lands('/my-franchises/f1/setup', '/my-franchises/f1/setup/legal');
  });
});
