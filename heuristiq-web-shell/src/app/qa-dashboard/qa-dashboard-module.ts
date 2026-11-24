import { HqChartsModule } from 'hq-shared-lib';
import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { SharedModule } from '../shared/shared-module';
import { QaDonutChart } from './qa-charts/qa-donut-chart';
import { QaLineChart } from './qa-charts/qa-line-chart';
import { QaReleaseChart } from './qa-charts/qa-release-chart';
import { QA_DASHBOARD_ROUTES } from './qa-dashboard.routes';
import { QaApiDocumentsPage } from './qa-api-documents/qa-api-documents';
import { QaEvidenceThumbnail } from './qa-evidence/qa-evidence-thumbnail';
import { QaEndpointDetailDialog } from './qa-endpoint-inventory/qa-endpoint-detail-dialog';
import { QaEndpointInventoryPage } from './qa-endpoint-inventory/qa-endpoint-inventory';
import { QaExecutiveSummaryPage } from './qa-executive-summary/qa-executive-summary';
import { QaFailuresPage } from './qa-failures/qa-failures';
import { QaFilterBar } from './qa-filter-bar/qa-filter-bar';
import { QaHeader } from './qa-header/qa-header';
import { QaOverviewPage } from './qa-overview/qa-overview';
import { QaStudioEditorPage } from './qa-test-case-studio/qa-studio-editor';
import { QaStudioListPage } from './qa-test-case-studio/qa-studio-list';
import { QaTestCasesPage } from './qa-test-cases/qa-test-cases';
import { QaTestSetRunsPage } from './qa-test-set-runs/qa-test-set-runs';
import { QaTestSetsPage } from './qa-test-sets/qa-test-sets';

/** The QA Automation section of the menu: the dashboards, the test case tables and the test sets. */
@NgModule({
  declarations: [
    QaHeader,
    QaFilterBar,
    QaReleaseChart,
    QaDonutChart,
    QaLineChart,
    QaOverviewPage,
    QaTestCasesPage,
    QaFailuresPage,
    QaTestSetRunsPage,
    QaExecutiveSummaryPage,
    QaTestSetsPage,
    QaEndpointInventoryPage,
    QaEndpointDetailDialog,
    QaStudioListPage,
    QaStudioEditorPage,
    QaApiDocumentsPage,
  ],
  imports: [SharedModule, HqChartsModule, QaEvidenceThumbnail, RouterModule.forChild(QA_DASHBOARD_ROUTES)],
})
export class QaDashboardModule {}
