import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared-module';
import { BoardColumn } from './board/board-column';
import { BoardHeader } from './board/board-header';
import { KitchenBoard } from './board/kitchen-board';
import { TicketCard } from './board/ticket-card';
import { KitchenRoutingModule } from './kitchen-routing-module';
import { KitchenLogin } from './login/kitchen-login';
import { KitchenShell } from './shell/kitchen-shell';

/** Kitchen display app (`/kitchen`): device sign-in and the live order board. */
@NgModule({
  declarations: [KitchenShell, KitchenLogin, KitchenBoard, BoardHeader, BoardColumn, TicketCard],
  imports: [SharedModule, KitchenRoutingModule],
})
export class KitchenModule {}
