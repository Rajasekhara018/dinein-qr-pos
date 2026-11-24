import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { HqBriefing } from './hq-briefing';
import { HqDecisionList } from './hq-decision-list';
import { HqTimeline } from './hq-timeline';

@Component({
  standalone: true,
  imports: [HqBriefing, HqDecisionList, HqTimeline],
  template: `
    <hq-briefing kicker="Today" headline="14 things done." emphasis="3 need you" [notes]="notes"><p class="extra">more</p></hq-briefing>
    <hq-decision-list title="Needs your decision" subtitle="3 waiting" [items]="items" viewAllLabel="View all" viewAllLink="/all" emptyText="Nothing waiting"></hq-decision-list>
    <hq-decision-list title="Empty" [items]="[]" emptyText="Nothing waiting"></hq-decision-list>
    <hq-timeline title="Today" [entries]="entries" emptyText="No entries"></hq-timeline>
    <hq-timeline title="Quiet" [entries]="[]" emptyText="No entries"></hq-timeline>`,
})
class Host {
  notes = [{ icon: 'bi-bell', text: 'Maya approved a waiver', link: '/n' }, { icon: 'bi-info', text: 'Plain note' }];
  items = [
    { id: 'a', icon: 'bi-x', title: 'Approve payout', meta: 'Franchise', tag: 'Overdue', tagTone: 'bad' as const, actionLabel: 'Review', link: '/p' },
    { id: 'b', icon: 'bi-y', title: 'Read me', link: '/r' },
  ];
  entries = [{ id: 'e1', when: '10:30', title: 'Standup', meta: 'Room 2', tone: 'pending' as const, link: '/s' }, { id: 'e2', when: '11:00', title: 'Lunch' }];
}

describe('home page components', () => {
  function render(): HTMLElement {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('draws the briefing with its headline, the emphasised part, the notes (linked or plain) and projected content', () => {
    const el = render();

    expect(el.querySelector('.brief__kicker')?.textContent).toBe('Today');
    expect(el.querySelector('.brief__headline')?.textContent).toContain('14 things done.');
    expect(el.querySelector('.brief__headline strong')?.textContent).toBe('3 need you');
    expect(el.querySelectorAll('.brief__note').length).toBe(2);
    expect(el.querySelector('.brief__note a')?.textContent).toBe('Maya approved a waiver');
    expect(el.querySelectorAll('.brief__note a').length).toBe(1);
    expect(el.querySelector('.brief .extra')).not.toBeNull();
  });

  it('lists what is waiting with its tag and button, a view-all link, and says so when nothing is waiting', () => {
    const el = render();
    const lists = el.querySelectorAll('hq-decision-list');

    expect(lists[0].querySelectorAll('.dl__row').length).toBe(2);
    expect(lists[0].querySelector('.dl__tag--bad')?.textContent).toBe('Overdue');
    expect(lists[0].querySelectorAll('.dl__action').length).toBe(1);
    expect(lists[0].querySelector('.dl__all')?.getAttribute('href')).toBe('/all');
    expect(lists[1].querySelector('.dl__empty')?.textContent).toContain('Nothing waiting');
    expect(lists[1].querySelector('.dl__all')).toBeNull();
  });

  it('draws the timeline with the urgency of each entry, linking the entries that lead somewhere, and an empty text', () => {
    const el = render();
    const timelines = el.querySelectorAll('hq-timeline');

    expect(timelines[0].querySelectorAll('.tl__item').length).toBe(2);
    expect(timelines[0].querySelector('.tl__item--pending .tl__when')?.textContent).toBe('10:30');
    expect(timelines[0].querySelectorAll('a.tl__name').length).toBe(1);
    expect(timelines[1].querySelector('.tl__empty')?.textContent).toBe('No entries');
  });
});
