import { NgModule, Optional, SkipSelf } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastHost } from './ui/toast-host';

/**
 * App-wide singletons with UI (toast host). Imported ONCE by AppModule. Stateful services are
 * `providedIn: 'root'` and need no module.
 */
@NgModule({
  declarations: [ToastHost],
  imports: [CommonModule],
  exports: [ToastHost],
})
export class CoreModule {
  constructor(@Optional() @SkipSelf() parent: CoreModule | null) {
    if (parent) {
      throw new Error('CoreModule is already loaded. Import it in AppModule only.');
    }
  }
}
