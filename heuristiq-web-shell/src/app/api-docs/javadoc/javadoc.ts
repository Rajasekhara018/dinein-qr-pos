import { Component, inject } from '@angular/core';
import { BrandingService } from '../../shared/services/branding-service';
import {
  JAVADOC_GROUPS,
  JAVADOC_GROUP_LABEL_KEYS,
  JAVADOC_SERVICES,
  JavadocGroup,
  JavadocService,
} from './javadoc.constants';

/** Java Docs landing screen: one card per service; a card opens that service's viewer route. */
@Component({
  selector: 'app-javadoc',
  standalone: false,
  templateUrl: './javadoc.html',
  styleUrl: './javadoc.scss',
})
export class Javadoc {
  /** Per-deployment branding (app name, logo, tagline), loaded at runtime by BrandingService. */
  readonly branding = inject(BrandingService).branding;

  readonly groups = JAVADOC_GROUPS.map((group) => ({
    group,
    labelKey: JAVADOC_GROUP_LABEL_KEYS[group],
    services: JAVADOC_SERVICES.filter((s) => s.group === group),
  }));

  trackByService(_: number, service: JavadocService): string {
    return service.id;
  }

  trackByGroup(_: number, entry: { group: JavadocGroup }): JavadocGroup {
    return entry.group;
  }
}
