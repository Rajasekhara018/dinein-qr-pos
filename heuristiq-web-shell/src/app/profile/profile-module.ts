import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared-module';
import { ProfileRoutingModule } from './profile-routing-module';
import { Profile } from './profile';
import { TimezonePicker } from './timezone-picker/timezone-picker';

@NgModule({
  declarations: [Profile, TimezonePicker],
  imports: [SharedModule, ProfileRoutingModule],
})
export class ProfileModule {}
