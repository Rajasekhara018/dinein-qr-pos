import { ChangeDetectorRef, Component, Input, OnChanges, OnDestroy, SimpleChanges, inject } from '@angular/core';
import { HqPrivilegeAccessService } from 'hq-shared-lib';
import { Subscription } from 'rxjs';
import { GOVERNANCE_PRIVILEGES, GOVERNANCE_TEXT_KEYS } from '../go-live-governance.constants';
import { parseTimestamp } from '../go-live-governance.helpers';
import { GovernanceState } from '../go-live-governance.models';
import { GoLiveGovernanceService } from '../go-live-governance.service';

/** What the panel shows. */
export type GovernancePanelState = 'off' | 'loading' | 'ready' | 'unavailable';

/**
 * Go-live governance of one application: the last go-live override (who, when, why, which steps) and the last alert times.
 * Everything is hidden, and no request is made, without APPLICATION_READINESS_GOVERNANCE_READ.
 */
@Component({
  selector: 'app-governance-panel',
  standalone: false,
  templateUrl: './governance-panel.html',
  styleUrl: './governance-panel.scss',
})
export class GovernancePanel implements OnChanges, OnDestroy {
  private readonly governance = inject(GoLiveGovernanceService);
  private readonly access = inject(HqPrivilegeAccessService);
  private readonly cdr = inject(ChangeDetectorRef);
  private subscription: Subscription | null = null;

  /** Catalogue code of the application. */
  @Input({ required: true }) code = '';
  /** Changes when the screen should read again. */
  @Input() refreshKey: unknown = null;

  readonly texts = GOVERNANCE_TEXT_KEYS;

  state: GovernancePanelState = 'off';
  data: GovernanceState | null = null;

  get canRead(): boolean {
    return this.access.can(GOVERNANCE_PRIVILEGES.read);
  }

  get overriddenAt(): Date | null {
    return parseTimestamp(this.data?.override?.at);
  }

  get lastBlockedAt(): Date | null {
    return parseTimestamp(this.data?.alerts?.lastBlockedAt);
  }

  get lastRefusedAt(): Date | null {
    return parseTimestamp(this.data?.alerts?.lastRefusedAt);
  }

  get overriddenBy(): string {
    return this.data?.override?.by ?? '';
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['code'] || changes['refreshKey']) this.load();
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  load(): void {
    this.subscription?.unsubscribe();
    if (!this.code || !this.canRead) {
      this.state = 'off';
      return;
    }
    this.state = 'loading';
    this.subscription = this.governance.state(this.code).subscribe((result) => {
      if (result.state === 'ready' && result.data) {
        this.data = result.data;
        this.state = 'ready';
      } else {
        this.state = result.state === 'forbidden' ? 'off' : 'unavailable';
      }
      this.cdr.markForCheck();
    });
  }
}
