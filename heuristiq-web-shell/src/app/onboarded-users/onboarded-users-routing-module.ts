import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { OnboardedUsersList } from './onboarded-users-list/onboarded-users-list';
import { OnboardedUserDetail } from './onboarded-user-detail/onboarded-user-detail';

const routes: Routes = [
  { path: '', component: OnboardedUsersList },
  { path: ':id', component: OnboardedUserDetail },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class OnboardedUsersRoutingModule {}
