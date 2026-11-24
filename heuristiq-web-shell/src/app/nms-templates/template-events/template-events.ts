import { ChangeDetectorRef, Component, HostListener, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable } from 'rxjs';

import { NmsTemplatesService } from '../nms-templates.service';
import { NmsEventDef } from '../event-attributes.config';
import {
  EventSelection, duplicateEventValues, eventsSignature, indexOfSelection, normalizeEvent, normalizeEventAttribute, normalizeEventValue,
} from '../event-definitions.helpers';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { PendingChangesAware } from '../../shared/guards/pending-changes.guard';
import { TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-template-events',
  templateUrl: './template-events.html',
  styleUrls: ['./template-events.scss'],
  standalone: false,
})
export class TemplateEvents implements OnInit, PendingChangesAware {
  events: NmsEventDef[] = [];
  selectedIndex = 0;
  attrDraft = '';
  loading = false;
  saving = false;
  private savedSnapshot = '[]';
  /** The events as last loaded or saved: a save sends only what differs from this. */
  private baseline: NmsEventDef[] = [];

  constructor(
    private router: Router,
    private svc: NmsTemplatesService,
    private snack: MatSnackBar,
    private appDialog: AppDialogService,
    private cdr: ChangeDetectorRef,
    private translate: TranslateService,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  get selected(): NmsEventDef | null {
    return this.events[this.selectedIndex] ?? null;
  }

  get hasChanges(): boolean {
    return !this.loading && this.currentSnapshot() !== this.savedSnapshot;
  }

  /** Values used by more than one event; each of those events is flagged and saving is blocked until they differ. */
  get duplicateValues(): ReadonlySet<string> {
    return new Set(duplicateEventValues(this.events));
  }

  get hasDuplicate(): boolean {
    return this.duplicateValues.size > 0;
  }

  isDuplicate(event: NmsEventDef): boolean {
    return this.duplicateValues.has(normalizeEventValue(event.value || event.label || ''));
  }

  @HostListener('window:beforeunload', ['$event'])
  beforeUnload(event: BeforeUnloadEvent): void {
    if (!this.hasChanges) return;
    event.preventDefault();
    event.returnValue = '';
  }

  hasPendingChanges(): boolean {
    return this.hasChanges;
  }

  confirmDiscardChanges(): Observable<boolean> {
    return this.appDialog.confirm({
      title: this.translate.instant('nmsTemplates.events.discardDialog.title'),
      message: this.translate.instant('nmsTemplates.events.discardDialog.message'),
      icon: 'bi-exclamation-triangle',
      variant: 'warning',
      confirmText: this.translate.instant('shared.adminCrud.discard.confirm'),
      cancelText: this.translate.instant('shared.adminCrud.discard.cancel'),
    });
  }

  load(): void {
    this.loading = true;
    const keep = this.currentSelection();
    this.svc.getEventDefinitions().subscribe({
      next: events => {
        this.events = events;
        this.selectedIndex = indexOfSelection(this.events, keep);
        this.attrDraft = '';
        this.setSavedSnapshot();
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.events = [];
        this.selectedIndex = -1;
        this.attrDraft = '';
        this.setSavedSnapshot();
        this.loading = false;
        this.snack.open(this.translate.instant('nmsTemplates.events.messages.loadFailed'), this.translate.instant('nmsTemplates.list.close'), { duration: 3000 });
        this.cdr.detectChanges();
      },
    });
  }

  selectEvent(index: number): void {
    this.selectedIndex = index;
    this.attrDraft = '';
  }

  addEvent(): void {
    const next: NmsEventDef = {
      value: this.nextEventValue(),
      label: this.translate.instant('nmsTemplates.events.newEventLabel'),
      attributes: [],
    };
    this.events = [...this.events, next];
    this.selectedIndex = this.events.length - 1;
  }

  deleteSelected(): void {
    if (!this.selected) return;
    this.appDialog.confirm({
      title: this.translate.instant('nmsTemplates.events.deleteDialog.title'),
      message: this.translate.instant('nmsTemplates.events.deleteDialog.message', { label: this.selected.label }),
      icon: 'bi-trash3',
      variant: 'danger',
      confirmText: this.translate.instant('shared.listPage.rowActions.delete'),
      cancelText: this.translate.instant('nmsTemplates.list.deleteDialog.keep'),
    }).subscribe(confirmed => {
      if (!confirmed) return;
      this.events = this.events.filter((_, index) => index !== this.selectedIndex);
      this.selectedIndex = this.events.length ? Math.max(0, this.selectedIndex - 1) : -1;
      this.attrDraft = '';
    });
  }

  updateLabel(label: string): void {
    if (!this.selected) return;
    this.selected.label = label;
    // A saved event keeps its value when it is renamed: templates are attached to the value, so it only changes when typed.
    if (!this.selected.id) {
      this.selected.value = normalizeEventValue(label);
    }
  }

  updateValue(value: string): void {
    if (!this.selected) return;
    this.selected.value = normalizeEventValue(value);
  }

  addAttribute(): void {
    if (!this.selected) return;
    const attr = normalizeEventAttribute(this.attrDraft);
    if (!attr) return;
    if (!this.selected.attributes.includes(attr)) {
      this.selected.attributes = [...this.selected.attributes, attr];
    }
    this.attrDraft = '';
  }

  removeAttribute(attr: string): void {
    if (!this.selected) return;
    this.selected.attributes = this.selected.attributes.filter(a => a !== attr);
  }

  save(): void {
    if (!this.hasChanges) return;
    if (!this.validate()) return;
    this.saving = true;
    const keep = this.currentSelection();
    this.svc.saveEventDefinitions(this.events, this.baseline).subscribe({
      next: events => {
        // The server answers sorted by label: find the event being edited again so the next edit lands on the same one.
        this.events = events;
        this.selectedIndex = indexOfSelection(this.events, keep);
        this.attrDraft = '';
        this.setSavedSnapshot();
        this.saving = false;
        this.snack.open(this.translate.instant('nmsTemplates.events.messages.saved'), this.translate.instant('nmsTemplates.list.close'), { duration: 2500 });
        this.cdr.detectChanges();
      },
      error: (err: { status?: number }) => {
        this.saving = false;
        const key = err?.status === 409 ? 'nmsTemplates.events.messages.conflict' : 'nmsTemplates.events.messages.saveFailed';
        this.snack.open(this.translate.instant(key), this.translate.instant('nmsTemplates.list.close'), { duration: 4000 });
        this.cdr.detectChanges();
      },
    });
  }

  /** Replaces every event on the server with the factory defaults, after a confirmation that says exactly that. */
  restoreDefaults(): void {
    this.appDialog.confirm({
      title: this.translate.instant('nmsTemplates.events.restoreDialog.title'),
      message: this.translate.instant('nmsTemplates.events.restoreDialog.message'),
      icon: 'bi-exclamation-octagon',
      variant: 'danger',
      confirmText: this.translate.instant('nmsTemplates.events.restoreDialog.confirm'),
      cancelText: this.translate.instant('shared.adminCrud.cancel'),
    }).subscribe(confirmed => {
      if (!confirmed) return;
      this.loading = true;
      this.svc.resetEventDefinitions().subscribe({
        next: events => {
          this.events = events;
          this.selectedIndex = this.events.length ? 0 : -1;
          this.attrDraft = '';
          this.setSavedSnapshot();
          this.loading = false;
          this.snack.open(this.translate.instant('nmsTemplates.events.messages.defaultsRestored'), this.translate.instant('nmsTemplates.list.close'), { duration: 2500 });
          this.cdr.detectChanges();
        },
        error: () => {
          this.loading = false;
          this.snack.open(this.translate.instant('nmsTemplates.events.messages.restoreFailed'), this.translate.instant('nmsTemplates.list.close'), { duration: 3000 });
          this.cdr.detectChanges();
        },
      });
    });
  }

  /** Leaves the screen; with unsaved edits the route's pending-changes guard asks first, so it is not asked twice here. */
  back(): void {
    this.router.navigate(['/nms-templates']);
  }

  private setSavedSnapshot(): void {
    this.savedSnapshot = this.currentSnapshot();
    this.baseline = this.events.map(event => normalizeEvent(event));
  }

  private currentSnapshot(): string {
    return eventsSignature(this.events);
  }

  /** The event being edited, so it can be found again in a list that was replaced and re-sorted. */
  private currentSelection(): EventSelection | null {
    const event = this.selected;
    return event ? { id: event.id, value: event.value } : null;
  }

  private validate(): boolean {
    for (const event of this.events) {
      Object.assign(event, normalizeEvent(event));
      if (!event.label || !event.value) {
        this.snack.open(this.translate.instant('nmsTemplates.events.messages.labelValueRequired'), this.translate.instant('nmsTemplates.list.close'), { duration: 3000 });
        return false;
      }
    }
    const [duplicate] = duplicateEventValues(this.events);
    if (duplicate) {
      this.snack.open(this.translate.instant('nmsTemplates.events.messages.duplicateValue', { value: duplicate }), this.translate.instant('nmsTemplates.list.close'), { duration: 3000 });
      return false;
    }
    return true;
  }

  private nextEventValue(): string {
    let i = this.events.length + 1;
    let value = `CUSTOM_EVENT_${i}`;
    const used = new Set(this.events.map(e => e.value));
    while (used.has(value)) {
      i += 1;
      value = `CUSTOM_EVENT_${i}`;
    }
    return value;
  }
}
