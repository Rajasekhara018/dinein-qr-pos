import { Routes } from '@angular/router';

import { permissionGuard } from '../auth/permission-guard';
import { QA_PERMISSIONS, QA_SCREEN_KEYS } from './qa-dashboard.constants';
import { QaApiDocumentsPage } from './qa-api-documents/qa-api-documents';
import { QaEndpointInventoryPage } from './qa-endpoint-inventory/qa-endpoint-inventory';
import { pendingChangesGuard } from '../shared/guards/pending-changes.guard';
import { QaExecutiveSummaryPage } from './qa-executive-summary/qa-executive-summary';
import { QaFailuresPage } from './qa-failures/qa-failures';
import { QaOverviewPage } from './qa-overview/qa-overview';
import { QaTestCasesPage } from './qa-test-cases/qa-test-cases';
import { QaStudioEditorPage } from './qa-test-case-studio/qa-studio-editor';
import { QaStudioListPage } from './qa-test-case-studio/qa-studio-list';
import { QaTestSetRunsPage } from './qa-test-set-runs/qa-test-set-runs';
import { QaTestSetsPage } from './qa-test-sets/qa-test-sets';

/**
 * Routes below /qa. Each screen has its own permission key and audit screen key; the Regression, Smoke, inventory
 * and execution screens are one component told which table to show (`kind`).
 */
export const QA_DASHBOARD_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'overview' },
  {
    path: 'overview',
    component: QaOverviewPage,
    canActivate: [permissionGuard],
    data: { permission: QA_PERMISSIONS.overview, screenKey: QA_SCREEN_KEYS.overview },
  },
  {
    path: 'regression',
    component: QaTestCasesPage,
    canActivate: [permissionGuard],
    data: { permission: QA_PERMISSIONS.suites, screenKey: QA_SCREEN_KEYS.regression, kind: 'regression' },
  },
  {
    path: 'smoke',
    component: QaTestCasesPage,
    canActivate: [permissionGuard],
    data: { permission: QA_PERMISSIONS.suites, screenKey: QA_SCREEN_KEYS.smoke, kind: 'smoke' },
  },
  {
    path: 'failures',
    component: QaFailuresPage,
    canActivate: [permissionGuard],
    data: { permission: QA_PERMISSIONS.failures, screenKey: QA_SCREEN_KEYS.failures },
  },
  {
    path: 'executions/test-cases',
    component: QaTestCasesPage,
    canActivate: [permissionGuard],
    data: { permission: QA_PERMISSIONS.executions, screenKey: QA_SCREEN_KEYS.executionsTestCases, kind: 'executions' },
  },
  {
    path: 'executions/test-sets',
    component: QaTestSetRunsPage,
    canActivate: [permissionGuard],
    data: { permission: QA_PERMISSIONS.testSetExecutions, screenKey: QA_SCREEN_KEYS.executionsTestSets },
  },
  {
    path: 'executive-summary',
    component: QaExecutiveSummaryPage,
    canActivate: [permissionGuard],
    data: { permission: QA_PERMISSIONS.executiveSummary, screenKey: QA_SCREEN_KEYS.executiveSummary },
  },
  {
    path: 'test-cases',
    component: QaTestCasesPage,
    canActivate: [permissionGuard],
    data: { permission: QA_PERMISSIONS.testCases, screenKey: QA_SCREEN_KEYS.testCases, kind: 'inventory' },
  },
  {
    path: 'test-sets',
    component: QaTestSetsPage,
    canActivate: [permissionGuard],
    data: { permission: QA_PERMISSIONS.testSets, screenKey: QA_SCREEN_KEYS.testSets },
  },
  {
    path: 'endpoint-inventory',
    component: QaEndpointInventoryPage,
    canActivate: [permissionGuard],
    data: { permission: QA_PERMISSIONS.endpointInventory, screenKey: QA_SCREEN_KEYS.endpointInventory },
  },
  {
    path: 'test-case-studio',
    component: QaStudioListPage,
    canActivate: [permissionGuard],
    data: { permission: QA_PERMISSIONS.testCaseStudio, screenKey: QA_SCREEN_KEYS.testCaseStudio },
  },
  // The editor: "new" must come before ":id" so it is not read as an id. Leaving it with unsaved changes asks first.
  {
    path: 'test-case-studio/new',
    component: QaStudioEditorPage,
    canActivate: [permissionGuard],
    canDeactivate: [pendingChangesGuard],
    data: { permission: QA_PERMISSIONS.testCaseStudio, screenKey: QA_SCREEN_KEYS.testCaseStudio },
  },
  {
    path: 'test-case-studio/:id',
    component: QaStudioEditorPage,
    canActivate: [permissionGuard],
    canDeactivate: [pendingChangesGuard],
    data: { permission: QA_PERMISSIONS.testCaseStudio, screenKey: QA_SCREEN_KEYS.testCaseStudio },
  },
  // Writing a module's document in place: leaving with unsaved text asks first.
  {
    path: 'api-documents',
    component: QaApiDocumentsPage,
    canActivate: [permissionGuard],
    canDeactivate: [pendingChangesGuard],
    data: { permission: QA_PERMISSIONS.apiDocuments, screenKey: QA_SCREEN_KEYS.apiDocuments },
  },
];
