import { ChangeDetectorRef, Component, Input, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { WorkflowService, workflowErrorMessage } from '../workflow-service';
import { InstanceTimeline, TimelineEntry } from '../workflow.models';
import { DECISION_KEY_PREFIX } from '../workflow.constants';
import { WorkflowPeopleService } from './workflow-people.service';

/**
 * A request's history (who did what, when, how long each step took), people shown by name. Used on the Workflow instance page
 * and in the history dialog of the onboarding screens. Reads the history whenever `instanceId` or `refresh` changes.
 */
@Component({
  selector: 'app-workflow-timeline',
  standalone: false,
  templateUrl: './workflow-timeline.html',
  styleUrl: './workflow-timeline.scss',
})
export class WorkflowTimeline {
  private readonly workflow = inject(WorkflowService);
  private readonly people = inject(WorkflowPeopleService);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);

  readonly decisionKeyPrefix = DECISION_KEY_PREFIX;
  timeline: InstanceTimeline | null = null;
  names: Record<string, string> = {};
  isLoading = false;
  failed = false;
  errorText = '';
  /** Hides the reference and outcome heading (the dialog shows them in its own header). */
  @Input() compact = false;

  private id = '';

  @Input() set instanceId(value: string | null | undefined) {
    this.id = value ?? '';
    this.load();
  }

  /** Changing it reads the history again (the instance page's Refresh). */
  @Input() set refresh(_: number) {
    if (this.id) this.load();
  }

  load(): void {
    if (!this.id) {
      this.timeline = null;
      return;
    }
    this.isLoading = true;
    this.failed = false;
    this.workflow.timeline(this.id).subscribe({
      next: (timeline) => {
        this.timeline = timeline;
        this.isLoading = false;
        this.loadNames(timeline);
        this.cdr.markForCheck();
      },
      error: () => {
        this.timeline = null;
        this.isLoading = false;
        this.failed = true;
        this.cdr.markForCheck();
      },
    });
  }

  download(entry: TimelineEntry): void {
    if (!entry.attachmentId) return;
    this.workflow.downloadAttachment(entry.attachmentId, entry.text ?? '').subscribe({
      error: (err) => {
        this.errorText = workflowErrorMessage(err) ?? this.translate.instant('workflow.timeline.downloadFailed');
        this.cdr.markForCheck();
      },
    });
  }

  /** The new assignee of a REASSIGNED entry, by name. */
  targetName(entry: TimelineEntry): string {
    return entry.text ? (this.names[entry.text] ?? entry.text) : '';
  }

  actorName(entry: TimelineEntry): string {
    return entry.actor ? (this.names[entry.actor] ?? entry.actor) : this.translate.instant('workflow.timeline.system');
  }

  /** "1d 2h", "3h 5m", "40m": how long a step was open. */
  duration(ms: number | null | undefined): string {
    if (ms === null || ms === undefined || ms < 0) return '';
    const minutes = Math.floor(ms / 60000);
    const d = Math.floor(minutes / 1440);
    const h = Math.floor((minutes % 1440) / 60);
    const m = minutes % 60;
    const unit = (k: string) => this.translate.instant(`workflow.timeline.units.${k}`);
    if (d > 0) return `${d}${unit('d')} ${h}${unit('h')}`;
    if (h > 0) return `${h}${unit('h')} ${m}${unit('m')}`;
    return `${m}${unit('m')}`;
  }

  iconOf(entry: TimelineEntry): string {
    switch (entry.type) {
      case 'STARTED': return 'bi-send';
      case 'TASK_CREATED': return 'bi-hourglass-split';
      case 'CLAIMED': return 'bi-person-check';
      case 'RELEASED': return 'bi-box-arrow-left';
      case 'RETURNED': return 'bi-arrow-return-left';
      case 'ATTACHMENT': return 'bi-paperclip';
      case 'REASSIGNED': return 'bi-person-up';
      case 'COMMENT': return 'bi-chat-left-text';
      case 'COMPLETED': return entry.decision === 'REJECTED' ? 'bi-x-circle' : 'bi-check-circle';
      default: return entry.decision === 'REJECTED' ? 'bi-x-octagon' : entry.decision === 'RETURNED' ? 'bi-arrow-return-left' : 'bi-flag';
    }
  }

  trackByEntry(index: number): number {
    return index;
  }

  private loadNames(timeline: InstanceTimeline): void {
    const ids = timeline.entries.flatMap((e) => [e.actor, e.type === 'REASSIGNED' ? e.text : null]);
    this.people.names(ids).subscribe((names) => {
      this.names = names;
      this.cdr.markForCheck();
    });
  }
}
