import { Component, Input } from '@angular/core';

import { WorkspaceTab } from '../workspace.constants';
import { WORKSPACE_HELP, WORKSPACE_HELP_STORAGE_KEY, WorkspaceHelpContent } from './workspace-help.constants';

/**
 * A short guide on top of each Notes & tasks tab: what the tab is for, how to use it and a worked example. The reader can
 * fold it away; that choice is kept in this browser only (and the guide simply shows when the browser keeps nothing).
 */
@Component({
  selector: 'app-workspace-help',
  standalone: false,
  templateUrl: './workspace-help.html',
  styleUrl: './workspace-help.scss',
})
export class WorkspaceHelp {
  /** The tab whose guide is shown. */
  @Input() tab: WorkspaceTab['key'] = 'desk';

  folded = readFolded();

  get content(): WorkspaceHelpContent {
    return WORKSPACE_HELP[this.tab];
  }

  toggle(): void {
    this.folded = !this.folded;
    try {
      localStorage.setItem(WORKSPACE_HELP_STORAGE_KEY, String(this.folded));
    } catch {
      // Storage blocked (private window): the choice lasts until the page is left.
    }
  }
}

/** Folded by default, like every help panel in the apps; open only when the person opened it before (kept as "false"). */
function readFolded(): boolean {
  try {
    return localStorage.getItem(WORKSPACE_HELP_STORAGE_KEY) !== 'false';
  } catch {
    return true;
  }
}
