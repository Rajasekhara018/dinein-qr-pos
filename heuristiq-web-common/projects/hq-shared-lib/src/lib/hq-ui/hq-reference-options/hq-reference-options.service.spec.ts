import { TestBed } from '@angular/core/testing';
import { Observable, Subject, of, throwError } from 'rxjs';
import { HQ_REFERENCE_FETCHER, HqReferenceFetcher, HqReferenceList, HqReferencePage } from './hq-reference-options.model';
import { HqReferenceOptionsService, hqReferenceValues } from './hq-reference-options.service';

describe('HqReferenceOptionsService', () => {
  let fetcher: ReturnType<typeof vi.fn<HqReferenceFetcher>>;

  function service(): HqReferenceOptionsService {
    TestBed.configureTestingModule({ providers: [{ provide: HQ_REFERENCE_FETCHER, useValue: fetcher }] });
    return TestBed.inject(HqReferenceOptionsService);
  }

  const page = (...values: string[]): HqReferencePage => ({ content: values.map((value) => ({ value })) });

  beforeEach(() => {
    fetcher = vi.fn<HqReferenceFetcher>().mockReturnValue(of(page('Hindu', 'Muslim')));
  });

  it('maps a page to its active, trimmed, distinct values in order', () => {
    expect(
      hqReferenceValues({
        content: [{ value: ' Hindu ' }, { value: 'hindu' }, { value: 'Off', active: false }, { value: '' }, { value: 'Sikh', active: true }],
      }),
    ).toEqual(['Hindu', 'Sikh']);
    expect(hqReferenceValues(null)).toEqual([]);
    expect(hqReferenceValues({ content: undefined as never })).toEqual([]);
  });

  it('reads a list once, shows it loaded, and keeps it for the session', () => {
    const s = service();
    const seen: HqReferenceList[] = [];
    s.list$('RELIGION').subscribe((list) => seen.push(list));
    s.list$('RELIGION').subscribe();

    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(fetcher).toHaveBeenCalledWith('RELIGION', null);
    expect(seen.at(-1)).toEqual({ status: 'loaded', values: ['Hindu', 'Muslim'] });
    expect(s.snapshot('RELIGION').values).toEqual(['Hindu', 'Muslim']);
    expect(s.list$('RELIGION')).toBe(s.list$('RELIGION'));
  });

  it('is loading while the read is in flight', () => {
    const pending = new Subject<HqReferencePage>();
    fetcher.mockReturnValue(pending as Observable<HqReferencePage>);
    const s = service();

    expect(s.snapshot('COUNTRY')).toEqual({ status: 'loading', values: [] });
    pending.next(page('India'));
    expect(s.snapshot('COUNTRY')).toEqual({ status: 'loaded', values: ['India'] });
  });

  it('keeps a list per parent: the states of one country are not the states of another', () => {
    fetcher.mockImplementation((_category, parent) => of(page(parent === 'India' ? 'Goa' : 'Bagmati')));
    const s = service();

    expect(s.snapshot('STATE', 'India').values).toEqual(['Goa']);
    expect(s.snapshot('STATE', 'Nepal').values).toEqual(['Bagmati']);
    expect(s.snapshot('STATE', ' india ').values).toEqual(['Goa']);
    expect(fetcher).toHaveBeenCalledTimes(2);
    expect(fetcher).toHaveBeenCalledWith('STATE', 'India');
  });

  it('a failed read is reported as failed, is not kept, and retry asks again', () => {
    fetcher.mockReturnValueOnce(throwError(() => new Error('401')));
    const s = service();

    expect(s.snapshot('EDUCATION')).toEqual({ status: 'failed', values: [] });
    s.retry('EDUCATION');
    expect(s.snapshot('EDUCATION')).toEqual({ status: 'loaded', values: ['Hindu', 'Muslim'] });
    expect(fetcher).toHaveBeenCalledTimes(2);

    s.retry('EDUCATION');
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it('exposes the empty list a control shows while its parent is not chosen', () => {
    expect(service().empty).toEqual({ status: 'loaded', values: [] });
  });
});
