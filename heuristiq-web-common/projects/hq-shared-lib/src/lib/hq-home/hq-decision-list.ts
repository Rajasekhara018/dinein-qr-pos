import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';

/** One row of the list: a thing that is waiting for the user, with the one action that deals with it. */
export interface HqDecisionItem {
  id: string;
  /** bootstrap-icons class of the row's round badge. */
  icon: string;
  title: string;
  /** Small print under the title (where it comes from). */
  meta?: string;
  /** A short coloured tag ("Overdue", "3 waiting"). */
  tag?: string;
  tagTone?: 'plain' | 'pending' | 'bad';
  /** The text of the action button ("Review"); the row has no button when empty. */
  actionLabel?: string;
  /** A router path the row and its button lead to. */
  link: string;
}

/**
 * The "needs your decision" card of a home page: a titled list of waiting things, each with a tag and a button, and a "view all"
 * link. Words are inputs, as the library has no translation dependency.
 */
@Component({
  selector: 'hq-decision-list',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="dl">
      <header class="dl__head">
        <div>
          <h3 class="dl__title">{{ title }}</h3>
          @if (subtitle) {
            <p class="dl__subtitle">{{ subtitle }}</p>
          }
        </div>
        @if (viewAllLabel && viewAllLink) {
          <a class="dl__all" [routerLink]="viewAllLink">{{ viewAllLabel }} <i class="bi bi-arrow-right" aria-hidden="true"></i></a>
        }
      </header>
      @if (items.length === 0) {
        <p class="dl__empty"><i class="bi bi-check2-circle" aria-hidden="true"></i> {{ emptyText }}</p>
      } @else {
        <ul class="dl__list">
          @for (item of items; track item.id) {
            <li class="dl__row">
              <span class="dl__badge" [class.dl__badge--pending]="item.tagTone === 'pending'" [class.dl__badge--bad]="item.tagTone === 'bad'" aria-hidden="true">
                <i class="bi" [class]="item.icon"></i>
              </span>
              <a class="dl__text" [routerLink]="item.link">
                <span class="dl__name">{{ item.title }}</span>
                @if (item.meta) {
                  <span class="dl__meta">{{ item.meta }}</span>
                }
              </a>
              @if (item.tag) {
                <span class="dl__tag" [class.dl__tag--pending]="item.tagTone === 'pending'" [class.dl__tag--bad]="item.tagTone === 'bad'">{{ item.tag }}</span>
              }
              @if (item.actionLabel) {
                <a class="dl__action" [routerLink]="item.link">{{ item.actionLabel }}</a>
              }
            </li>
          }
        </ul>
      }
    </section>
  `,
  styles: [`
    :host { display: block; }
    .dl { height: 100%; padding: 1rem 1.15rem 1.1rem; background: var(--workspace-panel, #fff); border: 1px solid var(--workspace-border, rgba(0,0,0,.08));
      border-radius: 16px; box-shadow: var(--theme-soft-shadow, 0 6px 24px rgba(0,0,0,.05)); }
    .dl__head { display: flex; align-items: flex-start; justify-content: space-between; gap: .75rem; margin-bottom: .5rem; }
    .dl__title { margin: 0; font-size: 1rem; font-weight: 700; color: var(--edu-ink, #1b2a4a); }
    .dl__subtitle { margin: .15rem 0 0; font-size: .78rem; color: var(--edu-muted, #5a6a7e); }
    .dl__all { font-size: .8rem; font-weight: 600; white-space: nowrap; color: var(--hq-link, var(--hq-primary, #0B6E4F)); text-decoration: none; }
    .dl__all:hover { text-decoration: underline; }
    .dl__list { list-style: none; margin: 0; padding: 0; }
    .dl__row { display: flex; align-items: center; gap: .75rem; padding: .7rem 0; border-top: 1px solid var(--workspace-border, rgba(0,0,0,.08)); }
    .dl__row:first-child { border-top: 0; }
    .dl__badge { display: inline-flex; align-items: center; justify-content: center; width: 2.1rem; height: 2.1rem; border-radius: 50%; flex: 0 0 auto;
      background: color-mix(in srgb, var(--hq-primary, #0B6E4F) 12%, transparent); color: var(--hq-link, var(--hq-primary, #0B6E4F)); }
    .dl__badge--pending { background: color-mix(in srgb, var(--snackBar-warn, #E8A33D) 20%, transparent); color: var(--theme-pending-pill-text, #8F5B00); }
    .dl__badge--bad { background: var(--theme-failed-pill-bg, #F9E3E1); color: var(--theme-failed-pill-text, #B3261E); }
    .dl__text { flex: 1 1 auto; min-width: 0; display: flex; flex-direction: column; text-decoration: none; color: inherit; }
    .dl__name { font-size: .9rem; font-weight: 600; color: var(--edu-ink, #1b2a4a); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .dl__meta { font-size: .76rem; color: var(--edu-muted, #5a6a7e); }
    .dl__tag { flex: 0 0 auto; font-size: .72rem; font-weight: 700; padding: .15rem .55rem; border-radius: 999px;
      background: color-mix(in srgb, var(--edu-muted, #5a6a7e) 14%, transparent); color: var(--edu-muted, #5a6a7e); }
    .dl__tag--pending { background: var(--theme-pending-pill-bg, #FBF0DC); color: var(--theme-pending-pill-text, #8F5B00); }
    .dl__tag--bad { background: var(--theme-failed-pill-bg, #F9E3E1); color: var(--theme-failed-pill-text, #B3261E); }
    .dl__action { flex: 0 0 auto; font-size: .8rem; font-weight: 700; padding: .35rem .9rem; border-radius: 999px; text-decoration: none;
      color: var(--hq-on-primary, #fff); background: var(--hq-primary, #0B6E4F); transition: filter .15s; }
    .dl__action:hover { filter: brightness(1.08); }
    .dl__empty { margin: .6rem 0 0; font-size: .88rem; color: var(--edu-muted, #5a6a7e); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HqDecisionList {
  @Input({ required: true }) title = '';
  @Input() subtitle = '';
  @Input() items: readonly HqDecisionItem[] = [];
  @Input() viewAllLabel = '';
  @Input() viewAllLink = '';
  /** Shown when nothing is waiting. */
  @Input() emptyText = '';
}
