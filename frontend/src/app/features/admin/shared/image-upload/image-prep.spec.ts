import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../../core/api/api-error';
import {
  checkImageFile,
  fitWithin,
  ImageCodec,
  ImagePrepError,
  MAX_UPLOAD_BYTES,
  needsReencode,
  outputFileName,
  prepareImage,
  uploadErrorMessage,
  DEFAULT_PREPARE_OPTIONS,
} from './image-prep';

function file(name: string, type: string, size: number): File {
  const f = new File([new Uint8Array(Math.min(size, 16))], name, { type });
  Object.defineProperty(f, 'size', { value: size });
  return f;
}

function codec(width: number, height: number, outputSize: number): ImageCodec & {
  encode: ReturnType<typeof vi.fn>;
} {
  return {
    decode: vi.fn(async () => ({ width, height, source: {} as CanvasImageSource, close: vi.fn() })),
    encode: vi.fn(async (_img, _w, _h, type: string) => {
      const blob = new Blob([new Uint8Array(8)], { type });
      Object.defineProperty(blob, 'size', { value: outputSize });
      return blob;
    }),
  };
}

describe('image pre-resize & validation', () => {
  it('rejects empty files and non-images', () => {
    expect(checkImageFile({ name: 'a.jpg', type: 'image/jpeg', size: 0 })).toBe('EMPTY');
    expect(checkImageFile({ name: 'menu.pdf', type: 'application/pdf', size: 10 })).toBe('NOT_AN_IMAGE');
    expect(checkImageFile({ name: 'photo.heic', type: '', size: 10 })).toBeNull();
    expect(checkImageFile({ name: 'a.png', type: 'image/png', size: 10 })).toBeNull();
  });

  it('fits within the max dimension keeping the aspect ratio, never upscaling', () => {
    expect(fitWithin(4000, 3000, 1600)).toEqual({ width: 1600, height: 1200, scaled: true });
    expect(fitWithin(1000, 3000, 1600)).toEqual({ width: 533, height: 1600, scaled: true });
    expect(fitWithin(800, 600, 1600)).toEqual({ width: 800, height: 600, scaled: false });
  });

  it('re-encodes big, oversized or unsupported files only', () => {
    const opts = DEFAULT_PREPARE_OPTIONS;
    expect(needsReencode({ name: 'a.jpg', type: 'image/jpeg', size: 200_000 }, 800, 600, opts)).toBe(false);
    expect(needsReencode({ name: 'a.jpg', type: 'image/jpeg', size: 200_000 }, 4000, 3000, opts)).toBe(true);
    expect(needsReencode({ name: 'a.jpg', type: 'image/jpeg', size: 3_000_000 }, 800, 600, opts)).toBe(true);
    expect(needsReencode({ name: 'a.heic', type: 'image/heic', size: 100 }, 800, 600, opts)).toBe(true);
  });

  it('shrinks a large phone photo to ≤1600px JPEG at quality 0.85', async () => {
    const c = codec(4032, 3024, 900_000);
    const result = await prepareImage(file('IMG_1234.HEIC', 'image/heic', 7_000_000), {}, c);
    expect(c.encode).toHaveBeenCalledWith(expect.anything(), 1600, 1200, 'image/jpeg', 0.85);
    expect(result).toMatchObject({ width: 1600, height: 1200, processed: true, fileName: 'IMG_1234.jpg' });
  });

  it('keeps small supported files untouched', async () => {
    const c = codec(640, 480, 1);
    const original = file('dosa.webp', 'image/webp', 80_000);
    const result = await prepareImage(original, {}, c);
    expect(c.encode).not.toHaveBeenCalled();
    expect(result.blob).toBe(original);
    expect(result.processed).toBe(false);
  });

  it('keeps PNG transparency for logos', async () => {
    const c = codec(3000, 3000, 400_000);
    const result = await prepareImage(file('logo.png', 'image/png', 2_000_000), { keepAlpha: true }, c);
    expect(c.encode.mock.calls[0][3]).toBe('image/png');
    expect(result.fileName).toBe('logo.png');
  });

  it('fails with TOO_LARGE when the result is still over 5 MB', async () => {
    const c = codec(4000, 4000, MAX_UPLOAD_BYTES + 1);
    await expect(prepareImage(file('huge.jpg', 'image/jpeg', 9_000_000), {}, c)).rejects.toMatchObject({
      problem: 'TOO_LARGE',
    });
  });

  it('rejects undecodable non-supported files, but lets small supported ones through to the server', async () => {
    const broken: ImageCodec = { decode: () => Promise.reject(new Error('x')), encode: vi.fn() };
    await expect(prepareImage(file('a.bmp', 'image/bmp', 1000), {}, broken)).rejects.toBeInstanceOf(ImagePrepError);
    const ok = await prepareImage(file('a.jpg', 'image/jpeg', 1000), {}, broken);
    expect(ok.processed).toBe(false);
  });

  it('maps server errors to friendly messages', () => {
    expect(uploadErrorMessage(new ApiError(415, 'UNSUPPORTED_IMAGE', 'x'))).toContain('JPEG, PNG or WEBP');
    expect(uploadErrorMessage(new ApiError(413, 'FILE_TOO_LARGE', 'x'))).toContain('5 MB');
    expect(uploadErrorMessage(new ImagePrepError('NOT_AN_IMAGE'))).toContain('not an image');
  });

  it('names outputs by type', () => {
    expect(outputFileName('photo.heic', 'image/jpeg')).toBe('photo.jpg');
    expect(outputFileName('logo', 'image/png')).toBe('logo.png');
  });
});
