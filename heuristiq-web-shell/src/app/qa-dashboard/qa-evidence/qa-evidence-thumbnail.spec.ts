import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { TranslateModule } from '@ngx-translate/core';
import { HqDocument, HqDocumentService } from 'hq-shared-lib';
import { Observable, Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import ar from '../../../assets/i18n/ar.json';
import en from '../../../assets/i18n/en.json';
import hi from '../../../assets/i18n/hi.json';
import kn from '../../../assets/i18n/kn.json';
import ml from '../../../assets/i18n/ml.json';
import ta from '../../../assets/i18n/ta.json';
import te from '../../../assets/i18n/te.json';
import { QA_THUMBNAIL, QA_THUMBNAIL_KEYS } from './qa-evidence.constants';
import { showsAsThumbnail } from './qa-evidence.helpers';
import { QaEvidenceThumbnail } from './qa-evidence-thumbnail';
import { QaEvidenceThumbnailService } from './qa-evidence-thumbnail.service';

const doc = (id: string, over: Partial<HqDocument> = {}): HqDocument =>
  ({ id, originalFileName: `${id}.png`, contentType: 'image/png', sizeBytes: 1000, ...over }) as HqDocument;
const image = (): Blob => new Blob(['x'], { type: 'image/png' });

describe('showsAsThumbnail', () => {
  it('shows the failure screenshot and the final state of a scenario as pictures and leaves an unknown kind as a link', () => {
    expect(showsAsThumbnail('FAILURE')).toBe(true);
    expect(showsAsThumbnail('SCREEN')).toBe(true);
    expect(showsAsThumbnail('???')).toBe(false);
  });
});

describe('QaEvidenceThumbnailService', () => {
  const documents = {
    getMetadata: vi.fn((id: string): Observable<HqDocument> => of(doc(id))),
    fetchFile: vi.fn((_doc: HqDocument): Observable<Blob> => of(image())),
  };
  let service: QaEvidenceThumbnailService;
  let sanitizer: DomSanitizer;
  let created: number;
  let revoked: string[];

  beforeEach(() => {
    vi.clearAllMocks();
    documents.getMetadata.mockImplementation((id: string) => of(doc(id)));
    documents.fetchFile.mockImplementation(() => of(image()));
    created = 0;
    revoked = [];
    URL.createObjectURL = vi.fn(() => `blob:qa-${++created}`);
    URL.revokeObjectURL = vi.fn((url: string) => {
      revoked.push(url);
    });
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [QaEvidenceThumbnailService, { provide: HqDocumentService, useValue: documents }] });
    service = TestBed.inject(QaEvidenceThumbnailService);
    sanitizer = TestBed.inject(DomSanitizer);
  });

  afterEach(() => vi.restoreAllMocks());

  const loadOne = (id: string): { url: SafeUrl | null | undefined; failed: boolean } => {
    const result: { url: SafeUrl | null | undefined; failed: boolean } = { url: undefined, failed: false };
    service.load(id).subscribe({ next: (u) => (result.url = u), error: () => (result.failed = true) });
    return result;
  };

  it('downloads the picture through the metadata and the bytes, and answers an image URL', () => {
    const result = loadOne('shot-1');
    expect(documents.getMetadata).toHaveBeenCalledWith('shot-1');
    expect(documents.fetchFile).toHaveBeenCalledTimes(1);
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
    expect(result.url).toBeTruthy();
  });

  it('keeps a picture for the life of the page: the second ask is answered from the cache', () => {
    const first = loadOne('shot-1');
    const second = loadOne('shot-1');
    expect(documents.getMetadata).toHaveBeenCalledTimes(1);
    expect(created).toBe(1);
    expect(second.url).toBe(first.url);
  });

  it('marks as safe only a blob: URL that it has just created from downloaded bytes', () => {
    const bypass = vi.spyOn(sanitizer, 'bypassSecurityTrustUrl');
    loadOne('shot-1');
    loadOne('shot-2');
    expect(bypass).toHaveBeenCalledTimes(2);
    for (const call of bypass.mock.calls) expect(call[0].startsWith('blob:')).toBe(true);
  });

  it('never marks anything as safe when the browser hands back something that is not a blob: URL', () => {
    URL.createObjectURL = vi.fn(() => 'https://evil.example/x.png');
    const bypass = vi.spyOn(sanitizer, 'bypassSecurityTrustUrl');
    const result = loadOne('shot-1');
    expect(result.url).toBeNull();
    expect(bypass).not.toHaveBeenCalled();
    expect(revoked).toContain('https://evil.example/x.png');
  });

  it('does not load a file bigger than the limit, nor a file that is not an image', () => {
    documents.getMetadata.mockImplementation((id: string) => of(doc(id, { sizeBytes: QA_THUMBNAIL.maxBytes + 1 })));
    expect(loadOne('big').url).toBeNull();
    documents.getMetadata.mockImplementation((id: string) => of(doc(id, { contentType: 'application/pdf' })));
    expect(loadOne('pdf').url).toBeNull();
    documents.getMetadata.mockImplementation((id: string) => of(doc(id, { contentType: undefined as unknown as string })));
    expect(loadOne('untyped').url).toBeNull();
    expect(documents.fetchFile).not.toHaveBeenCalled();
    expect(URL.createObjectURL).not.toHaveBeenCalled();
  });

  it('accepts a file of exactly the limit', () => {
    documents.getMetadata.mockImplementation((id: string) => of(doc(id, { sizeBytes: QA_THUMBNAIL.maxBytes })));
    expect(loadOne('edge').url).toBeTruthy();
  });

  it('answers null when the bytes turn out not to be an image', () => {
    documents.fetchFile.mockImplementation(() => of(new Blob(['x'], { type: 'text/html' })));
    expect(loadOne('odd').url).toBeNull();
    expect(URL.createObjectURL).not.toHaveBeenCalled();
  });

  it('refuses an id that is not a plain id without asking the DMS anything', () => {
    expect(loadOne('../etc/passwd').url).toBeNull();
    expect(loadOne('').url).toBeNull();
    expect(documents.getMetadata).not.toHaveBeenCalled();
  });

  it('passes a failure on, so the screen can fall back to the link', () => {
    documents.getMetadata.mockImplementation(() => throwError(() => new Error('gone')));
    expect(loadOne('shot-1').failed).toBe(true);
    documents.getMetadata.mockImplementation((id: string) => of(doc(id)));
    expect(loadOne('shot-1').url).toBeTruthy();
  });

  describe('the queue', () => {
    let pending: Map<string, Subject<HqDocument>>;

    beforeEach(() => {
      pending = new Map();
      documents.getMetadata.mockImplementation((id: string) => {
        const subject = new Subject<HqDocument>();
        pending.set(id, subject);
        return subject;
      });
    });

    const finish = (id: string): void => {
      pending.get(id)?.next(doc(id));
      pending.get(id)?.complete();
    };

    it('never runs more than three downloads at the same time', () => {
      ['a', 'b', 'c', 'd', 'e'].forEach((id) => loadOne(id));
      expect(documents.getMetadata).toHaveBeenCalledTimes(QA_THUMBNAIL.maxConcurrent);
      finish('a');
      expect(documents.getMetadata).toHaveBeenCalledTimes(4);
      finish('b');
      expect(documents.getMetadata).toHaveBeenCalledTimes(5);
    });

    it('lets the next one start when a download fails', () => {
      ['a', 'b', 'c', 'd'].forEach((id) => loadOne(id));
      pending.get('a')?.error(new Error('boom'));
      expect(documents.getMetadata).toHaveBeenCalledTimes(4);
    });

    it('shares one download between two asks for the same picture', () => {
      loadOne('a');
      loadOne('a');
      expect(documents.getMetadata).toHaveBeenCalledTimes(1);
    });

    it('forgets a download that nobody waits for any more, and frees its place', () => {
      const subscriptions = ['a', 'b', 'c', 'd'].map((id) => service.load(id).subscribe());
      expect(documents.getMetadata).toHaveBeenCalledTimes(3);
      subscriptions[3].unsubscribe(); // d was still waiting for a place
      subscriptions[0].unsubscribe(); // a gives its place back
      expect(documents.getMetadata).toHaveBeenCalledTimes(3);
      loadOne('e');
      expect(documents.getMetadata).toHaveBeenCalledTimes(4);
    });
  });

  describe('the memory cap', () => {
    const ids = (n: number): string[] => Array.from({ length: n }, (_, i) => `shot-${i + 1}`);
    const loadAndRelease = (id: string): void => {
      loadOne(id);
      service.release(id);
    };

    it('never keeps more object URLs alive than the cap when nobody shows them', () => {
      ids(QA_THUMBNAIL.maxObjectUrls + 10).forEach(loadAndRelease);
      expect(service.aliveCount).toBe(QA_THUMBNAIL.maxObjectUrls);
      expect(revoked).toHaveLength(10);
    });

    it('revokes the least recently used picture first, and downloads it again when it comes back', () => {
      ids(QA_THUMBNAIL.maxObjectUrls).forEach(loadAndRelease);
      expect(revoked).toEqual([]);
      loadAndRelease('shot-1'); // used again: no longer the oldest
      loadAndRelease('extra');
      expect(revoked).toEqual(['blob:qa-2']);
      const calls = documents.getMetadata.mock.calls.length;
      loadAndRelease('shot-2'); // was evicted: downloaded again
      expect(documents.getMetadata.mock.calls.length).toBe(calls + 1);
      loadAndRelease('shot-1'); // never evicted: answered from the cache
      expect(documents.getMetadata.mock.calls.length).toBe(calls + 1);
    });

    it('never revokes a picture that a thumbnail is showing, even beyond the cap', () => {
      ids(QA_THUMBNAIL.maxObjectUrls + 5).forEach((id) => loadOne(id));
      expect(service.aliveCount).toBe(QA_THUMBNAIL.maxObjectUrls + 5);
      expect(revoked).toEqual([]);
      ids(QA_THUMBNAIL.maxObjectUrls + 5).forEach((id) => service.release(id));
      expect(service.aliveCount).toBe(QA_THUMBNAIL.maxObjectUrls);
      expect(revoked).toEqual(['blob:qa-1', 'blob:qa-2', 'blob:qa-3', 'blob:qa-4', 'blob:qa-5']);
    });

    it('counts how many thumbnails show the same picture', () => {
      ids(QA_THUMBNAIL.maxObjectUrls).forEach(loadAndRelease);
      loadOne('shot-1');
      loadOne('shot-1'); // two thumbnails show shot-1
      service.release('shot-1');
      loadAndRelease('extra'); // evicts the oldest unused one, not shot-1
      expect(revoked).not.toContain('blob:qa-1');
      service.release('shot-1');
      loadAndRelease('extra-2'); // now shot-1 is unused too and is the most recently used: the others go first
      expect(revoked).not.toContain('blob:qa-1');
    });

    it('ignores a release for a picture it does not hold, and a release too many', () => {
      service.release('unknown');
      loadOne('shot-1');
      service.release('shot-1');
      service.release('shot-1');
      expect(service.aliveCount).toBe(1);
      expect(revoked).toEqual([]);
    });
  });

  it('gives every object URL back when the page goes, and loads nothing afterwards', () => {
    loadOne('shot-1');
    loadOne('shot-2');
    service.ngOnDestroy();
    expect(revoked.sort()).toEqual(['blob:qa-1', 'blob:qa-2']);
    expect(loadOne('shot-3').url).toBeNull();
    expect(documents.getMetadata).toHaveBeenCalledTimes(2);
  });
});

describe('QaEvidenceThumbnail', () => {
  const fakeUrl = 'blob:fake' as unknown as SafeUrl;
  let load$: Subject<SafeUrl | null>;
  const thumbnails = { load: vi.fn((_id: string): Observable<SafeUrl | null> => load$), release: vi.fn() };
  let fixture: ComponentFixture<QaEvidenceThumbnail>;

  class FakeObserver {
    static instances: FakeObserver[] = [];
    disconnected = false;
    observed: Element[] = [];
    constructor(public callback: (entries: Array<Partial<IntersectionObserverEntry>>) => void, public options?: IntersectionObserverInit) {
      FakeObserver.instances.push(this);
    }
    observe(el: Element): void {
      this.observed.push(el);
    }
    disconnect(): void {
      this.disconnected = true;
    }
  }

  function create(kind = 'FAILURE', disabled = false): { opened: number } {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [QaEvidenceThumbnail, TranslateModule.forRoot()],
      providers: [{ provide: QaEvidenceThumbnailService, useValue: thumbnails }],
    });
    fixture = TestBed.createComponent(QaEvidenceThumbnail);
    const counter = { opened: 0 };
    fixture.componentInstance.documentId = 'shot-1';
    fixture.componentInstance.kind = kind;
    fixture.componentInstance.disabled = disabled;
    fixture.componentInstance.opened.subscribe(() => counter.opened++);
    fixture.detectChanges();
    return counter;
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const q = (selector: string): HTMLElement | null => el().querySelector(selector);

  beforeEach(() => {
    vi.clearAllMocks();
    load$ = new Subject<SafeUrl | null>();
    thumbnails.load.mockImplementation(() => load$);
    FakeObserver.instances = [];
    vi.unstubAllGlobals();
  });

  afterEach(() => vi.unstubAllGlobals());

  describe('a browser without IntersectionObserver', () => {
    it('loads at once and shows a skeleton while it waits', () => {
      create();
      expect(thumbnails.load).toHaveBeenCalledWith('shot-1');
      const skeleton = q('.qa-thumb__skeleton');
      expect(skeleton?.getAttribute('role')).toBe('status');
      expect(skeleton?.getAttribute('aria-label')).toBe(QA_THUMBNAIL_KEYS.loading);
      expect(skeleton?.style.width).toBe(`${QA_THUMBNAIL.width}px`);
      expect(skeleton?.style.height).toBe(`${QA_THUMBNAIL.height}px`);
    });

    it('shows the picture as a real button that opens the viewer', () => {
      const counter = create();
      load$.next(fakeUrl);
      fixture.detectChanges();
      const button = q('button.qa-thumb__button') as HTMLButtonElement;
      expect(button.tagName).toBe('BUTTON');
      expect(button.type).toBe('button');
      expect(button.getAttribute('aria-label')).toBe('qaDashboard.evidence.failure');
      const img = button.querySelector('img') as HTMLImageElement;
      expect(img.getAttribute('src')).toBe('blob:fake');
      expect(img.getAttribute('alt')).toBe(QA_THUMBNAIL_KEYS.alt);
      expect(img.style.maxWidth).toBe(`${QA_THUMBNAIL.width}px`);
      expect(img.style.maxHeight).toBe(`${QA_THUMBNAIL.height}px`);
      button.click();
      expect(counter.opened).toBe(1);
    });

    it('does not open the viewer while another screenshot is being opened', () => {
      const counter = create('FAILURE', true);
      load$.next(fakeUrl);
      fixture.detectChanges();
      const button = q('button.qa-thumb__button') as HTMLButtonElement;
      expect(button.disabled).toBe(true);
      fixture.componentInstance.open();
      expect(counter.opened).toBe(0);
    });

    it('falls back to the old button when the file is too big or not an image', () => {
      const counter = create();
      load$.next(null);
      fixture.detectChanges();
      expect(q('img')).toBeNull();
      const link = q('button.qa-evidence-link') as HTMLButtonElement;
      expect(link.textContent).toContain('qaDashboard.evidence.failure');
      link.click();
      expect(counter.opened).toBe(1);
    });

    it('falls back to the old button, without any message, when the download fails', () => {
      create('SCREEN');
      load$.error(new Error('no'));
      fixture.detectChanges();
      expect(q('img')).toBeNull();
      expect(q('button.qa-evidence-link')?.textContent).toContain('qaDashboard.evidence.screen');
      expect(q('button.qa-evidence-link')?.getAttribute('data-kind')).toBe('SCREEN');
    });

    it('stops listening when it is destroyed', () => {
      create();
      expect(load$.observed).toBe(true);
      fixture.destroy();
      expect(load$.observed).toBe(false);
    });
  });

  describe('a browser with IntersectionObserver', () => {
    beforeEach(() => vi.stubGlobal('IntersectionObserver', FakeObserver));

    it('waits until the picture scrolls into view before it downloads anything', () => {
      create();
      expect(thumbnails.load).not.toHaveBeenCalled();
      expect(q('.qa-thumb__skeleton')).not.toBeNull();
      const [near, far] = FakeObserver.instances;
      expect(near.observed).toEqual([fixture.nativeElement]);
      expect(far.observed).toEqual([fixture.nativeElement]);
      expect(near.options?.rootMargin).toBe(QA_THUMBNAIL.rootMargin);
      expect(far.options?.rootMargin).toBe(QA_THUMBNAIL.unloadMargin);

      near.callback([{ isIntersecting: false }]);
      expect(thumbnails.load).not.toHaveBeenCalled();

      near.callback([{ isIntersecting: true }]);
      expect(thumbnails.load).toHaveBeenCalledTimes(1);
    });

    it('shows the final state of a passed scenario as a picture just like the failure one', () => {
      create('SCREEN');
      FakeObserver.instances[0].callback([{ isIntersecting: true }]);
      load$.next(fakeUrl);
      fixture.detectChanges();
      const button = q('button.qa-thumb__button') as HTMLButtonElement;
      expect(button.getAttribute('data-kind')).toBe('SCREEN');
      expect(button.getAttribute('aria-label')).toBe('qaDashboard.evidence.screen');
    });

    it('decodes the picture without blocking the scrolling', () => {
      create();
      FakeObserver.instances[0].callback([{ isIntersecting: true }]);
      load$.next(fakeUrl);
      fixture.detectChanges();
      const img = q('img') as HTMLImageElement;
      expect(img.getAttribute('loading')).toBe('lazy');
      expect(img.getAttribute('decoding')).toBe('async');
    });

    it('gives the picture back when the row has scrolled far away, and loads it again when it returns', () => {
      create();
      const [near, far] = FakeObserver.instances;
      near.callback([{ isIntersecting: true }]);
      load$.next(fakeUrl);
      fixture.detectChanges();
      expect(q('img')).not.toBeNull();

      far.callback([{ isIntersecting: true }]);
      expect(thumbnails.release).not.toHaveBeenCalled();
      far.callback([]);
      expect(thumbnails.release).not.toHaveBeenCalled();

      far.callback([{ isIntersecting: false }]);
      fixture.detectChanges();
      expect(thumbnails.release).toHaveBeenCalledWith('shot-1');
      expect(q('img')).toBeNull();
      expect(q('.qa-thumb__skeleton')).not.toBeNull();

      load$ = new Subject<SafeUrl | null>();
      near.callback([{ isIntersecting: true }]);
      expect(thumbnails.load).toHaveBeenCalledTimes(2);
      load$.next(fakeUrl);
      fixture.detectChanges();
      expect(q('img')?.getAttribute('src')).toBe('blob:fake');
    });

    it('stops a download that is still going when the row scrolls far away', () => {
      create();
      const [near, far] = FakeObserver.instances;
      near.callback([{ isIntersecting: true }]);
      expect(load$.observed).toBe(true);
      far.callback([{ isIntersecting: false }]);
      expect(load$.observed).toBe(false);
      expect(thumbnails.release).not.toHaveBeenCalled();
      expect(q('.qa-thumb__skeleton')).not.toBeNull();
    });

    it('leaves the old button alone when the row scrolls far away', () => {
      create();
      const [near, far] = FakeObserver.instances;
      near.callback([{ isIntersecting: true }]);
      load$.next(null);
      fixture.detectChanges();
      far.callback([{ isIntersecting: false }]);
      fixture.detectChanges();
      expect(q('button.qa-evidence-link')).not.toBeNull();
      expect(thumbnails.release).not.toHaveBeenCalled();
    });

    it('gives its picture back when it is destroyed while showing one, but not while still waiting or loading', () => {
      create();
      FakeObserver.instances[0].callback([{ isIntersecting: true }]);
      load$.next(fakeUrl);
      fixture.destroy();
      expect(thumbnails.release).toHaveBeenCalledTimes(1);
      expect(thumbnails.release).toHaveBeenCalledWith('shot-1');

      thumbnails.release.mockClear();
      create();
      fixture.destroy();
      create();
      FakeObserver.instances[FakeObserver.instances.length - 2].callback([{ isIntersecting: true }]);
      fixture.destroy();
      expect(thumbnails.release).not.toHaveBeenCalled();
    });

    it('downloads only once even when the observer fires twice', () => {
      create();
      const observer = FakeObserver.instances[0];
      observer.callback([{ isIntersecting: true }]);
      observer.callback([{ isIntersecting: true }]);
      expect(thumbnails.load).toHaveBeenCalledTimes(1);
    });

    it('lets go of the observer when it is destroyed before it was ever seen', () => {
      create();
      const [near, far] = FakeObserver.instances;
      fixture.destroy();
      expect(near.disconnected).toBe(true);
      expect(far.disconnected).toBe(true);
      expect(thumbnails.load).not.toHaveBeenCalled();
    });
  });
});

describe('the evidence texts', () => {
  const languages: Record<string, { qaDashboard: { evidence: Record<string, string> } }> = { en, hi, ta, te, kn, ml, ar } as never;

  it.each(Object.keys(languages))('has the preview texts in %s with the same {{kind}} parameter', (lang) => {
    const evidence = languages[lang].qaDashboard.evidence;
    expect(evidence['thumbnailAlt']).toBeTruthy();
    expect(evidence['thumbnailAlt']).toContain('{{kind}}');
    expect(evidence['thumbnailLoading']).toBeTruthy();
    expect(evidence['thumbnailLoading']).not.toContain('{{');
  });

  it.each(['hi', 'ta', 'te', 'kn', 'ml', 'ar'])('does not leave %s as a copy of the English text', (lang) => {
    const evidence = languages[lang].qaDashboard.evidence;
    expect(evidence['thumbnailAlt']).not.toBe(languages['en'].qaDashboard.evidence['thumbnailAlt']);
    expect(evidence['thumbnailLoading']).not.toBe(languages['en'].qaDashboard.evidence['thumbnailLoading']);
  });
});
