import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ApiDocs } from './api-docs';
import { Javadoc } from './javadoc/javadoc';
import { JavadocViewer } from './javadoc/javadoc-viewer/javadoc-viewer';

const routes: Routes = [
  { path: '', component: ApiDocs },
  { path: 'java-doc', component: Javadoc },
  { path: 'java-doc/:service', component: JavadocViewer },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ApiDocsRoutingModule {}
