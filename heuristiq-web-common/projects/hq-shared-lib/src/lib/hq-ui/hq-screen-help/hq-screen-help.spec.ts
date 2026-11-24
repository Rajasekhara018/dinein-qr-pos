import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HqSharedLibModule } from '../../hq-shared-lib.module';
import { HqScreenHelp } from './hq-screen-help';
import {
  HQ_SCREEN_HELP_COLLAPSED_PREFIX,
  HQ_SCREEN_HELP_OPENED_PREFIX,
  HQ_SCREEN_HELP_DEFAULT_TEXTS,
  HQ_SCREEN_HELP_TEXTS,
  HqScreenHelpContent,
  HqScreenHelpTexts,
} from './hq-screen-help.texts';

const HELP: Record<string, HqScreenHelpContent> = {
  users: { what: 'Manage users.', steps: ['Search', 'Open', 'Save'], tip: 'Changes apply at next sign-in.' },
  bare: { what: 'Only the what.', steps: [], tip: '' },
  empty: { what: '', steps: ['x'], tip: 'y' },
};

describe('HqScreenHelp', () => {
  let fixture: ComponentFixture<HqScreenHelp>;
  let el: HTMLElement;

  function create(screen: string, texts: HqScreenHelpTexts | null = { title: () => 'How it works', read: (s) => HELP[s] ?? null }) {
    TestBed.configureTestingModule({
      imports: [HqSharedLibModule],
      providers: texts ? [{ provide: HQ_SCREEN_HELP_TEXTS, useValue: texts }] : [],
    });
    fixture = TestBed.createComponent(HqScreenHelp);
    el = fixture.nativeElement;
    fixture.componentRef.setInput('screen', screen);
    fixture.detectChanges();
  }

  const q = (sel: string) => el.querySelector<HTMLElement>(sel);

  beforeEach(() => localStorage.clear());
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('starts closed by default, showing only the title', () => {
    create('users');
    expect(fixture.componentInstance.closed).toBe(true);
    expect(q('.rsh')?.classList).toContain('rsh--closed');
    expect(q('.rsh__body')).toBeNull();
    expect(q('.rsh__toggle')?.getAttribute('aria-expanded')).toBe('false');
  });

  it('shows what, steps and tip once opened', () => {
    create('users');
    q('.rsh__toggle')!.click();
    fixture.detectChanges();
    expect(q('.rsh__toggle span')?.textContent).toBe('How it works');
    expect(q('.rsh__toggle')?.getAttribute('aria-expanded')).toBe('true');
    expect(q('.rsh__what')?.textContent).toBe('Manage users.');
    expect(Array.from(el.querySelectorAll('.rsh__steps li')).map((li) => li.textContent)).toEqual(['Search', 'Open', 'Save']);
    expect(q('.rsh__tip')?.textContent?.trim()).toBe('Changes apply at next sign-in.');
    expect(q('.bi-chevron-up')).not.toBeNull();
  });

  it('leaves out the steps list and tip when they are empty', () => {
    create('bare');
    q('.rsh__toggle')!.click();
    fixture.detectChanges();
    expect(q('.rsh__what')?.textContent).toBe('Only the what.');
    expect(q('.rsh__steps')).toBeNull();
    expect(q('.rsh__tip')).toBeNull();
  });

  it('shows nothing for a screen without help or with an empty "what"', () => {
    create('unknown');
    expect(q('.rsh')).toBeNull();
    fixture.componentRef.setInput('screen', 'empty');
    fixture.detectChanges();
    expect(q('.rsh')).toBeNull();
  });

  it('shows nothing with the default texts (no app provider)', () => {
    create('users', null);
    expect(q('.rsh')).toBeNull();
    expect(fixture.componentInstance.title).toBe(HQ_SCREEN_HELP_DEFAULT_TEXTS.title());
  });

  it('opens and closes on toggle, remembering an opened panel per screen', () => {
    create('users');
    q('.rsh__toggle')!.click();
    fixture.detectChanges();
    expect(q('.rsh__body')).not.toBeNull();
    expect(q('.rsh__toggle')?.getAttribute('aria-expanded')).toBe('true');
    expect(q('.bi-chevron-up')).not.toBeNull();
    expect(localStorage.getItem(HQ_SCREEN_HELP_OPENED_PREFIX + 'users')).toBe('1');

    q('.rsh__toggle')!.click();
    fixture.detectChanges();
    expect(q('.rsh')?.classList).toContain('rsh--closed');
    expect(q('.bi-chevron-down')).not.toBeNull();
    expect(localStorage.getItem(HQ_SCREEN_HELP_OPENED_PREFIX + 'users')).toBeNull();
  });

  it('starts open on a screen where the person opened it before, and only there', () => {
    localStorage.setItem(HQ_SCREEN_HELP_OPENED_PREFIX + 'users', '1');
    create('users');
    expect(fixture.componentInstance.closed).toBe(false);
    expect(q('.rsh__body')).not.toBeNull();
    fixture.componentRef.setInput('screen', 'bare');
    fixture.detectChanges();
    expect(fixture.componentInstance.closed).toBe(true);
  });

  it('ignores the old "closed" flag: help is closed anyway', () => {
    localStorage.setItem(HQ_SCREEN_HELP_COLLAPSED_PREFIX + 'users', '1');
    create('users');
    expect(fixture.componentInstance.closed).toBe(true);
  });

  it('still works when storage is unavailable (reads as closed, toggles for this view only)', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked'); });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked'); });
    create('users');
    expect(fixture.componentInstance.closed).toBe(true);
    fixture.componentInstance.toggle();
    expect(fixture.componentInstance.closed).toBe(false);
  });
});
