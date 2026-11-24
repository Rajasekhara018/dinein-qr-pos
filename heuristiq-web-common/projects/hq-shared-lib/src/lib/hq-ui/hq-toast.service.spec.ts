import { TestBed } from '@angular/core/testing';
import { HqNotificationService } from './hq-notification.service';
import { HqToast, HqToastService } from './hq-toast.service';

describe('HqToastService', () => {
  let service: HqToastService;
  let current: HqToast[];

  beforeEach(() => {
    vi.useFakeTimers();
    service = TestBed.inject(HqToastService);
    service.toasts$.subscribe((t) => (current = t));
  });

  afterEach(() => vi.useRealTimers());

  it('starts empty', () => {
    expect(current).toEqual([]);
  });

  it('adds toasts with increasing ids and removes each after its own duration', () => {
    service.add('success', 'Saved', 1000);
    service.add('error', 'Failed', 3000);
    expect(current).toEqual([
      { id: 1, type: 'success', message: 'Saved', duration: 1000 },
      { id: 2, type: 'error', message: 'Failed', duration: 3000 },
    ]);

    vi.advanceTimersByTime(999);
    expect(current.length).toBe(2);
    vi.advanceTimersByTime(1);
    expect(current.map((t) => t.id)).toEqual([2]);
    vi.advanceTimersByTime(2000);
    expect(current).toEqual([]);
  });

  it('removes a toast on demand and ignores an unknown id', () => {
    service.add('info', 'A', 5000);
    service.add('info', 'B', 5000);
    service.remove(1);
    expect(current.map((t) => t.message)).toEqual(['B']);
    service.remove(42);
    expect(current.map((t) => t.message)).toEqual(['B']);
    // the timer of an already-removed toast does nothing harmful
    vi.advanceTimersByTime(5000);
    expect(current).toEqual([]);
  });
});

describe('HqNotificationService', () => {
  let notify: HqNotificationService;
  let add: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    add = vi.fn();
    TestBed.configureTestingModule({ providers: [{ provide: HqToastService, useValue: { add } }] });
    notify = TestBed.inject(HqNotificationService);
  });

  it('uses each type default duration', () => {
    notify.success('s');
    notify.error('e');
    notify.warning('w');
    notify.info('i');
    expect(add.mock.calls).toEqual([
      ['success', 's', 3000],
      ['error', 'e', 5000],
      ['warning', 'w', 4000],
      ['info', 'i', 3000],
    ]);
  });

  it('passes an explicit duration through', () => {
    notify.success('s', 10);
    notify.error('e', 20);
    notify.warning('w', 30);
    notify.info('i', 40);
    expect(add.mock.calls.map((c) => c[2])).toEqual([10, 20, 30, 40]);
  });
});
