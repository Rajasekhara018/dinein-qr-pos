import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';

/** One small note on the right of the briefing ("Maya approved a fee waiver"). */
export interface HqBriefingNote {
  /** bootstrap-icons class of the note's round badge. */
  icon: string;
  text: string;
  /** A router path; the note is a link when present. */
  link?: string;
}

/**
 * The banner at the top of a home page: a small kicker line, a headline with one emphasised part ("3 need you before noon"),
 * and up to a few short notes on the right. It is drawn in the theme's primary colour, so every theme gets its own banner.
 * The library has no translation dependency: every word is an input. Anything projected inside appears under the headline.
 */
@Component({
  selector: 'hq-briefing',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="brief" [attr.aria-label]="kicker">
      <div class="brief__main">
        @if (kicker) {
          <span class="brief__kicker">{{ kicker }}</span>
        }
        <h2 class="brief__headline">{{ headline }} @if (emphasis) { <strong>{{ emphasis }}</strong> }</h2>
        <ng-content></ng-content>
      </div>
      @if (notes.length > 0) {
        <ul class="brief__notes">
          @for (note of notes; track note.text) {
            <li class="brief__note">
              <span class="brief__badge" aria-hidden="true"><i class="bi" [class]="note.icon"></i></span>
              @if (note.link) {
                <a [routerLink]="note.link">{{ note.text }}</a>
              } @else {
                <span>{{ note.text }}</span>
              }
            </li>
          }
        </ul>
      }
    </section>
  `,
  styles: [`
    :host { display: block; }
    .brief { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 1.25rem; padding: 1.4rem 1.6rem; border-radius: 20px;
      color: var(--hq-on-primary, #fff); position: relative; overflow: hidden;
      background:
        radial-gradient(circle at 88% 15%, color-mix(in srgb, var(--hq-secondary, #D4A017) 55%, transparent), transparent 52%),
        linear-gradient(120deg, var(--hq-primary, #0B6E4F), color-mix(in srgb, var(--hq-primary, #0B6E4F) 70%, #000));
      box-shadow: 0 14px 34px color-mix(in srgb, var(--hq-primary, #0B6E4F) 28%, transparent); }
    .brief__main { flex: 1 1 18rem; min-width: 0; }
    .brief__kicker { display: inline-block; font-size: .68rem; font-weight: 700; letter-spacing: .12em; text-transform: uppercase; opacity: .85; margin-bottom: .45rem; }
    .brief__headline { margin: 0; font-size: 1.45rem; font-weight: 600; line-height: 1.3; }
    .brief__headline strong { font-weight: 800; }
    .brief__notes { flex: 0 1 24rem; list-style: none; margin: 0; padding: 0; display: grid; gap: .6rem; }
    .brief__note { display: flex; align-items: center; gap: .65rem; padding: .6rem .8rem; border-radius: 14px; font-size: .84rem;
      background: rgba(255, 255, 255, .16); backdrop-filter: blur(6px); border: 1px solid rgba(255, 255, 255, .22); }
    .brief__note a { color: inherit; text-decoration: none; }
    .brief__note a:hover { text-decoration: underline; }
    .brief__badge { display: inline-flex; align-items: center; justify-content: center; width: 1.9rem; height: 1.9rem; border-radius: 50%; flex: 0 0 auto;
      background: rgba(255, 255, 255, .22); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HqBriefing {
  @Input() kicker = '';
  @Input({ required: true }) headline = '';
  /** The part of the headline drawn heavier ("3 need you before noon"). */
  @Input() emphasis = '';
  @Input() notes: readonly HqBriefingNote[] = [];
}
