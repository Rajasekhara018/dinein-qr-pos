import { ChangeDetectorRef, Component, Input, OnChanges, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { finalize } from 'rxjs/operators';

import { AuthService } from '../../shared/services/auth-service';
import { NotificationService } from '../../shared/services/notification-service';
import { SharedModule } from '../../shared/shared-module';
import { WORKSPACE_MAX_BODY, WORKSPACE_MESSAGE_KEYS, WORKSPACE_PRIVILEGES } from '../workspace.constants';
import { canEditItem } from '../workspace.helpers';
import { WorkspaceItem } from '../workspace.models';
import { WorkspaceService } from '../workspace.service';

/**
 * The team's notes on one record (a profile, a submission, an agent ...): shown to every colleague who may read the
 * workspace, written by anyone who may create items, deleted only by their author. The record is named by a type and an id
 * (for example ONBOARDED_USER and the submission id); the type is a fixed code of the screen that embeds this panel.
 */
@Component({
  selector: 'app-record-notes',
  standalone: true,
  imports: [SharedModule],
  templateUrl: './record-notes.html',
  styleUrl: './record-notes.scss',
})
export class RecordNotes implements OnChanges {
  private readonly svc = inject(WorkspaceService);
  private readonly auth = inject(AuthService);
  private readonly notify = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);

  /** Type of the record the notes are about. */
  @Input({ required: true }) refType!: string;
  /** Id of the record the notes are about. */
  @Input({ required: true }) refId!: string;

  readonly privileges = WORKSPACE_PRIVILEGES;
  readonly maxBody = WORKSPACE_MAX_BODY;

  notes: WorkspaceItem[] = [];
  text = '';
  loading = false;
  saving = false;
  loadError = false;

  get canAdd(): boolean {
    return !this.saving && this.text.trim().length > 0;
  }

  ngOnChanges(): void {
    if (this.refType && this.refId) this.load();
  }

  private load(): void {
    this.loading = true;
    this.loadError = false;
    this.svc
      .list({ kinds: ['RECORD_NOTE'], refType: this.refType, refId: this.refId })
      .pipe(finalize(() => { this.loading = false; this.cdr.markForCheck(); }))
      .subscribe({
        next: (page) => (this.notes = page.content),
        error: () => (this.loadError = true),
      });
  }

  add(): void {
    if (!this.canAdd) return;
    this.saving = true;
    this.svc
      .create({ kind: 'RECORD_NOTE', body: this.text, refType: this.refType, refId: this.refId })
      .pipe(finalize(() => { this.saving = false; this.cdr.markForCheck(); }))
      .subscribe({
        next: () => {
          this.text = '';
          this.load();
        },
        error: () => this.notify.error(this.translate.instant(WORKSPACE_MESSAGE_KEYS.saveFailed)),
      });
  }

  remove(note: WorkspaceItem): void {
    this.svc.remove(note.id).subscribe({
      next: () => this.load(),
      error: () => this.notify.error(this.translate.instant(WORKSPACE_MESSAGE_KEYS.saveFailed)),
    });
  }

  isMine(note: WorkspaceItem): boolean {
    return canEditItem(note, this.auth.currentUser?.id);
  }

  trackNote(_: number, note: WorkspaceItem): string {
    return note.id;
  }
}
