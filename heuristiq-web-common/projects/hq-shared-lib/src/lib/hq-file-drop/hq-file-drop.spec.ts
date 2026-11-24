import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HqSharedLibModule } from '../hq-shared-lib.module';
import { HqFileDrop } from './hq-file-drop';
import { HQ_UPLOAD_DEFAULT_TEXTS, HQ_UPLOAD_TEXTS, HqUploadTexts } from './hq-upload.texts';

function file(name = 'a.csv', type = 'text/csv', size = 100): File {
  return new File(['x'.repeat(size)], name, { type });
}

function dragEvent(type: string, files: File[] = []): DragEvent {
  const event = new Event(type, { cancelable: true }) as DragEvent;
  Object.defineProperty(event, 'dataTransfer', { value: { files, dropEffect: 'none' } });
  return event;
}

function setup(texts?: HqUploadTexts): { fixture: ComponentFixture<HqFileDrop>; comp: HqFileDrop; zone: () => HTMLElement } {
  TestBed.configureTestingModule({
    imports: [HqSharedLibModule],
    providers: texts ? [{ provide: HQ_UPLOAD_TEXTS, useValue: texts }] : [],
  });
  const fixture = TestBed.createComponent(HqFileDrop);
  fixture.detectChanges();
  return { fixture, comp: fixture.componentInstance, zone: () => fixture.nativeElement.querySelector('.hq-drop') };
}

function q(fixture: ComponentFixture<unknown>, selector: string): HTMLElement | null {
  return fixture.nativeElement.querySelector(selector);
}

describe('HqFileDrop', () => {
  afterEach(() => vi.restoreAllMocks());

  describe('rendering', () => {
    it('shows the prompt, the choose button and no hint by default', () => {
      const { fixture } = setup();
      expect(q(fixture, '.hq-drop__text')?.textContent).toContain('Drop a file here, or');
      expect(q(fixture, '.hq-drop__choose')?.textContent).toContain('Choose a file');
      expect(q(fixture, '.hq-drop__hint')).toBeNull();
      expect(q(fixture, '.hq-drop__label')).toBeNull();
      expect(q(fixture, '.hq-drop__spinner')).toBeNull();
      expect(q(fixture, '[class*="mat-"], mat-icon, mat-spinner')).toBeNull();
    });

    it('shows label, required marker, hint, ids and the compact layout', () => {
      const { fixture } = setup();
      fixture.componentRef.setInput('label', 'Ladders (CSV)');
      fixture.componentRef.setInput('isRequired', true);
      fixture.componentRef.setInput('hints', 'CSV');
      fixture.componentRef.setInput('maxSizeMB', 10);
      fixture.componentRef.setInput('inputId', 'ladders');
      fixture.componentRef.setInput('compact', true);
      fixture.detectChanges();
      expect(q(fixture, '.hq-drop__label')?.textContent).toContain('Ladders (CSV)');
      expect(q(fixture, '.hq-drop__required')).not.toBeNull();
      expect(q(fixture, '.hq-drop__hint')?.textContent).toContain('CSV · up to 10 MB');
      expect(q(fixture, 'input#ladders')).not.toBeNull();
      expect(q(fixture, 'button#laddersBtn')).not.toBeNull();
      expect(q(fixture, '.hq-drop--compact')).not.toBeNull();
    });

    it('a chooseId overrides the button id', () => {
      const { fixture } = setup();
      fixture.componentRef.setInput('inputId', 'x');
      fixture.componentRef.setInput('chooseId', 'outerBtn');
      fixture.detectChanges();
      expect(q(fixture, 'button#outerBtn')).not.toBeNull();
    });

    it('multiple: plural wording, per-file size and the file cap', () => {
      const { fixture } = setup();
      fixture.componentRef.setInput('multiple', true);
      fixture.componentRef.setInput('hints', 'JPEG, PNG');
      fixture.componentRef.setInput('maxSizeMB', 5);
      fixture.componentRef.setInput('maxFiles', 3);
      fixture.detectChanges();
      expect(q(fixture, '.hq-drop__text')?.textContent).toContain('Drop files here, or');
      expect(q(fixture, '.hq-drop__choose')?.textContent).toContain('Choose files');
      expect(q(fixture, '.hq-drop__hint')?.textContent).toContain('JPEG, PNG · up to 5 MB each · at most 3 files');
      expect((q(fixture, 'input[type=file]') as HTMLInputElement).multiple).toBe(true);
    });

    it('hint falls back to the size alone, the cap alone, or nothing', () => {
      const { fixture, comp } = setup();
      fixture.componentRef.setInput('maxSizeMB', 2);
      expect(comp.limits).toBe('Up to 2 MB');
      fixture.componentRef.setInput('maxSizeMB', 0);
      fixture.componentRef.setInput('multiple', true);
      fixture.componentRef.setInput('maxFiles', 2);
      expect(comp.limits).toBe('at most 2 files');
      fixture.componentRef.setInput('maxFiles', 0);
      expect(comp.limits).toBe('');
    });

    it('loading shows the spinner and "Uploading…" and disables the button', () => {
      const { fixture } = setup();
      fixture.componentRef.setInput('loading', true);
      fixture.detectChanges();
      expect(q(fixture, '.hq-drop__spinner')).not.toBeNull();
      expect(q(fixture, '.hq-drop__text')?.textContent).toContain('Uploading…');
      expect((q(fixture, '.hq-drop__choose') as HTMLButtonElement).disabled).toBe(true);
      expect(q(fixture, '.hq-drop--busy')).not.toBeNull();
    });

    it('uses the app texts when HQ_UPLOAD_TEXTS is provided', () => {
      const { fixture } = setup({ ...HQ_UPLOAD_DEFAULT_TEXTS, dropOne: () => 'यहाँ फ़ाइल छोड़ें, या', chooseOne: () => 'फ़ाइल चुनें' });
      expect(q(fixture, '.hq-drop__text')?.textContent).toContain('यहाँ फ़ाइल छोड़ें, या');
      expect(q(fixture, '.hq-drop__choose')?.textContent).toContain('फ़ाइल चुनें');
    });
  });

  describe('choosing', () => {
    it('the button opens the file input', () => {
      const { fixture, comp } = setup();
      const click = vi.spyOn(comp.fileInput!.nativeElement, 'click').mockImplementation(() => {});
      (q(fixture, '.hq-drop__choose') as HTMLButtonElement).click();
      expect(click).toHaveBeenCalled();
    });

    it('emits browse instead when a wrapper listens to it', () => {
      const { comp } = setup();
      const browse = vi.fn();
      comp.browse.subscribe(browse);
      const click = vi.spyOn(comp.fileInput!.nativeElement, 'click').mockImplementation(() => {});
      comp.choose();
      expect(browse).toHaveBeenCalled();
      expect(click).not.toHaveBeenCalled();
    });

    it('does nothing while disabled or loading', () => {
      const { fixture, comp } = setup();
      const click = vi.spyOn(comp.fileInput!.nativeElement, 'click').mockImplementation(() => {});
      fixture.componentRef.setInput('disabled', true);
      comp.choose();
      fixture.componentRef.setInput('disabled', false);
      fixture.componentRef.setInput('loading', true);
      comp.choose();
      expect(click).not.toHaveBeenCalled();
    });

    it('emits the chosen file and clears the input', () => {
      const { fixture, comp } = setup();
      const picked = vi.fn();
      comp.picked.subscribe(picked);
      const input = q(fixture, 'input[type=file]') as HTMLInputElement;
      Object.defineProperty(input, 'files', { value: [file('a.csv'), file('b.csv')], configurable: true });
      input.dispatchEvent(new Event('change'));
      expect(picked).toHaveBeenCalledWith([expect.objectContaining({ name: 'a.csv' })]);
      expect(input.value).toBe('');
    });

    it('ignores an empty selection', () => {
      const { fixture, comp } = setup();
      const picked = vi.fn();
      comp.picked.subscribe(picked);
      const input = q(fixture, 'input[type=file]') as HTMLInputElement;
      Object.defineProperty(input, 'files', { value: null, configurable: true });
      input.dispatchEvent(new Event('change'));
      expect(picked).not.toHaveBeenCalled();
    });
  });

  describe('drag and drop', () => {
    it('highlights while a file is dragged over and survives child enter/leave', () => {
      const { fixture, comp, zone } = setup();
      const over = dragEvent('dragover');
      zone().dispatchEvent(dragEvent('dragenter'));
      zone().dispatchEvent(dragEvent('dragenter'));
      zone().dispatchEvent(over);
      expect(over.defaultPrevented).toBe(true);
      expect(over.dataTransfer!.dropEffect).toBe('copy');
      zone().dispatchEvent(dragEvent('dragleave'));
      fixture.detectChanges();
      expect(comp.dragOver).toBe(true);
      expect(q(fixture, '.hq-drop--over')).not.toBeNull();
      expect(q(fixture, '.hq-drop__text')?.textContent).toContain('Release to add');
      zone().dispatchEvent(dragEvent('dragleave'));
      zone().dispatchEvent(dragEvent('dragleave'));
      expect(comp.dragOver).toBe(false);
    });

    it('emits dropped files', () => {
      const { comp, zone } = setup();
      const picked = vi.fn();
      comp.picked.subscribe(picked);
      zone().dispatchEvent(dragEvent('dragenter'));
      const drop = dragEvent('drop', [file('d.csv')]);
      zone().dispatchEvent(drop);
      expect(drop.defaultPrevented).toBe(true);
      expect(comp.dragOver).toBe(false);
      expect(picked).toHaveBeenCalledWith([expect.objectContaining({ name: 'd.csv' })]);
    });

    it('a drop without files does nothing', () => {
      const { comp, zone } = setup();
      const picked = vi.fn();
      comp.picked.subscribe(picked);
      const drop = new Event('drop', { cancelable: true });
      zone().dispatchEvent(drop);
      expect(picked).not.toHaveBeenCalled();
    });

    it('ignores drags and drops while disabled', () => {
      const { fixture, comp, zone } = setup();
      const picked = vi.fn();
      comp.picked.subscribe(picked);
      fixture.componentRef.setInput('disabled', true);
      fixture.detectChanges();
      zone().dispatchEvent(dragEvent('dragenter'));
      const over = dragEvent('dragover');
      zone().dispatchEvent(over);
      expect(over.dataTransfer!.dropEffect).toBe('none');
      expect(comp.dragOver).toBe(false);
      zone().dispatchEvent(dragEvent('drop', [file()]));
      expect(picked).not.toHaveBeenCalled();
      expect(q(fixture, '.hq-drop--disabled')).not.toBeNull();
    });
  });

  describe('validation', () => {
    it('rejects a wrong type and shows the reason', () => {
      const { fixture, comp } = setup();
      fixture.componentRef.setInput('accept', '.csv');
      fixture.componentRef.setInput('hints', 'CSV');
      const picked = vi.fn();
      const rejected = vi.fn();
      comp.picked.subscribe(picked);
      comp.rejected.subscribe(rejected);
      comp.onDrop(dragEvent('drop', [file('a.pdf', 'application/pdf')]));
      fixture.detectChanges();
      expect(picked).not.toHaveBeenCalled();
      expect(rejected).toHaveBeenCalledWith('Please upload CSV files.');
      expect(q(fixture, '.hq-drop__error')?.textContent).toContain('Please upload CSV files.');
      expect(q(fixture, '.hq-drop--invalid')).not.toBeNull();
    });

    it('rejects files that are too large or too small, and clears the error on a good file', () => {
      const { fixture, comp } = setup();
      fixture.componentRef.setInput('maxSizeMB', 0.001);
      fixture.componentRef.setInput('minBytes', 50);
      const picked = vi.fn();
      comp.picked.subscribe(picked);
      comp.onDrop(dragEvent('drop', [file('big.csv', 'text/csv', 5000)]));
      expect(comp.errorMessage).toBe('File exceeds the 0.001 MB limit.');
      comp.onDrop(dragEvent('drop', [file('small.csv', 'text/csv', 10)]));
      expect(comp.errorMessage).toBe('File is too small (minimum 50 bytes).');
      comp.onDrop(dragEvent('drop', [file('ok.csv', 'text/csv', 100)]));
      expect(comp.errorMessage).toBe('');
      expect(picked).toHaveBeenCalledTimes(1);
    });

    it('multiple: emits the good files, names the bad one and stops at the cap', () => {
      const { fixture, comp } = setup();
      fixture.componentRef.setInput('multiple', true);
      fixture.componentRef.setInput('accept', 'image/*');
      fixture.componentRef.setInput('hints', 'images');
      fixture.componentRef.setInput('maxFiles', 2);
      const picked = vi.fn();
      comp.picked.subscribe(picked);
      comp.onDrop(dragEvent('drop', [file('a.png', 'image/png'), file('b.txt', 'text/plain'), file('c.png', 'image/png'), file('d.png', 'image/png')]));
      expect(picked).toHaveBeenCalledWith([expect.objectContaining({ name: 'a.png' }), expect.objectContaining({ name: 'c.png' })]);
      expect(comp.errorMessage).toBe('b.txt: Please upload images files.');
    });

    it('multiple: reports the cap when every file was fine', () => {
      const { fixture, comp } = setup();
      fixture.componentRef.setInput('multiple', true);
      fixture.componentRef.setInput('maxFiles', 1);
      comp.onDrop(dragEvent('drop', [file('a.csv'), file('b.csv')]));
      expect(comp.errorMessage).toBe('At most 1 documents can be attached.');
    });

    it('with validate off, emits files unchecked', () => {
      const { fixture, comp } = setup();
      fixture.componentRef.setInput('accept', '.csv');
      fixture.componentRef.setInput('validate', false);
      const picked = vi.fn();
      comp.picked.subscribe(picked);
      comp.onDrop(dragEvent('drop', [file('a.pdf', 'application/pdf')]));
      expect(picked).toHaveBeenCalledWith([expect.objectContaining({ name: 'a.pdf' })]);
      expect(comp.errorMessage).toBe('');
    });
  });
});
