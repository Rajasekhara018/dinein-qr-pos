import { WorkspaceTab } from '../workspace.constants';

/** Where the reader's choice to fold the help away is kept (per browser). */
export const WORKSPACE_HELP_STORAGE_KEY = 'rou.workspace.help.folded';

/** The help of one tab: its translation keys under workspace.help.<tab>. */
export interface WorkspaceHelpContent {
  summaryKey: string;
  pointKeys: readonly string[];
  exampleKeys: readonly string[];
}

const helpOf = (tab: WorkspaceTab['key'], points: number, steps: number): WorkspaceHelpContent => ({
  summaryKey: `workspace.help.${tab}.summary`,
  pointKeys: Array.from({ length: points }, (_, i) => `workspace.help.${tab}.p${i + 1}`),
  exampleKeys: Array.from({ length: steps }, (_, i) => `workspace.help.${tab}.e${i + 1}`),
});

/** What each tab is for, how to use it, and a worked example. */
export const WORKSPACE_HELP: Readonly<Record<WorkspaceTab['key'], WorkspaceHelpContent>> = {
  desk: helpOf('desk', 5, 4),
  todos: helpOf('todos', 4, 3),
  tasks: helpOf('tasks', 5, 4),
  notes: helpOf('notes', 4, 3),
  handover: helpOf('handover', 4, 3),
};
