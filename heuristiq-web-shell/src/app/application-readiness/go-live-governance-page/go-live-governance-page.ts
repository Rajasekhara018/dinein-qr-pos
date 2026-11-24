import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { APPLICATIONS_LIST_ROUTE, GOVERNANCE_TEXT_KEYS } from '../go-live-governance.constants';

/**
 * Access management > Applications > Go-live governance: the steps one application needs before go-live, its last go-live
 * override and the alert times. Each part checks its own privilege and shows nothing without it.
 */
@Component({
  selector: 'app-go-live-governance-page',
  standalone: false,
  templateUrl: './go-live-governance-page.html',
})
export class GoLiveGovernancePage implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private subscription: Subscription | null = null;

  readonly texts = GOVERNANCE_TEXT_KEYS;
  code = '';

  ngOnInit(): void {
    this.subscription = this.route.paramMap.subscribe((params) => {
      this.code = params.get('code') ?? '';
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  back(): void {
    void this.router.navigateByUrl(APPLICATIONS_LIST_ROUTE);
  }
}
