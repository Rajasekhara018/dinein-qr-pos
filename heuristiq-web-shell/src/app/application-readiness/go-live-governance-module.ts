import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared-module';
import { GoLiveBlockedDialog } from './go-live-blocked-dialog/go-live-blocked-dialog';
import { GoLiveGovernancePage } from './go-live-governance-page/go-live-governance-page';
import { GoLiveSteps } from './go-live-steps/go-live-steps';
import { GovernancePanel } from './governance-panel/governance-panel';

/** The go-live explanation: the dialog of a refused go-live (with the SUPER_ADMIN override) the governance panel, the list of go-live steps and their page. */
@NgModule({
  declarations: [GoLiveBlockedDialog, GovernancePanel, GoLiveGovernancePage, GoLiveSteps],
  imports: [SharedModule],
  exports: [GoLiveBlockedDialog, GovernancePanel, GoLiveGovernancePage, GoLiveSteps],
})
export class GoLiveGovernanceModule {}
