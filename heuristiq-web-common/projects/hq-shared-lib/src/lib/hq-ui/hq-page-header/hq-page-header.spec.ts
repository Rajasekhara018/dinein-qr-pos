import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HqSharedLibModule } from '../../hq-shared-lib.module';
import { HqPageHeader } from './hq-page-header';

@Component({
  standalone: true,
  imports: [HqSharedLibModule],
  template: `<hq-page-header title="T"><button actions class="my-action">Add</button><span class="stray">x</span></hq-page-header>`,
})
class ProjectionHost {}

describe('HqPageHeader', () => {
  let fixture: ComponentFixture<HqPageHeader>;
  let el: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HqSharedLibModule, ProjectionHost] });
    fixture = TestBed.createComponent(HqPageHeader);
    el = fixture.nativeElement;
  });

  const set = (inputs: Record<string, unknown>) => {
    Object.entries(inputs).forEach(([k, v]) => fixture.componentRef.setInput(k, v));
    fixture.detectChanges();
  };
  const q = (sel: string) => el.querySelector<HTMLElement>(sel);

  it('shows only the title when nothing else is set', () => {
    set({ title: 'Customers' });
    expect(q('.page-hdr__title')?.textContent?.trim()).toBe('Customers');
    expect(q('.page-hdr__kicker')).toBeNull();
    expect(q('.page-hdr__subtitle')).toBeNull();
    expect(q('.page-hdr__back')).toBeNull();
    expect(q('.page-hdr__meta')).toBeNull();
  });

  it('shows kicker and subtitle when given', () => {
    set({ title: 'T', kicker: 'Admin', subtitle: 'All of them' });
    expect(q('.page-hdr__kicker')?.textContent?.trim()).toBe('Admin');
    expect(q('.page-hdr__subtitle')?.textContent?.trim()).toBe('All of them');
  });

  it('shows the last-updated line only with a time, a label and not loading', () => {
    set({ lastUpdated: new Date(), lastUpdatedLabel: 'Updated 10:42' });
    expect(q('.page-hdr__meta')?.textContent?.trim()).toBe('Updated 10:42');

    set({ lastUpdatedLabel: '' });
    expect(q('.page-hdr__meta')).toBeNull();

    set({ lastUpdatedLabel: 'Updated', lastUpdated: null });
    expect(q('.page-hdr__meta')).toBeNull();
  });

  it('replaces the last-updated line with the loading line while loading', () => {
    set({ lastUpdated: new Date(), lastUpdatedLabel: 'Updated', loading: true, loadingLabel: 'Loading…' });
    const metas = el.querySelectorAll('.page-hdr__meta');
    expect(metas.length).toBe(1);
    expect(metas[0].classList).toContain('page-hdr__meta--loading');
    expect(metas[0].textContent?.trim()).toBe('Loading…');
  });

  it('shows a labelled back button that emits backClick', () => {
    const clicks = vi.fn();
    fixture.componentInstance.backClick.subscribe(clicks);
    set({ showBack: true, backLabel: 'Back to list' });
    const back = q('.page-hdr__back') as HTMLButtonElement;
    expect(back.getAttribute('aria-label')).toBe('Back to list');
    expect(back.title).toBe('Back to list');
    back.click();
    expect(clicks).toHaveBeenCalledTimes(1);
  });

  it('projects only [actions] content into the actions slot', () => {
    const host = TestBed.createComponent(ProjectionHost);
    host.detectChanges();
    const actions = (host.nativeElement as HTMLElement).querySelector('.page-hdr__actions')!;
    expect(actions.querySelector('.my-action')?.textContent).toBe('Add');
    expect((host.nativeElement as HTMLElement).querySelector('.stray')).toBeNull();
  });
});
