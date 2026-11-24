import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared-module';
import { DesignSystemRoutingModule } from './design-system-routing-module';
import { DesignSystemPage } from './design-system-page';

/**
 * Dev-only showcase of every design token, type style and shared component, in both themes. Not reachable in
 * production (see `dev-only.guard.ts`) and lazy-loaded like every other feature module, so it never affects the
 * production initial bundle regardless.
 */
@NgModule({
  declarations: [DesignSystemPage],
  imports: [SharedModule, DesignSystemRoutingModule],
})
export class DesignSystemModule {}
