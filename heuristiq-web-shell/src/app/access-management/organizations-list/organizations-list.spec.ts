import { TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { EMPTY, of } from 'rxjs';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { SearchApiService } from '../../shared/services/search-api-service';
import { UamService } from '../../shared/services/uam-service';
import { AdminFormService } from '../admin-form.service';
import { OrganizationsList } from './organizations-list';

const search = { searchRows: vi.fn() };
let granted: Set<string> | null = null;
const access = { changes$: EMPTY, can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)) };

function create() {
  const fixture = TestBed.createComponent(OrganizationsList);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
}

describe('OrganizationsList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    search.searchRows.mockReturnValue(of({ content: [{ id: 'o1', code: 'ACME', name: 'Acme', status: 'ACTIVE' }], totalElements: 1, totalPages: 1 }));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [OrganizationsList],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      providers: [
        provideRouter([]),
        { provide: SearchApiService, useValue: search },
        { provide: UamService, useValue: { remove: vi.fn() } },
        { provide: AdminFormService, useValue: { create: vi.fn(), edit: vi.fn() } },
        { provide: AppDialogService, useValue: { confirm: vi.fn() } },
        { provide: NotificationService, useValue: { success: vi.fn(), error: vi.fn() } },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
  });

  it('offers no legal profile action on the rows', () => {
    const { el } = create();

    expect(el.querySelector('[data-testid="open-legal-profile"]')).toBeNull();
    expect(el.querySelector('.bi-building-check')).toBeNull();
  });
});
