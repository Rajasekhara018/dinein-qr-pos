import { describe, expect, it } from 'vitest';
import { ONBOARDING_UPLOAD_ACCEPT, ONBOARDING_UPLOAD_MAX_SIZE_MB } from './franchise-onboarding-upload.constants';
import {
  fileExtension, isDmsFileRuleRejection, normalizedOnboardingFile, onboardingContentType, onboardingUploadProblem,
} from './franchise-onboarding-upload.helpers';

const MB = 1024 * 1024;

function fileOf(name: string, type: string, size = 2048): File {
  const file = new File([new Uint8Array(Math.min(size, 4096))], name, { type });
  Object.defineProperty(file, 'size', { value: size });
  return file;
}

describe('franchise onboarding upload rules', () => {
  it('offers PDF, JPG, JPEG and PNG, by extension and content type, up to 5 MB', () => {
    expect(ONBOARDING_UPLOAD_ACCEPT).toBe('.pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png');
    expect(ONBOARDING_UPLOAD_MAX_SIZE_MB).toBe(5);
  });

  it('reads the extension in any case', () => {
    expect(fileExtension('Cheque.JPEG')).toBe('.jpeg');
    expect(fileExtension('scan.final.Jpg')).toBe('.jpg');
    expect(fileExtension('noextension')).toBe('');
    expect(onboardingContentType('ID.PNG')).toBe('image/png');
    expect(onboardingContentType('deed.Pdf')).toBe('application/pdf');
    expect(onboardingContentType('photo.webp')).toBeNull();
  });

  it.each([
    ['cheque.jpg', 'image/jpeg'], ['CHEQUE.JPG', ''], ['scan.JPEG', 'image/jpg'], ['scan.jpeg', 'image/pjpeg'],
    ['proof.png', 'image/png'], ['PROOF.PNG', 'application/octet-stream'], ['deed.PDF', 'application/pdf'],
  ])('accepts %s sent as "%s"', (name, type) => {
    expect(onboardingUploadProblem(fileOf(name, type))).toBeNull();
  });

  it('refuses other types, empty files and files over the limit', () => {
    expect(onboardingUploadProblem(fileOf('letter.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'))).toBe('invalidType');
    expect(onboardingUploadProblem(fileOf('photo.webp', 'image/webp'))).toBe('invalidType');
    expect(onboardingUploadProblem(fileOf('scan', 'image/jpeg'))).toBe('invalidType');
    expect(onboardingUploadProblem(fileOf('scan.jpg', 'image/jpeg', 0))).toBe('empty');
    expect(onboardingUploadProblem(fileOf('scan.jpg', 'image/jpeg', 5 * MB))).toBeNull();
    expect(onboardingUploadProblem(fileOf('scan.jpg', 'image/jpeg', 5 * MB + 1))).toBe('tooLarge');
  });

  it.each([
    ['CHEQUE.JPG', '', 'image/jpeg'], ['scan.jpeg', 'image/jpg', 'image/jpeg'], ['scan.jpg', 'image/pjpeg', 'image/jpeg'],
    ['proof.PNG', 'application/octet-stream', 'image/png'], ['deed.pdf', '', 'application/pdf'],
  ])('sends %s (browser type "%s") as %s, keeping its name and bytes', async (name, type, expected) => {
    const original = new File(['jpeg-bytes'], name, { type, lastModified: 42 });
    const sent = normalizedOnboardingFile(original);
    expect(sent.type).toBe(expected);
    expect(sent.name).toBe(name);
    expect(sent.lastModified).toBe(42);
    expect(await sent.text()).toBe('jpeg-bytes');
  });

  it('leaves a file that already has the right type, or an unknown type, as it is', () => {
    const jpeg = new File(['x'], 'a.jpg', { type: 'image/jpeg' });
    const other = new File(['x'], 'a.txt', { type: 'text/plain' });
    expect(normalizedOnboardingFile(jpeg)).toBe(jpeg);
    expect(normalizedOnboardingFile(other)).toBe(other);
  });

  it('recognises the DMS type and size refusals only', () => {
    expect(isDmsFileRuleRejection({ error: { message: 'Document content type is not allowed' } })).toBe(true);
    expect(isDmsFileRuleRejection({ error: { message: 'Document file extension is not allowed' } })).toBe(true);
    expect(isDmsFileRuleRejection({ error: { message: 'Document exceeds the configured maximum file size' } })).toBe(true);
    expect(isDmsFileRuleRejection({ error: { message: 'Unknown or inactive document type X' } })).toBe(false);
    expect(isDmsFileRuleRejection({ status: 503 })).toBe(false);
    expect(isDmsFileRuleRejection(null)).toBe(false);
  });
});
