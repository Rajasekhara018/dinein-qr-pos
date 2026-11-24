import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared-module';
import { HomeRoutingModule } from './home-routing-module';
import { Home } from './home';

@NgModule({
  declarations: [Home],
  imports: [SharedModule, HomeRoutingModule],
})
export class HomeModule {}
