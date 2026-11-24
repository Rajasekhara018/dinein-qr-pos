import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { HqDocument } from 'hq-shared-lib';
import { NotificationService } from '../../shared/services/notification-service';
import { TranslateService } from '@ngx-translate/core';

export interface DocRequirement {
  id: string;
  label: string;
}

const DOC_REQUIREMENT_IDS = ['gst_cert', 'pan_card', 'trade_licence', 'cancelled_cheque', 'fleet_agreement'] as const;

/**
 * Vendor business documents. Each document is uploaded straight to Rider_DMS by
 * <hq-doc-upload> as it's picked, filed under category `vendor-<docId>` with the vendor
 * application id as owner -- DMS is the record of what was submitted, so admin screens read
 * the same category/owner pairs back. (The old AdminAPI "attach" endpoint no longer exists.)
 */
@Component({
  selector: 'app-vendor-documents',
  templateUrl: './vendor-documents.html',
  styleUrl: './vendor-documents.scss',
  standalone: false,
})
export class VendorDocuments implements OnInit {
  docs: DocRequirement[] = [];
  vendorId: string | null = null;
  uploaded: Record<string, HqDocument | null> = {};
  isSuccess = false;
  attemptedSubmit = false;

  constructor(
    private router: Router,
    private notify: NotificationService,
    private translate: TranslateService,
  ) {}

  ngOnInit(): void {
    this.docs = DOC_REQUIREMENT_IDS.map((id) => ({
      id,
      label: this.translate.instant(`vendorPortal.documents.requirements.${id}`),
    }));
    const nav = this.router.getCurrentNavigation();
    const state = nav?.extras?.state as { vendor_id?: string } | undefined;
    this.vendorId = state?.['vendor_id'] ?? null;

    if (!this.vendorId) {
      // Try history state (for page reloads or direct navigation)
      const histState = history.state as { vendor_id?: string };
      this.vendorId = histState?.vendor_id ?? null;
    }

    if (!this.vendorId) {
      this.vendorId = sessionStorage.getItem('railcab_vendor_application_id');
    }

    if (!this.vendorId && !this.isSuccess) {
      this.router.navigate(['/vendor/register']);
    }
  }

  categoryFor(docId: string): string {
    return `vendor-${docId}`;
  }

  get uploadedCount(): number {
    return this.docs.filter((d) => !!this.uploaded[d.id]).length;
  }

  get progressPercent(): number {
    return (this.uploadedCount / this.docs.length) * 100;
  }

  get allUploaded(): boolean {
    return this.uploadedCount === this.docs.length;
  }

  get missingDocs(): DocRequirement[] {
    return this.docs.filter((d) => !this.uploaded[d.id]);
  }

  hasFile(docId: string): boolean {
    return !!this.uploaded[docId];
  }

  isMissing(docId: string): boolean {
    return this.attemptedSubmit && !this.uploaded[docId];
  }

  onChange(docId: string, doc: HqDocument | null): void {
    this.uploaded = { ...this.uploaded, [docId]: doc };
  }

  onRejected(message: string): void {
    this.notify.error(message);
  }

  onSubmit(): void {
    this.attemptedSubmit = true;
    if (!this.vendorId) {
      this.notify.error(this.translate.instant('vendorPortal.documents.messages.vendorIdMissing'));
      this.router.navigate(['/vendor/register']);
      return;
    }
    if (this.missingDocs.length > 0) {
      const labels = this.missingDocs.map((d) => d.label).join(', ');
      this.notify.error(this.translate.instant('vendorPortal.documents.messages.uploadAllRequired', { labels }));
      return;
    }
    this.notify.success(this.translate.instant('vendorPortal.documents.messages.uploadSuccess'));
    this.isSuccess = true;
    sessionStorage.removeItem('railcab_vendor_application_id');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
}
