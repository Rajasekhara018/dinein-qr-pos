import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ROUTES, Route, Router, provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { pendingChangesGuard } from '../../shared/guards/pending-changes.guard';
import { AccessManagementRoutingModule } from '../access-management-routing-module';
import { AdminFormPage } from '../admin-form-page/admin-form-page';
import { OrganizationSetupPage } from './organization-setup';
import { ORGANIZATION_SETUP_PERMISSION, ORGANIZATION_SETUP_SCREEN_KEY } from './organization-setup.constants';

@Component({ template: '', standalone: true })
class Landing {}

function accessManagementRoutes(): Route[] {
  TestBed.configureTestingModule({ imports: [AccessManagementRoutingModule] });
  return (TestBed.inject(ROUTES) as Route[][]).flat();
}

const find = (routes: Route[], path: string): Route | undefined => routes.find((r) => r.path === path);

describe('organization setup routes', () => {
  it('opens the setup on the details step by default, and each step by its own segment', () => {
    const routes = accessManagementRoutes();
    expect(find(routes, 'organizations/:id/setup')).toMatchObject({ redirectTo: 'organizations/:id/setup/details', pathMatch: 'full' });
    const step = find(routes, 'organizations/:id/setup/:step');
    expect(step?.component).toBe(OrganizationSetupPage);
    expect(step?.canDeactivate).toEqual([pendingChangesGuard]);
    expect(step?.data).toMatchObject({ permission: ORGANIZATION_SETUP_PERMISSION, screenKey: ORGANIZATION_SETUP_SCREEN_KEY });
    expect(step?.canActivate?.length).toBe(1);
  });

  it('sends the old edit and legal-profile addresses to their step', () => {
    const routes = accessManagementRoutes();
    expect(find(routes, 'organizations/:id/edit')).toMatchObject({ redirectTo: 'organizations/:id/setup/details', pathMatch: 'full' });
    expect(find(routes, 'organizations/:id/legal-profile')).toMatchObject({ redirectTo: 'organizations/:id/setup/legal', pathMatch: 'full' });
    expect(find(routes, 'organizations/:id/edit')?.component).toBeUndefined();
  });

  it('keeps the create page and the edit pages of every other entity', () => {
    const routes = accessManagementRoutes();
    expect(find(routes, 'organizations/new')?.component).toBe(AdminFormPage);
    expect(find(routes, 'users/:id/edit')?.component).toBe(AdminFormPage);
    expect(find(routes, 'applications/:id/edit')?.component).toBe(AdminFormPage);
  });

  it('really lands an old bookmark on the step (the redirects as configured, run by the router)', async () => {
    const redirects = accessManagementRoutes().filter((r) => !!r.redirectTo && (r.path ?? '').startsWith('organizations/:id/'));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideRouter([...redirects, { path: 'organizations/:id/setup/:step', component: Landing }])],
    });
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/organizations/o1/edit');
    expect(router.url).toBe('/organizations/o1/setup/details');
    await router.navigateByUrl('/organizations/o1/legal-profile');
    expect(router.url).toBe('/organizations/o1/setup/legal');
    await router.navigateByUrl('/organizations/o1/setup');
    expect(router.url).toBe('/organizations/o1/setup/details');
  });
});
