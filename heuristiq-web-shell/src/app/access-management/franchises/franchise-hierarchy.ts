import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { NotificationService } from '../../shared/services/notification-service';
import { FranchiseApi } from './franchise-api';
import { FRANCHISE_HIERARCHY_PRIVILEGES, PARENT_ELIGIBLE_STATUS } from './franchise-hierarchy.constants';
import { FranchiseHierarchy } from './franchise-hierarchy.models';
import { Franchise } from './franchise.models';

/**
 * Where one franchise sits in the hierarchy (a state franchise over district franchises over city franchises): its parent, the
 * franchises directly under it and everything below. An administrator can put it under another active franchise, or back at the top.
 * The server refuses a loop, a parent that is not active, and a chain that is too deep.
 */
@Component({
  selector: 'app-franchise-hierarchy',
  standalone: false,
  templateUrl: './franchise-hierarchy.html',
  styleUrl: './franchises-list.scss',
})
export class FranchiseHierarchyPage implements OnInit {
  readonly privileges = FRANCHISE_HIERARCHY_PRIVILEGES;

  franchiseId = '';
  hierarchy: FranchiseHierarchy | null = null;
  franchises: Franchise[] = [];
  /** The parent chosen in the picker; empty means the top level. */
  parentId = '';
  isLoading = true;
  isSaving = false;
  errorMessage = '';

  constructor(
    private api: FranchiseApi,
    private notify: NotificationService,
    private translate: TranslateService,
    private route: ActivatedRoute,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.franchiseId = this.route.snapshot.paramMap.get('id') ?? '';
    this.api.list().subscribe({
      next: (franchises) => {
        this.franchises = franchises;
        this.load();
      },
      error: () => this.fail(),
    });
  }

  /** The code shown for a franchise id (the id itself when the franchise is not in the list). */
  codeOf(id: string): string {
    return this.franchises.find((franchise) => franchise.unitId === id)?.code ?? id;
  }

  /** The code of this franchise, for the page title. */
  get code(): string {
    return this.codeOf(this.franchiseId);
  }

  /** Franchises that may be chosen as parent: active ones, not this franchise and not one below it. */
  get parentOptions(): Franchise[] {
    const below = new Set(this.hierarchy?.descendantFranchiseIds ?? []);
    return this.franchises.filter(
      (franchise) => franchise.unitId !== this.franchiseId && !below.has(franchise.unitId) && franchise.status === PARENT_ELIGIBLE_STATUS,
    );
  }

  get changed(): boolean {
    return (this.hierarchy?.parentFranchiseId ?? '') !== this.parentId;
  }

  save(): void {
    if (this.isSaving || !this.changed) {
      return;
    }
    this.isSaving = true;
    this.api.setParent(this.franchiseId, this.parentId).subscribe({
      next: () => {
        this.isSaving = false;
        this.notify.success(this.translate.instant('franchiseHierarchy.saved'));
        this.load();
      },
      error: (err: { error?: { message?: string } }) => {
        this.isSaving = false;
        this.notify.error(err?.error?.message || this.translate.instant('franchiseHierarchy.saveFailed'));
        this.cdr.markForCheck();
      },
    });
  }

  private load(): void {
    this.isLoading = true;
    this.api.hierarchy(this.franchiseId).subscribe({
      next: (hierarchy) => {
        this.hierarchy = hierarchy;
        this.parentId = hierarchy.parentFranchiseId ?? '';
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: () => this.fail(),
    });
  }

  private fail(): void {
    this.isLoading = false;
    this.errorMessage = this.translate.instant('franchiseHierarchy.loadFailed');
    this.cdr.markForCheck();
  }
}
