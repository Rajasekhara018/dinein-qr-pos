import { Component, OnInit, inject } from '@angular/core';
import { OrganizationChoice } from '../../shared/models/uam.models';
import { OrganizationContextService } from '../../shared/services/organization-context-service';

/**
 * Shows, and for an organization-wide user lets them choose, which organization's structure the screen shows and edits.
 * Picking another one moves the whole session into it, so the builder, the chart and the import always work on exactly
 * one organization. Everyone else sees their own organization and cannot change it.
 */
@Component({
  selector: 'app-org-selector',
  standalone: false,
  templateUrl: './org-selector.html',
  styleUrl: './org-selector.scss',
})
export class OrgSelector implements OnInit {
  readonly context = inject(OrganizationContextService);

  ngOnInit(): void {
    this.context.load().subscribe({ error: () => {} });
  }

  choose(list: OrganizationChoice[], tenantId: string, current: OrganizationChoice | null): void {
    if (!this.context.canSwitch) return;
    const next = list.find((organization) => organization.tenantId === tenantId);
    if (next && next.tenantId !== current?.tenantId) {
      this.context.switchTo(next);
    }
  }

  trackOrganization(_: number, organization: OrganizationChoice): string {
    return organization.tenantId;
  }
}
