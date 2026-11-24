import { ChangeDetectorRef, Component, ViewChild, inject } from '@angular/core';
import { NgModel } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateService } from '@ngx-translate/core';
import { HqApplicationsService } from 'hq-shared-lib';
import { NotificationService } from '../../shared/services/notification-service';
import { onboardingError } from '../franchise-onboarding/franchise-onboarding.helpers';
import { FranchiseApi } from './franchise-api';
import { isDriverApplication } from './franchise-application.helpers';
import { ADD_AGENT_DIALOG_TITLE_ID } from './franchise-members.constants';
import { AddFranchiseAgentRequest, FranchiseApplication } from './franchise.models';
import { addAgentError, emptyAgentRequest, placesLeft, toAgentRequest } from './franchises.helpers';

/** What the dialog is opened with: the franchise and its applications with their places, as the members screen read them. */
export interface FranchiseAddAgentDialogData {
  franchiseId: string;
  /** The franchise's code for the title (RBM-00001); empty while it is not known. */
  franchiseCode: string;
  applications: FranchiseApplication[];
}

/**
 * An administrator adds an agent to any franchise of the organization, from the franchise's members screen: the same fields and
 * checks as the owner's form on My franchises, sent through the administrator's call. Closes with true once the agent was added.
 */
@Component({
  selector: 'app-franchise-add-agent-dialog',
  standalone: false,
  templateUrl: './franchise-add-agent-dialog.html',
  styleUrl: './franchise-add-agent-dialog.scss',
})
export class FranchiseAddAgentDialog {
  readonly data = inject<FranchiseAddAgentDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<FranchiseAddAgentDialog, boolean>>(MatDialogRef);
  private readonly api = inject(FranchiseApi);
  private readonly notify = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly applicationNames = inject(HqApplicationsService);
  private readonly cdr = inject(ChangeDetectorRef);

  readonly titleId = ADD_AGENT_DIALOG_TITLE_ID;
  readonly placesLeft = placesLeft;
  /** Only applications with a place free can take an agent. */
  readonly openApplications = this.data.applications.filter((application) => !isDriverApplication(application.applicationCode) && placesLeft(application) > 0);

  agent: AddFranchiseAgentRequest = {
    ...emptyAgentRequest(),
    applicationCode: this.openApplications.length === 1 ? this.openApplications[0].applicationCode : '',
  };
  saving = false;

  /** The phone field, to know whether a typed number is valid (the field shows why when it is not). */
  @ViewChild('agentPhone') phoneField?: NgModel;

  appName(code: string): string {
    return this.applicationNames.nameOf(code);
  }

  submit(): void {
    this.add(this.phoneField?.invalid === true);
  }

  /** @param phoneInvalid the phone number was typed but is not a valid number (the phone field says so) */
  add(phoneInvalid = false): void {
    if (isDriverApplication(this.agent.applicationCode)) return;
    if (this.saving) {
      return;
    }
    if (phoneInvalid) {
      this.notify.error(this.translate.instant('accessManagement.franchises.errors.phone'));
      return;
    }
    const error = addAgentError(this.agent);
    if (error) {
      this.notify.error(this.translate.instant(error));
      return;
    }
    this.saving = true;
    this.api.adminAddAgent(this.data.franchiseId, toAgentRequest(this.agent)).subscribe({
      next: () => {
        this.saving = false;
        this.notify.success(this.translate.instant('accessManagement.franchises.messages.agentAdded'));
        this.ref.close(true);
      },
      error: (err: unknown) => {
        this.saving = false;
        // The service says why (no place left, the email belongs to someone else, ...); the form stays filled in.
        this.notify.error(onboardingError(err, this.translate, 'accessManagement.franchises.messages.agentFailed'));
        this.cdr.detectChanges();
      },
    });
  }

  cancel(): void {
    if (!this.saving) {
      this.ref.close(false);
    }
  }
}
