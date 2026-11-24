import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared-module';
import { WorkflowHistoryModule } from '../workflow/history/workflow-history.module';
import { OnboardingReviewsRoutingModule } from './onboarding-reviews-routing-module';
import { OnboardingReviewsList } from './onboarding-reviews-list/onboarding-reviews-list';

@NgModule({
  declarations: [OnboardingReviewsList],
  imports: [SharedModule, WorkflowHistoryModule, OnboardingReviewsRoutingModule],
})
export class OnboardingReviewsModule {}
