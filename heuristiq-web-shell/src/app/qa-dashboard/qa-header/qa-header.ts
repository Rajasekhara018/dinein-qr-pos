import { Component, EventEmitter, Input, Output } from '@angular/core';

import { QA_WORKSPACES, QA_WORKSPACE_KEYS } from '../qa-dashboard.constants';
import { QaWorkspace } from '../qa-dashboard.models';

/**
 * The header every QA dashboard screen shares: title, the UI | API workspace switcher, refresh and (when the screen
 * has an export and the user holds its privilege) export.
 */
@Component({
  selector: 'app-qa-header',
  standalone: false,
  templateUrl: './qa-header.html',
  styleUrls: ['../qa-dashboard.shared.scss', './qa-header.scss'],
})
export class QaHeader {
  @Input() title = '';
  @Input() subtitle = '';
  @Input() workspace: QaWorkspace = 'UI';
  @Input() loading = false;
  /** Privilege code of the export action; null = the screen has no export button. */
  @Input() exportPrivilege: string | null = null;
  /** Hide the workspace switcher on a screen whose data is not per workspace. */
  @Input() showWorkspace = true;
  @Output() workspaceChange = new EventEmitter<QaWorkspace>();
  @Output() refresh = new EventEmitter<void>();
  @Output() exported = new EventEmitter<void>();

  readonly workspaces = QA_WORKSPACES;
  readonly workspaceKeys = QA_WORKSPACE_KEYS;

  choose(workspace: QaWorkspace): void {
    if (workspace !== this.workspace) this.workspaceChange.emit(workspace);
  }
}
