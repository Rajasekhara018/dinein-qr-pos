import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { Profile } from './profile';
import { pendingChangesGuard } from '../shared/guards/pending-changes.guard';

const routes: Routes = [
  { path: '', component: Profile, canDeactivate: [pendingChangesGuard] },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ProfileRoutingModule {}
