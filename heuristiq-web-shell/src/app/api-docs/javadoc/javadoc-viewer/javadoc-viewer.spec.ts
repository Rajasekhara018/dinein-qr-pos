import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject } from 'rxjs';
import { vi } from 'vitest';
import { environment } from '../../../../environments/environment';
import { JAVADOC_LIST_ROUTE, JAVADOC_SERVICES } from '../javadoc.constants';
import { JavadocViewer } from './javadoc-viewer';

describe('JavadocViewer', () => {
  const params$ = new BehaviorSubject(convertToParamMap({ service: 'uam' }));
  const router = { navigateByUrl: vi.fn(() => Promise.resolve(true)) };
  const originalUrl = environment.javaDocsUrl;
  let fixture: ComponentFixture<JavadocViewer>;

  function create(): JavadocViewer {
    TestBed.configureTestingModule({
      declarations: [JavadocViewer],
      imports: [TranslateModule.forRoot()],
      providers: [
        { provide: ActivatedRoute, useValue: { paramMap: params$ } },
        { provide: Router, useValue: router },
      ],
    });
    fixture = TestBed.createComponent(JavadocViewer);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  function render(): void {
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
  }

  /** A load event whose frame document looks like the given one (null = unreadable). */
  function frameEvent(doc: unknown, throws = false): Event {
    const frame = throws
      ? { get contentDocument(): never { throw new Error('cross-origin'); } }
      : { contentDocument: doc };
    return { target: frame } as unknown as Event;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    // A host that resolves nowhere, so the test window never loads real documentation.
    (environment as { javaDocsUrl: string }).javaDocsUrl = 'http://docs.invalid/javadoc/index.html';
    params$.next(convertToParamMap({ service: 'uam' }));
  });

  afterEach(() => {
    (environment as { javaDocsUrl: string }).javaDocsUrl = originalUrl;
  });

  it('opens the service named in the route against the gateway origin and shows its name', () => {
    const component = create();
    const uam = JAVADOC_SERVICES.find((s) => s.id === 'uam');

    expect(component.service).toEqual(uam);
    expect(component.docUrl).toBe('http://docs.invalid/api/uam/javadoc/');
    expect(component.frameUrl).not.toBeNull();
    expect(component.loading).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('UAM');
    const link = fixture.nativeElement.querySelector('a[target="_blank"]') as HTMLAnchorElement;
    expect(link.href).toBe('http://docs.invalid/api/uam/javadoc/');
    expect(link.rel).toContain('noopener');
    expect(fixture.nativeElement.querySelector('iframe')).not.toBeNull();
  });

  it('sends an unknown service id back to the list and shows nothing', () => {
    params$.next(convertToParamMap({ service: 'nope' }));
    const component = create();

    expect(router.navigateByUrl).toHaveBeenCalledWith(JAVADOC_LIST_ROUTE);
    expect(component.service).toBeNull();
    expect(fixture.nativeElement.querySelector('section')).toBeNull();
  });

  it('switches service when the route parameter changes', () => {
    const component = create();

    params$.next(convertToParamMap({ service: 'gateway' }));

    expect(component.service?.id).toBe('gateway');
    expect(component.docUrl).toBe('http://docs.invalid/javadoc/gateway/index.html');
  });

  it('goes back to the list explicitly from the toolbar', () => {
    create();

    (fixture.nativeElement.querySelector('.jd-viewer__bar button') as HTMLButtonElement).click();

    expect(router.navigateByUrl).toHaveBeenCalledWith(JAVADOC_LIST_ROUTE);
  });

  it('reload loads the frame again with a fresh url object and clears the unavailable state', () => {
    const component = create();
    const before = component.frameUrl;
    component.onFrameLoad(frameEvent({ contentType: 'application/json', title: '' }));
    expect(component.unavailable).toBe(true);

    component.reload();

    expect(component.frameUrl).not.toBe(before);
    expect(component.unavailable).toBe(false);
    expect(component.loading).toBe(true);
    expect(component.service?.id).toBe('uam');
  });

  it('treats a normal html document as loaded', () => {
    const component = create();

    component.onFrameLoad(frameEvent({ contentType: 'text/html', title: 'UAM API', querySelector: () => null }));

    expect(component.loading).toBe(false);
    expect(component.unavailable).toBe(false);
  });

  it('flags a non-html answer (the gateway\'s JSON error) as unavailable and offers back and reload', () => {
    const component = create();

    component.onFrameLoad(frameEvent({ contentType: 'application/json', title: '' }));
    render();

    expect(component.unavailable).toBe(true);
    expect(fixture.nativeElement.querySelector('.jd-viewer__unavailable')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('iframe').classList).toContain('jd-viewer__frame-hidden');
    expect(fixture.nativeElement.querySelector('.jd-viewer__loading')).toBeNull();
  });

  it('flags a Whitelabel error page by title or by heading', () => {
    const component = create();

    component.onFrameLoad(frameEvent({ contentType: 'text/html', title: 'Whitelabel Error Page' }));
    expect(component.unavailable).toBe(true);

    component.onFrameLoad(frameEvent({ contentType: 'text/html', title: '', querySelector: () => ({ textContent: 'Whitelabel Error Page' }) }));
    expect(component.unavailable).toBe(true);

    component.onFrameLoad(frameEvent({ contentType: 'text/html', title: 'Overview', querySelector: () => ({ textContent: 'Overview' }) }));
    expect(component.unavailable).toBe(false);
  });

  it('treats an unreadable frame (cross-origin, or no document) as loaded', () => {
    const component = create();

    component.onFrameLoad(frameEvent(null, true));
    expect(component.unavailable).toBe(false);
    expect(component.loading).toBe(false);

    component.onFrameLoad(frameEvent(null));
    expect(component.unavailable).toBe(false);
  });
});
