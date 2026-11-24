import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router } from '@angular/router';
import { HqPrivilegeAccessService } from 'hq-shared-lib';
import { Subscription } from 'rxjs';

import { LIFECYCLE_EVENTS_PAGE_SIZE, LIFECYCLE_PREVIEW_DIALOG_WIDTH, LIFECYCLE_ROUTE } from '../lifecycle.constants';
import { LifecycleFilesService } from '../lifecycle-files.service';
import { DocumentVersion, PreviewKind, asDocument, previewKind, versionsOf } from '../lifecycle-versions.helpers';
import { LifecyclePreviewData, LifecyclePreviewDialog } from '../lifecycle-preview-dialog/lifecycle-preview-dialog';
import { hasMoreEvents } from '../lifecycle.helpers';
import { LifecycleDocumentDetail, LifecycleEvent, LifecyclePage } from '../lifecycle.models';
import { LifecycleAuditService } from '../lifecycle-audit.service';
import { LifecycleService } from '../lifecycle.service';
import { LifecycleViewBase } from '../lifecycle-view.base';

/** One line of the facts card. `value` is already formatted; empty values are shown as a dash. */
export interface FactRow {
  labelKey: string;
  value: string;
  mono?: boolean;
  copyable?: boolean;
}

export interface FactGroup {
  titleKey: string;
  icon: string;
  rows: FactRow[];
}

@Component({
  selector: 'app-lifecycle-detail',
  templateUrl: './lifecycle-detail.html',
  styleUrl: './lifecycle-detail.scss',
  standalone: false,
  providers: [LifecycleFilesService],
})
export class LifecycleDetail extends LifecycleViewBase implements OnInit, OnDestroy {
  private readonly svc = inject(LifecycleService);
  private readonly audit = inject(LifecycleAuditService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly access = inject(HqPrivilegeAccessService);
  private readonly files = inject(LifecycleFilesService);
  private readonly dialog = inject(MatDialog);

  /** The document's versions (every upload of the same owner and category), newest first; the page's own one is marked. */
  versions: DocumentVersion[] = [];
  versionsLoading = false;
  /** Thumbnails read so far, by document id. */
  thumbs: Record<string, string> = {};

  documentId = '';
  detail: LifecycleDocumentDetail | null = null;
  groups: FactGroup[] = [];
  loading = false;
  loadFailed = false;

  events: LifecycleEvent[] = [];
  eventsPage: LifecyclePage<LifecycleEvent> | null = null;
  eventsLoading = false;
  eventsFailed = false;
  /** The audit trail answered 403: shown as a note, not as an error. */
  eventsForbidden = false;

  private subs = new Subscription();
  private eventsSub: Subscription | null = null;

  ngOnInit(): void {
    this.documentId = this.route.snapshot.paramMap.get('id') ?? '';
    this.load();
    // Dates and labels follow the language switcher.
    this.subs.add(this.translate.onLangChange.subscribe(() => this.buildGroups()));
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
    this.eventsSub?.unsubscribe();
  }

  /** The history lives in the central audit trail and needs its read privilege. */
  get canReadAudit(): boolean {
    return this.access.can(this.privileges.audit);
  }

  get moreEvents(): boolean {
    return hasMoreEvents(this.eventsPage);
  }

  load(): void {
    this.loading = true;
    this.loadFailed = false;
    this.subs.add(
      this.svc.getDocument(this.documentId).subscribe({
        next: (d) => {
          this.detail = d;
          this.buildGroups();
          this.loading = false;
          if (this.canReadAudit) this.loadEvents(true);
          this.loadVersions(d);
        },
        error: () => {
          this.loading = false;
          this.loadFailed = true;
          this.notify.error(this.translate.instant('documentManagement.lifecycle.detail.loadFailed'));
        },
      }),
    );
  }

  /** Loads the first page, or appends the next one ("load more"). */
  loadEvents(first: boolean): void {
    this.eventsSub?.unsubscribe();
    const page = first || !this.eventsPage ? 0 : this.eventsPage.page + 1;
    this.eventsLoading = true;
    this.eventsFailed = false;
    this.eventsForbidden = false;
    this.eventsSub = this.audit.documentEvents(this.documentId, page, LIFECYCLE_EVENTS_PAGE_SIZE).subscribe({
      next: (res) => {
        this.events = first ? res.content : [...this.events, ...res.content];
        this.eventsPage = res;
        this.eventsLoading = false;
      },
      error: (err: unknown) => {
        this.eventsLoading = false;
        if (this.audit.classifyError(err) === 'forbidden') this.eventsForbidden = true;
        else this.eventsFailed = true;
      },
    });
  }

  /** Opening a file needs a download link; without the privilege the preview card is not shown. */
  get canPreview(): boolean {
    return this.access.can(this.privileges.download);
  }

  kindOf(version: DocumentVersion): PreviewKind {
    return previewKind(version.doc.contentType);
  }

  /** Shows the versions in the preview dialog, starting with the chosen one. */
  openPreview(index: number): void {
    if (!this.canPreview || !this.versions.length) return;
    this.dialog.open<LifecyclePreviewDialog, LifecyclePreviewData>(LifecyclePreviewDialog, {
      data: { versions: this.versions, index, files: this.files },
      width: LIFECYCLE_PREVIEW_DIALOG_WIDTH,
      maxWidth: '96vw',
      autoFocus: false,
      panelClass: 'app-dialog-panel',
    });
  }

  /** The page's document first; the other uploads of its owner and category when the person may list them. */
  private loadVersions(detail: LifecycleDocumentDetail): void {
    if (!this.canPreview) return;
    this.versions = detail.deleted ? [] : [{ doc: asDocument(detail), current: true }];
    this.readThumbs();
    if (!this.access.can(this.privileges.listVersions) || !detail.category || !detail.ownerId) return;
    this.versionsLoading = true;
    this.subs.add(this.files.versions(detail.category, detail.ownerId).subscribe({
      next: (uploads) => {
        this.versions = versionsOf(detail, uploads).filter((v) => !(v.current && detail.deleted));
        this.versionsLoading = false;
        this.readThumbs();
      },
      error: () => { this.versionsLoading = false; },
    }));
  }

  private readThumbs(): void {
    for (const version of this.versions) {
      if (this.kindOf(version) !== 'image' || this.thumbs[version.doc.id]) continue;
      this.subs.add(this.files.url(version.doc).subscribe({
        next: (url) => { this.thumbs = { ...this.thumbs, [version.doc.id]: url }; },
        error: () => undefined,
      }));
    }
  }

  back(): void {
    this.router.navigate([LIFECYCLE_ROUTE]);
  }

  private yesNo(value: boolean | null | undefined): string {
    return this.translate.instant(value ? 'documentManagement.lifecycle.yes' : 'documentManagement.lifecycle.no');
  }

  private who(id: string | null | undefined, at: string | null | undefined): string {
    const when = this.absolute(at);
    return [id, when].filter(Boolean).join(' · ');
  }

  private buildGroups(): void {
    const d = this.detail;
    if (!d) {
      this.groups = [];
      return;
    }
    const p = 'documentManagement.lifecycle.facts.';
    this.groups = [
      {
        titleKey: p + 'file',
        icon: 'bi-file-earmark',
        rows: [
          { labelKey: p + 'originalFileName', value: d.originalFileName ?? '' },
          { labelKey: p + 'documentType', value: d.documentType ?? '' },
          { labelKey: p + 'category', value: d.category ?? '' },
          { labelKey: p + 'owner', value: d.ownerId ?? '', mono: true, copyable: true },
          { labelKey: p + 'contentType', value: d.contentType ?? '' },
          { labelKey: p + 'size', value: d.sizeBytes == null ? '' : this.size(d.sizeBytes) },
          { labelKey: p + 'status', value: this.statusLabel(d.status) },
          { labelKey: p + 'uploaded', value: this.who(d.uploadedBy, d.createdAt) },
          { labelKey: p + 'tenant', value: d.tenantId ?? '', mono: true },
        ],
      },
      {
        titleKey: p + 'storage',
        icon: 'bi-hdd-network',
        rows: [
          { labelKey: p + 'provider', value: d.provider ?? '' },
          { labelKey: p + 'storedFileName', value: d.storedFileName ?? '', mono: true },
          { labelKey: p + 'objectKey', value: d.objectKey ?? '', mono: true, copyable: true },
          { labelKey: p + 'versionId', value: d.versionId ?? '', mono: true, copyable: true },
          { labelKey: p + 'checksum', value: d.checksumSha256 ?? '', mono: true, copyable: true },
          { labelKey: p + 'encryption', value: d.encryption ?? '' },
        ],
      },
      {
        titleKey: p + 'scan',
        icon: 'bi-shield-check',
        rows: [
          { labelKey: p + 'scanStatus', value: this.statusLabel(d.scanStatus) },
          { labelKey: p + 'scanMessage', value: d.scanMessage ?? '' },
        ],
      },
      {
        titleKey: p + 'retention',
        icon: 'bi-hourglass-split',
        rows: [
          { labelKey: p + 'retentionUntil', value: this.day(d.retentionUntil) },
          { labelKey: p + 'legalHold', value: this.yesNo(d.legalHold) },
          { labelKey: p + 'legalHoldChanged', value: this.who(d.legalHoldChangedBy, d.legalHoldChangedAt) },
        ],
      },
      {
        titleKey: p + 'deletion',
        icon: 'bi-trash3',
        rows: [
          { labelKey: p + 'deleted', value: this.yesNo(d.deleted) },
          { labelKey: p + 'deletedAt', value: this.who(d.deletedBy, d.deletedAt) },
        ],
      },
    ];
  }
}
