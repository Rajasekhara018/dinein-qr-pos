import { TestBed } from '@angular/core/testing';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatSelectModule } from '@angular/material/select';
import { firstValueFrom, of } from 'rxjs';
import { HqAppDialogService } from './hq-app-dialog.service';
import { HqAppDialog } from './hq-app-dialog/hq-app-dialog';

/** Waits (condition-based) until the dialog panel is rendered in the overlay. */
async function waitForPanel(): Promise<HTMLElement> {
  let panel: HTMLElement | null = null;
  await vi.waitFor(() => {
    TestBed.tick();
    panel = document.querySelector('.app-dialog-panel .app-dialog');
    if (!panel) throw new Error('dialog not open yet');
  }, { timeout: 10000, interval: 10 });
  return panel!;
}

describe('HqAppDialogService', () => {
  describe('with a stubbed MatDialog', () => {
    let open: ReturnType<typeof vi.fn>;
    let service: HqAppDialogService;

    function closeWith(result: unknown): void {
      open.mockReturnValue({ afterClosed: () => of(result) });
    }

    beforeEach(() => {
      open = vi.fn();
      TestBed.configureTestingModule({ providers: [{ provide: MatDialog, useValue: { open } }] });
      service = TestBed.inject(HqAppDialogService);
    });

    it('opens HqAppDialog with the standard panel config and the data', async () => {
      closeWith(true);
      const data = { title: 'Delete?' };
      await firstValueFrom(service.confirm(data));
      expect(open).toHaveBeenCalledWith(HqAppDialog, {
        width: '460px',
        maxWidth: '94vw',
        autoFocus: true,
        restoreFocus: true,
        panelClass: 'app-dialog-panel',
        data,
      });
    });

    it('confirm resolves true only for a true result', async () => {
      closeWith(true);
      expect(await firstValueFrom(service.confirm({ title: 'A' }))).toBe(true);
      closeWith(null);
      expect(await firstValueFrom(service.confirm({ title: 'A' }))).toBe(false);
      closeWith(undefined);
      expect(await firstValueFrom(service.confirm({ title: 'A' }))).toBe(false);
      closeWith({ a: 'x' });
      expect(await firstValueFrom(service.confirm({ title: 'A' }))).toBe(false);
    });

    it('form resolves the values, or null when cancelled or closed with a bare true', async () => {
      closeWith({ name: 'Asha' });
      expect(await firstValueFrom(service.form({ title: 'F', fields: [] }))).toEqual({ name: 'Asha' });
      closeWith(null);
      expect(await firstValueFrom(service.form({ title: 'F' }))).toBeNull();
      closeWith(true);
      expect(await firstValueFrom(service.form({ title: 'F' }))).toBeNull();
    });

    it('prompt wraps the field into fields and resolves its value as a string', async () => {
      const field = { key: 'reason', label: 'Reason' };
      closeWith({ reason: 'late' });
      expect(await firstValueFrom(service.prompt({ title: 'Why?', field }))).toBe('late');
      expect(open.mock.calls[0][1].data).toEqual({ title: 'Why?', field, fields: [field] });
    });

    it('prompt stringifies checkbox and multiselect values, empties a missing key and nulls a cancel', async () => {
      closeWith({ ok: true });
      expect(await firstValueFrom(service.prompt({ title: 'T', field: { key: 'ok', label: 'Ok', type: 'checkbox' } }))).toBe('true');
      closeWith({ roles: ['a', 'b'] });
      expect(await firstValueFrom(service.prompt({ title: 'T', field: { key: 'roles', label: 'R', type: 'multiselect' } }))).toBe('a,b');
      closeWith({ other: 'x' });
      expect(await firstValueFrom(service.prompt({ title: 'T', field: { key: 'missing', label: 'M' } }))).toBe('');
      closeWith(null);
      expect(await firstValueFrom(service.prompt({ title: 'T', field: { key: 'a', label: 'A' } }))).toBeNull();
    });
  });

  describe('with the real MatDialog', () => {
    beforeEach(() => {
      TestBed.configureTestingModule({
        imports: [CommonModule, FormsModule, MatDialogModule, MatCheckboxModule, MatSelectModule],
        declarations: [HqAppDialog],
      });
    });

    afterEach(() => {
      TestBed.inject(MatDialog).closeAll();
      document.querySelectorAll('.cdk-overlay-container').forEach((n) => n.remove());
    });

    it('renders the dialog in the overlay and resolves the typed prompt value on OK', async () => {
      const service = TestBed.inject(HqAppDialogService);
      const result = firstValueFrom(service.prompt({ title: 'Your name', field: { key: 'name', label: 'Name', required: true } }));
      await waitForPanel();

      const panel = document.querySelector('.app-dialog-panel') as HTMLElement;
      expect(panel).not.toBeNull();
      expect(panel.querySelector('h2')?.textContent).toContain('Your name');
      const confirm = panel.querySelector('.btn-confirm') as HTMLButtonElement;
      expect(confirm.disabled).toBe(true);

      const input = panel.querySelector('input') as HTMLInputElement;
      input.value = 'Meera';
      input.dispatchEvent(new Event('input'));
      TestBed.tick();
      expect(confirm.disabled).toBe(false);
      confirm.click();
      expect(await result).toBe('Meera');
    });

    it('resolves false when the dialog is cancelled', async () => {
      const service = TestBed.inject(HqAppDialogService);
      const result = firstValueFrom(service.confirm({ title: 'Leave?' }));
      await waitForPanel();
      (document.querySelector('.app-dialog-panel .btn-cancel') as HTMLButtonElement).click();
      expect(await result).toBe(false);
    });
  });
});
