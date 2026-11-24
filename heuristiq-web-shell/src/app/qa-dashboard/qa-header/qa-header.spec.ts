import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';

import { QA_DASHBOARD_PRIVILEGES } from '../qa-dashboard.constants';
import { accessMock } from '../qa-dashboard.fixtures';
import { QaHeader } from './qa-header';

describe('QaHeader', () => {
  let granted: Set<string> | null;
  let fixture: ComponentFixture<QaHeader>;
  let header: QaHeader;

  function create(inputs: Partial<QaHeader> = {}): void {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [QaHeader],
      imports: [TranslateModule.forRoot(), HqSharedLibModule],
      providers: [{ provide: HqPrivilegeAccessService, useValue: accessMock(granted) }],
    });
    fixture = TestBed.createComponent(QaHeader);
    header = fixture.componentInstance;
    Object.assign(header, inputs);
    fixture.detectChanges();
  }

  const q = (selector: string): HTMLButtonElement | null => (fixture.nativeElement as HTMLElement).querySelector(selector);

  beforeEach(() => (granted = null));

  it('shows the two workspaces and marks the chosen one', () => {
    create({ workspace: 'API' });
    const options = (fixture.nativeElement as HTMLElement).querySelectorAll('.qa-switch__option');
    expect(options).toHaveLength(2);
    expect(q('.qa-switch__option[data-workspace="API"]')?.getAttribute('aria-checked')).toBe('true');
    expect(q('.qa-switch__option[data-workspace="UI"]')?.getAttribute('aria-checked')).toBe('false');
  });

  it('tells the screen only when another workspace is chosen', () => {
    create({ workspace: 'UI' });
    const chosen: string[] = [];
    header.workspaceChange.subscribe((w) => chosen.push(w));
    q('.qa-switch__option[data-workspace="UI"]')?.click();
    q('.qa-switch__option[data-workspace="API"]')?.click();
    expect(chosen).toEqual(['API']);
  });

  it('hides the switch on a screen that is not per workspace', () => {
    create({ showWorkspace: false });
    expect(q('.qa-switch')).toBeNull();
  });

  it('asks the screen to refresh and disables the buttons while loading', () => {
    create();
    let refreshed = 0;
    header.refresh.subscribe(() => refreshed++);
    q('.qa-refresh')?.click();
    expect(refreshed).toBe(1);
    header.loading = true;
    fixture.detectChanges();
    expect(q('.qa-refresh')?.disabled).toBe(true);
  });

  it('has no export button unless the screen has one', () => {
    create({ exportPrivilege: null });
    expect(q('.qa-export')).toBeNull();
  });

  it('shows the export button to a user who holds its privilege and tells the screen when it is used', () => {
    create({ exportPrivilege: QA_DASHBOARD_PRIVILEGES.testCaseExport });
    let exported = 0;
    header.exported.subscribe(() => exported++);
    q('.qa-export')?.click();
    expect(exported).toBe(1);
  });

  it('hides the export button from a user without the privilege', () => {
    granted = new Set([QA_DASHBOARD_PRIVILEGES.testCaseList]);
    create({ exportPrivilege: QA_DASHBOARD_PRIVILEGES.testCaseExport });
    expect(q('.qa-export')).toBeNull();
    expect(q('.qa-refresh')).not.toBeNull();
  });
});
