import { ApiError } from '../../../../core/api/api-error';

/**
 * Client-side image validation and pre-resize before upload.
 *
 * Why no library (e.g. browser-image-compression): the server already strips EXIF, auto-rotates and re-encodes to
 * max 800px, so the client only needs to shrink big phone photos (12+ MP, often > 5 MB) to keep uploads fast and
 * under the 5 MB limit. `createImageBitmap` (honours EXIF orientation) + a canvas does that in ~60 lines with no
 * extra dependency in the bundle.
 */

export const MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
export const ACCEPTED_TYPES: readonly string[] = ['image/jpeg', 'image/png', 'image/webp'];

export interface PrepareOptions {
  /** Longest side after resizing. */
  maxDimension: number;
  /** JPEG quality 0–1. */
  quality: number;
  /** Re-encode files larger than this even if their dimensions are fine. */
  reencodeAboveBytes: number;
  /** Keep transparency (PNG output) — e.g. for logos. Otherwise JPEG. */
  keepAlpha: boolean;
}

export const DEFAULT_PREPARE_OPTIONS: PrepareOptions = {
  maxDimension: 1600,
  quality: 0.85,
  reencodeAboveBytes: 1.5 * 1024 * 1024,
  keepAlpha: false,
};

export type ImageProblem = 'EMPTY' | 'NOT_AN_IMAGE' | 'TOO_LARGE' | 'UNREADABLE';

export interface FileLike {
  name: string;
  type: string;
  size: number;
}

export interface DecodedImage {
  width: number;
  height: number;
  source: CanvasImageSource;
  close?: () => void;
}

/** Seams for tests (jsdom has no canvas / createImageBitmap). */
export interface ImageCodec {
  decode(file: Blob): Promise<DecodedImage>;
  encode(image: DecodedImage, width: number, height: number, type: string, quality: number): Promise<Blob>;
}

export interface PreparedImage {
  blob: Blob;
  fileName: string;
  width: number;
  height: number;
  /** True when the file was re-encoded (resized or converted). */
  processed: boolean;
}

const IMAGE_EXTENSION = /\.(jpe?g|png|webp|gif|bmp|heic|heif|avif)$/i;

/** Quick checks before decoding. Unknown `image/*` types (HEIC, GIF…) pass: they are converted if decodable. */
export function checkImageFile(file: FileLike): ImageProblem | null {
  if (file.size === 0) return 'EMPTY';
  const looksLikeImage = file.type.startsWith('image/') || (!file.type && IMAGE_EXTENSION.test(file.name));
  if (!looksLikeImage) return 'NOT_AN_IMAGE';
  // A huge file is only rejected when we could not shrink it; see prepareImage().
  return null;
}

/** Scales (w, h) down so the longest side is ≤ max, keeping the aspect ratio. Never upscales. */
export function fitWithin(
  width: number,
  height: number,
  max: number,
): { width: number; height: number; scaled: boolean } {
  const longest = Math.max(width, height);
  if (longest <= max || longest === 0) return { width, height, scaled: false };
  const ratio = max / longest;
  return {
    width: Math.max(1, Math.round(width * ratio)),
    height: Math.max(1, Math.round(height * ratio)),
    scaled: true,
  };
}

export function needsReencode(
  file: FileLike,
  width: number,
  height: number,
  options: PrepareOptions,
): boolean {
  return (
    fitWithin(width, height, options.maxDimension).scaled ||
    !ACCEPTED_TYPES.includes(file.type) ||
    file.size > options.reencodeAboveBytes
  );
}

export function outputFileName(name: string, type: string): string {
  const base = name.replace(/\.[^.]+$/, '') || 'image';
  return `${base}.${type === 'image/png' ? 'png' : 'jpg'}`;
}

/**
 * Validates, and shrinks/converts when useful. Throws an `ImagePrepError` with a user-facing problem code.
 */
export async function prepareImage(
  file: File,
  options: Partial<PrepareOptions> = {},
  codec: ImageCodec = browserCodec,
): Promise<PreparedImage> {
  const opts = { ...DEFAULT_PREPARE_OPTIONS, ...options };
  const problem = checkImageFile(file);
  if (problem) throw new ImagePrepError(problem);

  let decoded: DecodedImage;
  try {
    decoded = await codec.decode(file);
  } catch {
    // Cannot decode here: let the server judge supported types, reject everything else.
    if (ACCEPTED_TYPES.includes(file.type) && file.size <= MAX_UPLOAD_BYTES) {
      return { blob: file, fileName: file.name, width: 0, height: 0, processed: false };
    }
    throw new ImagePrepError(ACCEPTED_TYPES.includes(file.type) ? 'TOO_LARGE' : 'UNREADABLE');
  }

  try {
    if (!needsReencode(file, decoded.width, decoded.height, opts)) {
      return {
        blob: file,
        fileName: file.name,
        width: decoded.width,
        height: decoded.height,
        processed: false,
      };
    }
    const target = fitWithin(decoded.width, decoded.height, opts.maxDimension);
    const type = opts.keepAlpha && file.type !== 'image/jpeg' ? 'image/png' : 'image/jpeg';
    let blob = await codec.encode(decoded, target.width, target.height, type, opts.quality);
    // Converting a small, already-supported file can make it bigger: keep the original then.
    if (!target.scaled && ACCEPTED_TYPES.includes(file.type) && blob.size >= file.size) {
      blob = file;
    }
    if (blob.size > MAX_UPLOAD_BYTES) throw new ImagePrepError('TOO_LARGE');
    const processed = blob !== file;
    return {
      blob,
      fileName: processed ? outputFileName(file.name, type) : file.name,
      width: target.width,
      height: target.height,
      processed,
    };
  } finally {
    decoded.close?.();
  }
}

export class ImagePrepError extends Error {
  constructor(readonly problem: ImageProblem) {
    super(imageProblemMessage(problem));
  }
}

export function imageProblemMessage(problem: ImageProblem): string {
  switch (problem) {
    case 'EMPTY':
      return 'The file is empty.';
    case 'NOT_AN_IMAGE':
      return 'That file is not an image. Choose a JPEG, PNG or WEBP photo.';
    case 'TOO_LARGE':
      return 'The image is larger than 5 MB. Choose a smaller photo.';
    default:
      return 'This image could not be read. Try a JPEG, PNG or WEBP file.';
  }
}

/** User-facing message for an upload failure (server codes from `ImageProcessor` / `AdminImageController`). */
export function uploadErrorMessage(error: unknown): string {
  if (error instanceof ImagePrepError) return error.message;
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'UNSUPPORTED_IMAGE':
        return 'Unsupported image type. Use a JPEG, PNG or WEBP file.';
      case 'FILE_TOO_LARGE':
        return 'The image is larger than 5 MB. Choose a smaller photo.';
      case 'INVALID_IMAGE':
      case 'EMPTY_FILE':
        return 'The image could not be read. Is the file corrupted?';
      case 'IMAGE_TOO_LARGE':
        return 'The image dimensions are too large.';
      case 'RATE_LIMITED':
        return 'Too many uploads. Please wait a minute and try again.';
      case 'NETWORK_ERROR':
        return 'Upload failed: you appear to be offline.';
      default:
        return error.message || 'Upload failed. Please try again.';
    }
  }
  return 'Upload failed. Please try again.';
}

/** Browser implementation: EXIF-aware decode, canvas re-encode. */
export const browserCodec: ImageCodec = {
  async decode(file: Blob): Promise<DecodedImage> {
    const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
    return { width: bitmap.width, height: bitmap.height, source: bitmap, close: () => bitmap.close() };
  },
  encode(image, width, height, type, quality): Promise<Blob> {
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    if (!ctx) return Promise.reject(new ImagePrepError('UNREADABLE'));
    if (type === 'image/jpeg') {
      // JPEG has no alpha: paint transparent areas white instead of black.
      ctx.fillStyle = '#ffffff';
      ctx.fillRect(0, 0, width, height);
    }
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(image.source, 0, 0, width, height);
    return new Promise((resolve, reject) =>
      canvas.toBlob(
        (blob) => (blob ? resolve(blob) : reject(new ImagePrepError('UNREADABLE'))),
        type,
        quality,
      ),
    );
  },
};
