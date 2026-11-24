import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../shared/shared-module';
import { WorkflowHistoryModule } from './history/workflow-history.module';
import { WorkflowShell } from './workflow-shell/workflow-shell';
import { WorkflowTasks } from './tasks/workflow-tasks';
import { CompleteTaskDialog } from './tasks/complete-task-dialog/complete-task-dialog';
import { AddCommentDialog } from './tasks/add-comment-dialog/add-comment-dialog';
import { ReassignDialog } from './tasks/reassign-dialog/reassign-dialog';
import { WorkflowReports } from './reports/workflow-reports';
import { WorkflowStart } from './start/workflow-start';
import { WorkflowInstanceView } from './instance/workflow-instance';
import { WorkflowDefinitions } from './definitions/workflow-definitions';
import { WorkflowDefinition } from './definitions/definition/workflow-definition';
import { BpmnGraphView } from './definitions/bpmn-graph-view/bpmn-graph-view';
import { permissionGuard } from '../auth/permission-guard';
import { WORKFLOW_DESIGNER_PERMISSION, WORKFLOW_TASKS_PERMISSION } from './workflow.constants';

const routes: Routes = [
  {
    path: '',
    component: WorkflowShell,
    children: [
      { path: '', redirectTo: 'tasks', pathMatch: 'full' },
      // The task inbox (and starting or viewing an instance) for whoever works tasks; the designer and reports for whoever may
      // read the process definitions.
      { path: 'tasks', component: WorkflowTasks, canActivate: [permissionGuard], data: { permission: WORKFLOW_TASKS_PERMISSION } },
      { path: 'start', component: WorkflowStart, canActivate: [permissionGuard], data: { permission: WORKFLOW_TASKS_PERMISSION } },
      { path: 'definitions', component: WorkflowDefinitions, canActivate: [permissionGuard], data: { permission: WORKFLOW_DESIGNER_PERMISSION } },
      { path: 'definitions/:key', component: WorkflowDefinition, canActivate: [permissionGuard], data: { permission: WORKFLOW_DESIGNER_PERMISSION } },
      { path: 'reports', component: WorkflowReports, canActivate: [permissionGuard], data: { permission: WORKFLOW_DESIGNER_PERMISSION } },
      { path: 'instances', component: WorkflowInstanceView, canActivate: [permissionGuard], data: { permission: WORKFLOW_TASKS_PERMISSION } },
      { path: 'instances/:id', component: WorkflowInstanceView, canActivate: [permissionGuard], data: { permission: WORKFLOW_TASKS_PERMISSION } },
    ],
  },
];

@NgModule({
  declarations: [WorkflowShell, WorkflowTasks, CompleteTaskDialog, AddCommentDialog, ReassignDialog, WorkflowReports, WorkflowStart, WorkflowInstanceView, WorkflowDefinitions, WorkflowDefinition, BpmnGraphView],
  imports: [SharedModule, WorkflowHistoryModule, RouterModule.forChild(routes)],
})
export class WorkflowModule {}
