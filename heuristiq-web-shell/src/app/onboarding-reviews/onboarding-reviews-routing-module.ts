import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { OnboardingReviewsList } from './onboarding-reviews-list/onboarding-reviews-list';

const routes: Routes = [{ path: '', component: OnboardingReviewsList }];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class OnboardingReviewsRoutingModule {}
