import { NO_ERRORS_SCHEMA } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HQ_CONFIG, HqDocumentService, HqPrivilegeAccessService, HqSharedLibModule, HqTimezoneService } from 'hq-shared-lib';
import { EMPTY, of } from 'rxjs';
import { vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { OrganizationContextService } from '../../shared/services/organization-context-service';
import { OrgImportMode } from '../org-structure.models';
import { OrgStructureService } from '../org-structure.service';
import { OrgImport } from './org-import';

/**
 * The files each step offers, written out in full: they must stay equal to docs/samples (org-ladders-import-sample.csv,
 * org-departments-import-sample.csv, org-import-sample.csv), so a change to one without the other fails here.
 */
const EXPECTED: Record<OrgImportMode, { template: string[]; sample: string[]; templateFile: string; sampleFile: string }> = {
  LADDERS: {
    template: ['ladder,position,rank,managerial'],
    sample: ['ladder,position,rank,managerial', 'Standard,Head,3,yes', 'Standard,Lead,2,yes', 'Standard,Member,1,no'],
    templateFile: 'ladders-template.csv',
    sampleFile: 'org-ladders-import-sample.csv',
  },
  DEPARTMENTS: {
    template: ['code,name,parent_code,unit_type,ladder'],
    sample: [
      'code,name,parent_code,unit_type,ladder',
      'EXEC,Executive Office,,DIVISION,Standard',
      'TECH,Technology,EXEC,DIVISION,Standard',
      'HR,Human Resources,EXEC,DEPARTMENT,Standard',
      'SALES,Sales,EXEC,DEPARTMENT,Standard',
      'BACKEND,Backend Engineering,TECH,TEAM,Standard',
      'FRONTEND,Frontend Engineering,TECH,TEAM,Standard',
    ],
    templateFile: 'departments-template.csv',
    sampleFile: 'org-departments-import-sample.csv',
  },
  EMPLOYEES: {
    template: ['employee_code,full_name,department_code,position_name,email,phone,manager_employee_code,joined_on,is_head'],
    sample: [
      'employee_code,full_name,department_code,position_name,email,phone,manager_employee_code,joined_on,is_head',
      'E1001,Rajesh Kumar,EXEC,Head,rajesh.kumar@example.com,,,2022-04-01,yes',
      'E1002,Priya Sharma,TECH,Head,priya.sharma@example.com,,E1001,2022-06-15,yes',
      'E1003,Arjun Reddy,BACKEND,Lead,arjun.reddy@example.com,,E1002,2023-01-09,yes',
      'E1004,Sneha Iyer,BACKEND,Member,sneha.iyer@example.com,,E1003,2023-07-03,',
      'E1005,Karthik Nair,BACKEND,Member,karthik.nair@example.com,,E1003,2024-02-12,',
      'E1006,Divya Menon,FRONTEND,Lead,divya.menon@example.com,,E1002,2023-03-20,yes',
      'E1007,Rohit Verma,FRONTEND,Member,rohit.verma@example.com,,E1006,2024-05-06,',
      'E1008,Anita Desai,SALES,Head,anita.desai@example.com,,E1001,2022-09-01,yes',
      'E1009,Vikram Singh,SALES,Member,vikram.singh@example.com,,E1008,2023-11-13,',
      'E1010,Meera Pillai,SALES,Member,meera.pillai@example.com,,E1008,2024-08-26,',
      'E1011,Suresh Babu,HR,Head,suresh.babu@example.com,,E1001,2022-10-17,yes',
      'E1012,Lakshmi Rao,HR,Member,lakshmi.rao@example.com,,E1011,2025-01-08,',
    ],
    templateFile: 'employees-template.csv',
    sampleFile: 'org-import-sample.csv',
  },
};

describe('OrgImport: the template and sample downloads of every step', () => {
  const api = new Proxy({}, { get: () => vi.fn(() => of([])) });
  const access = { changes$: EMPTY, can: () => true };

  function create(mode: OrgImportMode): OrgImport {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [OrgImport],
      imports: [TranslateModule.forRoot(), HqSharedLibModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: OrgStructureService, useValue: api },
        { provide: NotificationService, useValue: { error: vi.fn(), success: vi.fn() } },
        { provide: AppDialogService, useValue: {} },
        { provide: OrganizationContextService, useValue: { currentName$: of('Acme') } },
        { provide: HqTimezoneService, useValue: { formatLocale: (v: string) => v } },
        { provide: HqPrivilegeAccessService, useValue: access },
        { provide: HqDocumentService, useValue: {} },
        { provide: HQ_CONFIG, useValue: {} },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => 'ladders' } } } },
      ],
    });
    const fixture = TestBed.createComponent(OrgImport);
    fixture.detectChanges();
    fixture.componentInstance.setMode(mode);
    return fixture.componentInstance;
  }

  async function download(action: (c: OrgImport) => void, mode: OrgImportMode): Promise<{ text: string; name: string; type: string }> {
    const c = create(mode);
    const blobs: Blob[] = [];
    const names: string[] = [];
    URL.createObjectURL = vi.fn((b: Blob) => { blobs.push(b); return 'blob:x'; });
    URL.revokeObjectURL = vi.fn();
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) { names.push(this.download); });
    action(c);
    click.mockRestore();
    expect(blobs).toHaveLength(1);
    return { text: await blobs[0].text(), name: names[0], type: blobs[0].type };
  }

  const lines = (text: string) => text.split('\r\n');

  (['LADDERS', 'DEPARTMENTS', 'EMPLOYEES'] as const).forEach((mode) => {
    it(`${mode}: the template is the header line only`, async () => {
      const file = await download((c) => c.downloadTemplate(), mode);
      expect(lines(file.text)).toEqual([...EXPECTED[mode].template, '']);
      expect(file.name).toBe(EXPECTED[mode].templateFile);
      expect(file.type).toContain('text/csv');
    });

    it(`${mode}: the sample is the header and the rows of its file in docs/samples`, async () => {
      const file = await download((c) => c.downloadSample(), mode);
      expect(lines(file.text)).toEqual([...EXPECTED[mode].sample, '']);
      expect(file.name).toBe(EXPECTED[mode].sampleFile);
    });
  });

  it('keeps the three samples consistent: ladder names, department codes and job titles agree', () => {
    const column = (rows: string[], at: number) => rows.slice(1).map((row) => row.split(',')[at]);
    const ladders = new Set(column(EXPECTED.LADDERS.sample, 0));
    const positions = new Set(column(EXPECTED.LADDERS.sample, 1));
    const departments = new Set(column(EXPECTED.DEPARTMENTS.sample, 0));
    column(EXPECTED.DEPARTMENTS.sample, 4).forEach((ladder) => expect(ladders.has(ladder), ladder).toBe(true));
    column(EXPECTED.EMPLOYEES.sample, 2).forEach((department) => expect(departments.has(department), department).toBe(true));
    column(EXPECTED.EMPLOYEES.sample, 3).forEach((position) => expect(positions.has(position), position).toBe(true));
  });

  it('builds every download in the browser: no call reaches the server', async () => {
    const calls = vi.fn();
    const watching = new Proxy({}, { get: (_t, name) => (...args: unknown[]) => { calls(name, args); return of([]); } });
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [OrgImport],
      imports: [TranslateModule.forRoot(), HqSharedLibModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: OrgStructureService, useValue: watching },
        { provide: NotificationService, useValue: {} }, { provide: AppDialogService, useValue: {} },
        { provide: OrganizationContextService, useValue: { currentName$: of('Acme') } },
        { provide: HqTimezoneService, useValue: { formatLocale: (v: string) => v } },
        { provide: HqPrivilegeAccessService, useValue: access }, { provide: HqDocumentService, useValue: {} }, { provide: HQ_CONFIG, useValue: {} },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => 'ladders' } } } },
      ],
    });
    const c = TestBed.createComponent(OrgImport);
    c.detectChanges();
    const before = calls.mock.calls.length;
    URL.createObjectURL = vi.fn(() => 'blob:x');
    URL.revokeObjectURL = vi.fn();
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    (['LADDERS', 'DEPARTMENTS', 'EMPLOYEES'] as const).forEach((mode) => {
      c.componentInstance.setMode(mode);
      const afterSwitch = calls.mock.calls.length;
      c.componentInstance.downloadTemplate();
      c.componentInstance.downloadSample();
      expect(calls.mock.calls.length, mode).toBe(afterSwitch);
    });
    click.mockRestore();
    expect(calls.mock.calls.length).toBeGreaterThan(before);
  });
});
