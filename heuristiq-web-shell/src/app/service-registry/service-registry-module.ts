import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../shared/shared-module';
import { ServiceRegistry } from './service-registry';

const routes: Routes = [{ path: '', component: ServiceRegistry }];

@NgModule({
  declarations: [ServiceRegistry],
  imports: [SharedModule, RouterModule.forChild(routes)],
})
export class ServiceRegistryModule {}
