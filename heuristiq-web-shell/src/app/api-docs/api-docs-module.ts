import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared-module';
import { ApiDocsRoutingModule } from './api-docs-routing-module';
import { ApiDocs } from './api-docs';
import { Javadoc } from './javadoc/javadoc';
import { JavadocViewer } from './javadoc/javadoc-viewer/javadoc-viewer';

@NgModule({
  declarations: [ApiDocs, Javadoc, JavadocViewer],
  imports: [SharedModule, ApiDocsRoutingModule],
})
export class ApiDocsModule {}
