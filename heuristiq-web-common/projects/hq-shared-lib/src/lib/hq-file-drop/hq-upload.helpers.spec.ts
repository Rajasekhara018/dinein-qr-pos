import { hqFileProblem, hqUploadLimits, hqUploadTextsFrom } from './hq-upload.helpers';
import { HQ_UPLOAD_TEXT_PARAMS } from './hq-upload.constants';
import { HQ_UPLOAD_DEFAULT_TEXTS, HqUploadTexts } from './hq-upload.texts';

function file(name: string, type: string, size: number): File {
  return new File(['x'.repeat(size)], name, { type });
}

describe('hq-upload helpers', () => {
  describe('hqUploadTextsFrom', () => {
    it('reads <prefix>.<member> with no params for plain texts', () => {
      const lookup = vi.fn((key: string) => `T(${key})`);
      const texts = hqUploadTextsFrom(lookup, 'shared.upload');
      expect(texts.chooseOne()).toBe('T(shared.upload.chooseOne)');
      expect(lookup).toHaveBeenCalledWith('shared.upload.chooseOne');
    });

    it('passes arguments as the named params', () => {
      const lookup = vi.fn((key: string, params?: Record<string, unknown>) => `${key}:${JSON.stringify(params)}`);
      const texts = hqUploadTextsFrom(lookup, 'p');
      expect(texts.tooLargeNamed('a.pdf', 5)).toBe('p.tooLargeNamed:{"name":"a.pdf","max":5}');
      expect(texts.limits('CSV', 10)).toBe('p.limits:{"hints":"CSV","max":10}');
    });

    it('covers every member of the default texts', () => {
      const texts = hqUploadTextsFrom((key) => key, 'k');
      for (const member of Object.keys(HQ_UPLOAD_DEFAULT_TEXTS) as (keyof HqUploadTexts)[]) {
        const args = (HQ_UPLOAD_TEXT_PARAMS[member] ?? []).map(() => 'x');
        expect((texts[member] as (...a: unknown[]) => string)(...args)).toBe(`k.${member}`);
      }
    });

    it('every param list matches the default text arity', () => {
      for (const member of Object.keys(HQ_UPLOAD_DEFAULT_TEXTS) as (keyof HqUploadTexts)[]) {
        expect(HQ_UPLOAD_DEFAULT_TEXTS[member].length).toBe((HQ_UPLOAD_TEXT_PARAMS[member] ?? []).length);
      }
    });
  });

  describe('hqFileProblem', () => {
    const rules = { accept: '.pdf', hints: 'PDF', maxSizeMB: 0.001, minBytes: 50 };

    it('accepts a good file', () => {
      expect(hqFileProblem(file('a.pdf', 'application/pdf', 100), rules, HQ_UPLOAD_DEFAULT_TEXTS)).toBeNull();
    });

    it('names the first rule broken', () => {
      expect(hqFileProblem(file('a.txt', 'text/plain', 100), rules, HQ_UPLOAD_DEFAULT_TEXTS)).toBe('Please upload PDF files.');
      expect(hqFileProblem(file('a.pdf', 'application/pdf', 10), rules, HQ_UPLOAD_DEFAULT_TEXTS)).toBe('File is too small (minimum 50 bytes).');
      expect(hqFileProblem(file('a.pdf', 'application/pdf', 5000), rules, HQ_UPLOAD_DEFAULT_TEXTS)).toBe('File exceeds the 0.001 MB limit.');
    });

    it('has no limits when they are zero', () => {
      const open = { accept: '', hints: '', maxSizeMB: 0, minBytes: 0 };
      expect(hqFileProblem(file('a.bin', '', 1), open, HQ_UPLOAD_DEFAULT_TEXTS)).toBeNull();
    });
  });

  describe('hqUploadLimits', () => {
    it('combines types and size, per file when asked', () => {
      expect(hqUploadLimits('PDF', 5, HQ_UPLOAD_DEFAULT_TEXTS)).toBe('PDF · up to 5 MB');
      expect(hqUploadLimits('PDF', 5, HQ_UPLOAD_DEFAULT_TEXTS, true)).toBe('PDF · up to 5 MB each');
    });

    it('falls back to the types or the size alone', () => {
      expect(hqUploadLimits('PDF', 0, HQ_UPLOAD_DEFAULT_TEXTS)).toBe('PDF');
      expect(hqUploadLimits('', 5, HQ_UPLOAD_DEFAULT_TEXTS)).toBe('Up to 5 MB');
      expect(hqUploadLimits('', 0, HQ_UPLOAD_DEFAULT_TEXTS)).toBe('');
    });
  });
});
