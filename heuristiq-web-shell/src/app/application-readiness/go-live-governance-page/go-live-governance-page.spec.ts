import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject } from 'rxjs';
import { vi } from 'vitest';
import { GoLiveGovernancePage } from './go-live-governance-page';

describe('GoLiveGovernancePage', () => {
  const params$ = new BehaviorSubject(convertToParamMap({ code: 'FOOD_DELIVER_APP' }));
  const router = { navigateByUrl: vi.fn().mockResolvedValue(true) };
  let fixture: ComponentFixture<GoLiveGovernancePage>;

  beforeEach(() => {
    router.navigateByUrl.mockClear();
    TestBed.configureTestingModule({
      declarations: [GoLiveGovernancePage],
      imports: [TranslateModule.forRoot()],
      providers: [
        { provide: ActivatedRoute, useValue: { paramMap: params$ } },
        { provide: Router, useValue: router },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    });
    fixture = TestBed.createComponent(GoLiveGovernancePage);
    fixture.detectChanges();
  });

  it('shows the go-live steps and the governance panel for the application of the route, and nothing about access', () => {
    expect(fixture.componentInstance.code).toBe('FOOD_DELIVER_APP');
    expect(fixture.nativeElement.querySelector('app-go-live-steps')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('app-governance-panel')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('app-application-access-section')).toBeNull();
  });

  it('follows the route when the code changes', () => {
    params$.next(convertToParamMap({ code: 'BEST_PURCHASE_APP' }));
    fixture.componentRef.changeDetectorRef.markForCheck();
    fixture.detectChanges();

    expect(fixture.componentInstance.code).toBe('BEST_PURCHASE_APP');
  });

  it('goes back to the applications list', () => {
    fixture.componentInstance.back();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/access-management/applications');
  });

  it('stops listening to the route when it is destroyed', () => {
    fixture.destroy();
    params$.next(convertToParamMap({ code: 'LATE' }));

    expect(fixture.componentInstance.code).not.toBe('LATE');
  });
});
