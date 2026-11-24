import { Component, inject } from '@angular/core';
import { AuthService } from '../../shared/services/auth-service';
import { WORKFLOW_TABS } from '../workflow.constants';

/**
 * Page chrome for the Workflow screens: heading plus tabs between Tasks, Definitions, Instance and Reports. A tab shows only to
 * someone who may open its screen (the task inbox and the designer have their own permissions).
 */
@Component({
  selector: 'app-workflow-shell',
  standalone: false,
  templateUrl: './workflow-shell.html',
  styleUrl: './workflow-shell.scss',
})
export class WorkflowShell {
  private readonly auth = inject(AuthService);

  get tabs(): typeof WORKFLOW_TABS {
    return WORKFLOW_TABS.filter((tab) => this.auth.canAccess(tab.permission));
  }
}
