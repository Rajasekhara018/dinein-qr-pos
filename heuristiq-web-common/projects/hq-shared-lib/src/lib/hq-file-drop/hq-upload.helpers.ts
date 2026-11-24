import { isAcceptedFile } from '../hq-file.util';
import { HQ_UPLOAD_TEXT_PARAMS } from './hq-upload.constants';
import { HQ_UPLOAD_DEFAULT_TEXTS, HqUploadTexts } from './hq-upload.texts';

/** A translate lookup, e.g. `(key, params) => translate.instant(key, params)`. */
export type HqTranslateLookup = (key: string, params?: Record<string, unknown>) => string;

/** The rules a picked file is checked against before it is handed on. */
export interface HqFileRules {
  /** Accept list like '.csv,text/csv'; empty accepts any type. */
  accept: string;
  /** Shown in the "wrong type" message, e.g. 'PDF, JPEG, PNG'. */
  hints: string;
  /** Largest size in MB; 0 or less means no limit. */
  maxSizeMB: number;
  /** Smallest size in bytes; 0 means no lower limit. */
  minBytes: number;
}

/**
 * Builds HqUploadTexts from an app's translate lookup: member `x` reads key `<prefix>.x`, with its arguments passed
 * as the named parameters listed in HQ_UPLOAD_TEXT_PARAMS. Apps provide it as
 * `{ provide: HQ_UPLOAD_TEXTS, useFactory: (t) => hqUploadTextsFrom((k, p) => t.instant(k, p), 'shared.upload'), deps: [TranslateService] }`.
 */
export function hqUploadTextsFrom(lookup: HqTranslateLookup, prefix: string): HqUploadTexts {
  const texts = {} as Record<keyof HqUploadTexts, (...args: unknown[]) => string>;
  for (const member of Object.keys(HQ_UPLOAD_DEFAULT_TEXTS) as (keyof HqUploadTexts)[]) {
    const names = HQ_UPLOAD_TEXT_PARAMS[member] ?? [];
    texts[member] = (...args: unknown[]) => {
      if (!names.length) return lookup(`${prefix}.${member}`);
      const params: Record<string, unknown> = {};
      names.forEach((name, index) => (params[name] = args[index]));
      return lookup(`${prefix}.${member}`, params);
    };
  }
  return texts as unknown as HqUploadTexts;
}

/** The reason a file breaks the rules (already in the user's language), or null when it is fine. */
export function hqFileProblem(file: File, rules: HqFileRules, texts: HqUploadTexts): string | null {
  if (!isAcceptedFile(file, rules.accept)) return texts.invalidType(rules.hints);
  if (rules.minBytes > 0 && file.size < rules.minBytes) return texts.tooSmall();
  if (rules.maxSizeMB > 0 && file.size > rules.maxSizeMB * 1024 * 1024) return texts.tooLarge(rules.maxSizeMB);
  return null;
}

/** The hint line under a drop area: types and size limit, or just the types when there is no limit. */
export function hqUploadLimits(hints: string, maxSizeMB: number, texts: HqUploadTexts, each = false): string {
  if (!(maxSizeMB > 0)) return hints;
  if (!hints) return texts.maxSize(maxSizeMB);
  return each ? texts.limitsEach(hints, maxSizeMB) : texts.limits(hints, maxSizeMB);
}
