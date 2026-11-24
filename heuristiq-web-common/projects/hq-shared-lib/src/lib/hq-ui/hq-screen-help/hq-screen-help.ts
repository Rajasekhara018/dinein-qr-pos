import { Component, Input, OnChanges, inject } from '@angular/core';
import {
  HQ_SCREEN_HELP_DEFAULT_TEXTS,
  HQ_SCREEN_HELP_OPENED_PREFIX,
  HQ_SCREEN_HELP_TEXTS,
  HqScreenHelpContent,
} from './hq-screen-help.texts';

/**
 * A short "how this screen works" panel: what the screen is for, the steps for the usual task and one thing worth knowing.
 * The text comes from the app's HQ_SCREEN_HELP_TEXTS provider. It starts closed on every screen, so screens open on their
 * content; a person who opens it keeps it open on that screen (on that device) until they close it again. Nothing is shown
 * for a screen without help text.
 */
@Component({
  selector: 'hq-screen-help',
  templateUrl: './hq-screen-help.html',
  styleUrl: './hq-screen-help.scss',
  standalone: false,
})
export class HqScreenHelp implements OnChanges {
  private readonly texts = inject(HQ_SCREEN_HELP_TEXTS, { optional: true }) ?? HQ_SCREEN_HELP_DEFAULT_TEXTS;

  /** The screen's help key, as the app's texts know it. */
  @Input({ required: true }) screen = '';

  closed = true;
  content: HqScreenHelpContent | null = null;

  get title(): string {
    return this.texts.title();
  }

  ngOnChanges(): void {
    this.closed = !this.readOpened();
    const content = this.texts.read(this.screen);
    this.content = content && content.what ? content : null;
  }

  toggle(): void {
    this.closed = !this.closed;
    try {
      if (this.closed) localStorage.removeItem(HQ_SCREEN_HELP_OPENED_PREFIX + this.screen);
      else localStorage.setItem(HQ_SCREEN_HELP_OPENED_PREFIX + this.screen, '1');
    } catch {
      /* storage unavailable: the choice lasts for this view only */
    }
  }

  private readOpened(): boolean {
    try {
      return localStorage.getItem(HQ_SCREEN_HELP_OPENED_PREFIX + this.screen) === '1';
    } catch {
      return false;
    }
  }
}
