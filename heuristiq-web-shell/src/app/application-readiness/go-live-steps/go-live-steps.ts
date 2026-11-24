import { ChangeDetectorRef, Component, Input, OnChanges, OnDestroy, SimpleChanges, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqPrivilegeAccessService } from 'hq-shared-lib';
import { Subscription } from 'rxjs';
import { GOVERNANCE_PRIVILEGES, GOVERNANCE_TEXT_KEYS } from '../go-live-governance.constants';
import { GoLiveGovernanceService } from '../go-live-governance.service';
import { toGoLiveSteps } from '../go-live-steps.helpers';
import { GoLiveStepView } from '../go-live-steps.models';
import { ReadinessNavigationService } from '../readiness-navigation.service';

/** What the list shows. */
export type GoLiveStepsState = 'off' | 'loading' | 'ready' | 'empty' | 'unavailable';

/**
 * The steps one application needs before go-live, as a plain list: done / not done / could not be checked, the sentence of
 * the step and its Fix link. Nothing is shown, and nothing is requested, without APPLICATION_READINESS_READ.
 */
@Component({
  selector: 'app-go-live-steps',
  standalone: false,
  templateUrl: './go-live-steps.html',
  styleUrl: './go-live-steps.scss',
})
export class GoLiveSteps implements OnChanges, OnDestroy {
  private readonly governance = inject(GoLiveGovernanceService);
  private readonly access = inject(HqPrivilegeAccessService);
  private readonly navigation = inject(ReadinessNavigationService);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);
  private subscription: Subscription | null = null;

  /** Catalogue code of the application. */
  @Input({ required: true }) code = '';

  readonly texts = GOVERNANCE_TEXT_KEYS;
  state: GoLiveStepsState = 'off';
  steps: GoLiveStepView[] = [];

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['code']) this.load();
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  load(): void {
    this.subscription?.unsubscribe();
    if (!this.code || !this.access.can(GOVERNANCE_PRIVILEGES.steps)) {
      this.state = 'off';
      return;
    }
    this.state = 'loading';
    this.subscription = this.governance.steps(this.code).subscribe((result) => {
      if (result.state === 'ready' && result.data) {
        const has = (key: string): boolean => this.translate.instant(key) !== key;
        this.steps = toGoLiveSteps(result.data.steps, has, (path) => this.navigation.canOpen(path));
        this.state = this.steps.length > 0 ? 'ready' : 'empty';
      } else {
        this.steps = [];
        this.state = result.state === 'forbidden' ? 'off' : 'unavailable';
      }
      this.cdr.markForCheck();
    });
  }

  trackStep(_: number, step: GoLiveStepView): string {
    return step.id;
  }

  markKey(step: GoLiveStepView): string {
    return step.mark === 'done' ? this.texts.stepDone : step.mark === 'unknown' ? this.texts.stepUnknown : this.texts.stepNotDone;
  }

  markIcon(step: GoLiveStepView): string {
    return step.mark === 'done' ? 'bi-check-circle-fill' : step.mark === 'unknown' ? 'bi-question-circle' : 'bi-x-circle';
  }
}
