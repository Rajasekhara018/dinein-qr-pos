import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { SharedModule } from '../shared/shared-module';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { Workspace } from './workspace';
import { ServiceDesk } from './service-desk/service-desk';
import { WorkspaceTimeline } from './workspace-timeline/workspace-timeline';
import { WorkspaceHelp } from './workspace-help/workspace-help';

const routes: Routes = [{ path: '', component: Workspace }];

/** The operations workspace: the service desk, notes, to-dos, team tasks and handover notes. */
@NgModule({
  declarations: [Workspace, ServiceDesk, WorkspaceTimeline, WorkspaceHelp],
  imports: [SharedModule, DragDropModule, RouterModule.forChild(routes)],
})
export class WorkspaceModule {}
