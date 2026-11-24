import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { QuillModule } from 'ngx-quill';

import { NmsTemplatesRoutingModule } from './nms-templates-routing-module';
import { MaterialModule } from '../shared/material-module';
import { SharedModule } from '../shared/shared-module';

import { TemplateLanding } from './template-landing/template-landing';
import { TemplateList } from './template-list/template-list';
import { TemplateFormComponent } from './template-form/template-form';
import { TemplateEvents } from './template-events/template-events';

@NgModule({
  declarations: [
    TemplateLanding,
    TemplateList,
    TemplateFormComponent,
    TemplateEvents,
  ],
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    MaterialModule,
    SharedModule,
    QuillModule.forRoot(),
    NmsTemplatesRoutingModule,
  ],
})
export class NmsTemplatesModule {}
