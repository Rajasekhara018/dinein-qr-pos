import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { NMS_CHANNELS, NmsChannel } from '../event-attributes.config';

/**
 * Communication templates: one tab per channel (Email, SMS, WhatsApp, Push) and the list of that channel's templates under it, on the
 * same screen. The chosen channel is kept in the address (`?channel=SMS`), so a link opens the right tab and the browser's back
 * button and a reload keep it.
 */
@Component({
  selector: 'app-template-landing',
  templateUrl: './template-landing.html',
  styleUrl: './template-landing.scss',
  standalone: false,
})
export class TemplateLanding implements OnInit, OnDestroy {
  channels = NMS_CHANNELS;
  /** The channel whose tab is open. */
  selected: NmsChannel = NMS_CHANNELS[0].value;
  private sub?: Subscription;

  constructor(private router: Router, private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.sub = this.route.queryParamMap.subscribe((params) => {
      const wanted = params.get('channel');
      const known = this.channels.find((channel) => channel.value === wanted);
      this.selected = known ? known.value : NMS_CHANNELS[0].value;
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  /** Opens a channel's tab and records it in the address without adding a history entry for every click. */
  selectChannel(channel: NmsChannel): void {
    this.selected = channel;
    this.router.navigate([], { relativeTo: this.route, queryParams: { channel }, replaceUrl: true });
  }

  /** Arrow keys, Home and End move between the tabs, as in any tab bar. */
  onTabKey(event: KeyboardEvent, index: number): void {
    const last = this.channels.length - 1;
    const target: Record<string, number> = {
      ArrowRight: index === last ? 0 : index + 1,
      ArrowLeft: index === 0 ? last : index - 1,
      Home: 0,
      End: last,
    };
    const next = target[event.key];
    if (next === undefined) {
      return;
    }
    event.preventDefault();
    const channel = this.channels[next].value;
    this.selectChannel(channel);
    queueMicrotask(() => document.getElementById('nms-tab-' + channel)?.focus());
  }

  openEvents(): void {
    this.router.navigate(['/nms-templates/events']);
  }
}
