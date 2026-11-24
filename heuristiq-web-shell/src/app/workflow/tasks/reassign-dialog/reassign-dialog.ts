import { ChangeDetectorRef, Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { UamService } from '../../../shared/services/uam-service';
import { UamUser } from '../../../shared/models/uam.models';
import { WorkflowTask } from '../../workflow.models';

export interface ReassignDialogData {
  task: WorkflowTask;
}

interface UserOption {
  id: string;
  label: string;
}

/** Picks the UAM user a task is handed to. Closes with their user id, or null. */
@Component({
  selector: 'app-reassign-dialog',
  standalone: false,
  templateUrl: './reassign-dialog.html',
  styleUrl: './reassign-dialog.scss',
})
export class ReassignDialog implements OnInit {
  users: UserOption[] = [];
  filter = '';
  selected = '';
  loadFailed = false;

  constructor(
    private ref: MatDialogRef<ReassignDialog, string | null>,
    @Inject(MAT_DIALOG_DATA) public data: ReassignDialogData,
    private uam: UamService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.uam.listUsers(0, 200).subscribe({
      next: (page) => {
        this.users = page.content
          .filter((u: UamUser) => u.id !== this.data.task.assignee)
          .map((u: UamUser) => ({ id: u.id, label: u.displayName || u.userName + (u.email ? ` (${u.email})` : '') }));
        this.cdr.detectChanges();
      },
      error: () => { this.loadFailed = true; this.cdr.detectChanges(); },
    });
  }

  get visible(): UserOption[] {
    const q = this.filter.trim().toLowerCase();
    return q ? this.users.filter((u) => u.label.toLowerCase().includes(q)) : this.users;
  }

  submit(): void {
    if (this.selected) this.ref.close(this.selected);
  }

  cancel(): void {
    this.ref.close(null);
  }
}
