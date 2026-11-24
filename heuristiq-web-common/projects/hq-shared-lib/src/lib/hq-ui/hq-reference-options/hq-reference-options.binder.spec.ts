import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HqReferenceBinder } from './hq-reference-options.binder';
import { HQ_REFERENCE_FETCHER, HqReferenceFetcher, HqReferenceList } from './hq-reference-options.model';
import { HqReferenceOptionsService } from './hq-reference-options.service';

describe('HqReferenceBinder', () => {
  const answers: Record<string, unknown> = {};
  let fetcher: ReturnType<typeof vi.fn<HqReferenceFetcher>>;
  let binder: HqReferenceBinder;

  const last = (key: string): HqReferenceList => {
    let value!: HqReferenceList;
    binder.list$(key).subscribe((list) => (value = list)).unsubscribe();
    return value;
  };

  beforeEach(() => {
    Object.keys(answers).forEach((key) => delete answers[key]);
    fetcher = vi.fn<HqReferenceFetcher>().mockImplementation((category, parent) =>
      of({ content: [{ value: parent ? `${category} of ${parent}` : category }] }));
    TestBed.configureTestingModule({ providers: [{ provide: HQ_REFERENCE_FETCHER, useValue: fetcher }] });
    binder = new HqReferenceBinder(
      TestBed.inject(HqReferenceOptionsService),
      { religion: 'RELIGION', country: 'COUNTRY', state: 'STATE', city: 'CITY' },
      { state: 'country', city: 'state' },
      (key) => answers[key],
    );
  });

  it('knows which fields are reference fields and which field a child waits for', () => {
    expect(binder.has('religion')).toBe(true);
    expect(binder.has('email')).toBe(false);
    expect(binder.parentKey('state')).toBe('country');
    expect(binder.parentKey('religion')).toBeUndefined();
  });

  it('reads a flat list straight away', () => {
    expect(last('religion')).toEqual({ status: 'loaded', values: ['RELIGION'] });
    expect(fetcher).toHaveBeenCalledWith('RELIGION', null);
  });

  it('gives a child the empty list, and does not read anything, until its parent is chosen', () => {
    expect(last('state')).toEqual({ status: 'loaded', values: [] });
    expect(fetcher).not.toHaveBeenCalled();
    expect(binder.list$('state')).toBe(binder.list$('city'));

    answers['country'] = ' India ';
    expect(binder.parentValue('state')).toBe('India');
    expect(last('state').values).toEqual(['STATE of India']);
    answers['state'] = 'Goa';
    expect(last('city').values).toEqual(['CITY of Goa']);
  });

  it('has no parent value for a field without a parent, or for a blank answer', () => {
    expect(binder.parentValue('religion')).toBeNull();
    answers['country'] = '   ';
    expect(binder.parentValue('state')).toBeNull();
    answers['country'] = null;
    expect(binder.parentValue('state')).toBeNull();
  });

  it('retries the list of a field under its current parent after a failed read', () => {
    answers['country'] = 'India';
    fetcher.mockReturnValueOnce(throwError(() => new Error('401')));
    expect(last('state')).toEqual({ status: 'failed', values: [] });

    binder.retry('state');

    expect(fetcher).toHaveBeenLastCalledWith('STATE', 'India');
    expect(last('state').status).toBe('loaded');
  });
});
