import { HqChartsModule } from 'hq-shared-lib';
import { NgModule } from '@angular/core';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { SharedModule } from '../shared/shared-module';
import { OnboardingFormsRoutingModule } from './onboarding-forms-routing-module';
import { OnboardingForms } from './onboarding-forms';
import { FieldDialog } from './field-dialog/field-dialog';
import { PreviewDialog } from './preview-dialog/preview-dialog';

@NgModule({
  declarations: [OnboardingForms, FieldDialog, PreviewDialog],
  imports: [SharedModule, HqChartsModule, DragDropModule, OnboardingFormsRoutingModule],
})
export class OnboardingFormsModule {}
