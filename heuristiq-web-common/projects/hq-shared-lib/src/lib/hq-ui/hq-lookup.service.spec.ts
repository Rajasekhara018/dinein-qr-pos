import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { HqLookupConfig, HqLookupDialog } from './hq-lookup-dialog/hq-lookup-dialog';
import { HqLookupService } from './hq-lookup.service';
import { HQ_LOOKUP_DEFAULT_TEXTS } from './hq-lookup.texts';

describe('HqLookupService', () => {
  type Item = { id: string };
  const config: HqLookupConfig<Item> = {
    title: 'Pick',
    columns: [{ key: 'id', label: 'Id' }],
    idOf: (i) => i.id,
    fetch: () => of({ items: [] }),
  };

  function setup(answer: Item[] | null) {
    const open = vi.fn(() => ({ afterClosed: () => of(answer) }));
    TestBed.configureTestingModule({ providers: [{ provide: MatDialog, useValue: { open } }] });
    return { open, service: TestBed.inject(HqLookupService) };
  }

  it('opens the lookup dialog with the config as data and the shared panel options', () => {
    const { open, service } = setup(null);
    service.open(config).subscribe();
    expect(open).toHaveBeenCalledWith(HqLookupDialog, {
      width: '920px',
      maxWidth: '96vw',
      maxHeight: '92vh',
      autoFocus: false,
      restoreFocus: true,
      panelClass: 'app-dialog-panel',
      data: config,
    });
  });

  it('resolves the chosen records', () => {
    const { service } = setup([{ id: 'a' }]);
    const result = vi.fn();
    service.open(config).subscribe(result);
    expect(result).toHaveBeenCalledWith([{ id: 'a' }]);
  });

  it('resolves null when cancelled', () => {
    const { service } = setup(null);
    const result = vi.fn();
    service.open(config).subscribe(result);
    expect(result).toHaveBeenCalledWith(null);
  });
});

describe('HQ_LOOKUP_DEFAULT_TEXTS', () => {
  it('gives English words with the numbers filled in', () => {
    const t = HQ_LOOKUP_DEFAULT_TEXTS;
    expect(t.typeToSearch(2)).toBe('Type at least 2 characters to search.');
    expect(t.selected(4)).toBe('4 selected');
    expect([t.search(), t.noResults(), t.loadFailed(), t.retry(), t.clearSelection(), t.cancel(), t.select()]).toEqual([
      'Search...', 'No matching records.', 'Failed to load data.', 'Try again', 'Clear selection', 'Cancel', 'Select',
    ]);
  });
});
