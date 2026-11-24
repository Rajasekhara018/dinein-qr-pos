import { OrganizationContextService } from '../../shared/services/organization-context-service';
import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqCommonTableColumn } from 'hq-shared-lib';
import { ORG_PAGE_SIZE } from '../org-structure.constants';
import { OrgPerson, OrgUnit } from '../org-structure.models';
import { OrgStructureService } from '../org-structure.service';
import { NotificationService } from '../../shared/services/notification-service';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';

/** A person as the table shows them: the head of the selected department is marked. */
export interface OrgPersonRow extends OrgPerson {
  role: string;
}

/** A unit in the explorer; children are fetched the first time it is expanded. */
export interface OrgTreeNode {
  unit: OrgUnit;
  depth: number;
  expanded: boolean;
  loading: boolean;
  children: OrgTreeNode[] | null;
}

/**
 * The live organization: an expandable department tree on the left and, for the selected unit,
 * everyone in it and below it on the right. Both load on demand so a 100k-person organization never
 * has to be fetched at once.
 */
@Component({
  selector: 'app-org-chart',
  standalone: false,
  templateUrl: './org-chart.html',
  styleUrl: './org-chart.scss',
})
export class OrgChart implements OnInit {
  /** The organization whose structure this screen shows (the session's). */
  readonly organization = inject(OrganizationContextService);

  roots: OrgTreeNode[] = [];
  /** The tree flattened in display order, rebuilt whenever a node expands or collapses. */
  visible: OrgTreeNode[] = [];
  isLoading = false;
  hasError = false;

  selected: OrgTreeNode | null = null;
  people: OrgPerson[] = [];
  /** The loaded people with their role in the department (the head first), as the table shows them. */
  peopleRows: OrgPersonRow[] = [];
  peopleLoading = false;
  hasMorePeople = false;
  peopleColumns: HqCommonTableColumn<OrgPerson>[] = [];

  constructor(
    private api: OrgStructureService,
    private cdr: ChangeDetectorRef,
    private notify: NotificationService,
    private translate: TranslateService,
  ) {}

  ngOnInit(): void {
    const t = (key: string) => this.translate.instant(key);
    this.peopleColumns = [
      { key: 'employeeCode', label: t('orgStructure.chart.columns.code') },
      { key: 'fullName', label: t('orgStructure.chart.columns.name') },
      { key: 'role', label: t('orgStructure.chart.columns.role') },
      { key: 'positionName', label: t('orgStructure.chart.columns.position') },
      { key: 'email', label: t('orgStructure.chart.columns.email') },
    ];
    this.isLoading = true;
    this.api.children().subscribe({
      next: (units) => {
        this.roots = units.map((unit) => this.toNode(unit, 0));
        this.rebuildVisible();
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.hasError = true;
        this.isLoading = false;
        this.cdr.detectChanges();
      },
    });
  }

  toggle(node: OrgTreeNode, event?: Event): void {
    event?.stopPropagation();
    if (node.expanded) {
      node.expanded = false;
      this.rebuildVisible();
      return;
    }
    if (node.children) {
      node.expanded = true;
      this.rebuildVisible();
      return;
    }
    node.loading = true;
    this.api.children(node.unit.id).subscribe({
      next: (units) => {
        node.children = units.map((unit) => this.toNode(unit, node.depth + 1));
        node.expanded = true;
        node.loading = false;
        this.rebuildVisible();
        this.cdr.detectChanges();
      },
      error: (err) => {
        node.loading = false;
        this.notify.error(apiErrorMessage(err, this.translate.instant('orgStructure.chart.loadFailed')));
        this.cdr.detectChanges();
      },
    });
  }

  select(node: OrgTreeNode): void {
    this.selected = node;
    this.people = [];
    this.peopleRows = [];
    this.hasMorePeople = false;
    this.loadPeople();
  }

  loadPeople(): void {
    if (!this.selected) return;
    const last = this.people.length ? this.people[this.people.length - 1].employeeId : null;
    this.peopleLoading = true;
    this.api.people(this.selected.unit.id, last, ORG_PAGE_SIZE).subscribe({
      next: (page) => {
        this.people = [...this.people, ...page];
        this.rebuildPeopleRows();
        this.hasMorePeople = page.length === ORG_PAGE_SIZE;
        this.peopleLoading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.peopleLoading = false;
        this.notify.error(apiErrorMessage(err, this.translate.instant('orgStructure.chart.loadFailed')));
        this.cdr.detectChanges();
      },
    });
  }

  /** Marks the head of the selected department and lists them first among the people loaded so far. */
  private rebuildPeopleRows(): void {
    const headId = this.selected?.unit.headEmployeeId ?? null;
    const headRole = this.translate.instant('orgStructure.chart.headBadge');
    const rows = this.people.map((person) => ({ ...person, role: headId !== null && person.employeeId === headId ? headRole : '' }));
    this.peopleRows = [...rows.filter((row) => row.role), ...rows.filter((row) => !row.role)];
  }

  trackNode(_: number, node: OrgTreeNode): string {
    return node.unit.id;
  }

  private toNode(unit: OrgUnit, depth: number): OrgTreeNode {
    return { unit, depth, expanded: false, loading: false, children: null };
  }

  private rebuildVisible(): void {
    const out: OrgTreeNode[] = [];
    const walk = (nodes: OrgTreeNode[]) => {
      for (const node of nodes) {
        out.push(node);
        if (node.expanded && node.children) walk(node.children);
      }
    };
    walk(this.roots);
    this.visible = out;
  }
}
