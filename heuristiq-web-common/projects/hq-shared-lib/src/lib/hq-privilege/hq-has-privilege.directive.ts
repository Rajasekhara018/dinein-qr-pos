import { Directive, Input, OnDestroy, OnInit, TemplateRef, ViewContainerRef } from '@angular/core';
import { Subscription } from 'rxjs';
import { HqPrivilegeAccessService } from './hq-privilege-access.service';

/**
 * Shows its element only when the user holds a privilege for the action. Give one privilege code or several
 * (any one is enough), using the names in privilege.json:
 *
 *   <button *hqHasPrivilege="'ADMIN_RIDE_CANCEL'">Cancel ride</button>
 *   <th *hqHasPrivilege="['ADMIN_PAYOUT_LIST', 'ADMIN_PAYOUT_READ']">Payout</th>
 *
 * Re-checks when the user's privileges change. A UI hint only: the gateway checks every API call.
 */
@Directive({ selector: '[hqHasPrivilege]', standalone: false })
export class HqHasPrivilege implements OnInit, OnDestroy {
  private codes: string | readonly string[] = [];
  private shown = false;
  private subscription?: Subscription;

  constructor(
    private readonly template: TemplateRef<unknown>,
    private readonly container: ViewContainerRef,
    private readonly access: HqPrivilegeAccessService,
  ) {}

  @Input()
  set hqHasPrivilege(codes: string | readonly string[]) {
    this.codes = codes;
    this.render();
  }

  ngOnInit(): void {
    this.subscription = this.access.changes$.subscribe(() => this.render());
    this.render();
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  private render(): void {
    const allowed = this.access.can(this.codes);
    if (allowed && !this.shown) {
      this.container.createEmbeddedView(this.template);
      this.shown = true;
    } else if (!allowed && this.shown) {
      this.container.clear();
      this.shown = false;
    }
  }
}
