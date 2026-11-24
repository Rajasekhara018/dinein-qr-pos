import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { pendingChangesGuard } from '../shared/guards/pending-changes.guard';
import { OnboardingForms } from './onboarding-forms';

const routes: Routes = [
  { path: '', component: OnboardingForms, canDeactivate: [pendingChangesGuard] },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class OnboardingFormsRoutingModule {}
