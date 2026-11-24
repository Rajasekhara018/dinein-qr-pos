import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';

/** One entry of the timeline: when, what, and where it leads. */
export interface HqTimelineEntry {
  id: string;
  /** The time or date text ("10:30", "Today", "Overdue"). */
  when: string;
  title: string;
  meta?: string;
  tone?: 'plain' | 'pending' | 'bad';
  /** A router path; the entry is a link when present. */
  link?: string;
}

/** The "Today" column of a home page: a vertical line of dated entries, the colour of each dot showing how urgent it is. */
@Component({
  selector: 'hq-timeline',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="tl">
      <header class="tl__head">
        <h3 class="tl__title">{{ title }}</h3>
        @if (actionLabel && actionLink) {
          <a class="tl__action" [routerLink]="actionLink">{{ actionLabel }}</a>
        }
      </header>
      @if (entries.length === 0) {
        <p class="tl__empty">{{ emptyText }}</p>
      } @else {
        <ol class="tl__list">
          @for (entry of entries; track entry.id) {
            <li class="tl__item" [class.tl__item--pending]="entry.tone === 'pending'" [class.tl__item--bad]="entry.tone === 'bad'">
              <span class="tl__when">{{ entry.when }}</span>
              <span class="tl__dot" aria-hidden="true"></span>
              <span class="tl__body">
                @if (entry.link) {
                  <a class="tl__name" [routerLink]="entry.link">{{ entry.title }}</a>
                } @else {
                  <span class="tl__name">{{ entry.title }}</span>
                }
                @if (entry.meta) {
                  <span class="tl__meta">{{ entry.meta }}</span>
                }
              </span>
            </li>
          }
        </ol>
      }
    </section>
  `,
  styles: [`
    :host { display: block; }
    .tl { height: 100%; padding: 1rem 1.15rem 1.1rem; background: var(--workspace-panel, #fff); border: 1px solid var(--workspace-border, rgba(0,0,0,.08));
      border-radius: 16px; box-shadow: var(--theme-soft-shadow, 0 6px 24px rgba(0,0,0,.05)); }
    .tl__head { display: flex; align-items: center; justify-content: space-between; gap: .75rem; margin-bottom: .6rem; }
    .tl__title { margin: 0; font-size: 1rem; font-weight: 700; color: var(--edu-ink, #1b2a4a); }
    .tl__action { font-size: .8rem; font-weight: 600; color: var(--hq-link, var(--hq-primary, #0B6E4F)); text-decoration: none; }
    .tl__list { list-style: none; margin: 0; padding: 0; position: relative; }
    .tl__item { display: grid; grid-template-columns: 3.6rem 1rem 1fr; align-items: start; column-gap: .5rem; padding: .5rem 0; position: relative; }
    .tl__item::before { content: ''; position: absolute; left: calc(3.6rem + .5rem + .5rem - 1px); top: 0; bottom: 0; width: 2px;
      background: var(--workspace-border, rgba(0,0,0,.1)); }
    .tl__when { font-size: .76rem; font-weight: 600; color: var(--edu-muted, #5a6a7e); padding-top: .1rem; }
    .tl__dot { width: .65rem; height: .65rem; margin: .25rem auto 0; border-radius: 50%; position: relative; z-index: 1;
      background: var(--hq-primary, #0B6E4F); box-shadow: 0 0 0 3px var(--workspace-panel, #fff); }
    .tl__item--pending .tl__dot { background: var(--snackBar-warn, #E8A33D); }
    .tl__item--bad .tl__dot { background: var(--danger-text, #B3261E); }
    .tl__body { display: flex; flex-direction: column; min-width: 0; }
    .tl__name { font-size: .86rem; font-weight: 600; color: var(--edu-ink, #1b2a4a); text-decoration: none; overflow: hidden; text-overflow: ellipsis; }
    a.tl__name:hover { text-decoration: underline; }
    .tl__meta { font-size: .74rem; color: var(--edu-muted, #5a6a7e); }
    .tl__empty { margin: .4rem 0 0; font-size: .85rem; color: var(--edu-muted, #5a6a7e); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HqTimeline {
  @Input({ required: true }) title = '';
  @Input() entries: readonly HqTimelineEntry[] = [];
  @Input() actionLabel = '';
  @Input() actionLink = '';
  @Input() emptyText = '';
}
