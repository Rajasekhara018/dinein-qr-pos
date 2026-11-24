import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HQ_UPLOAD_DEFAULT_TEXTS, HQ_UPLOAD_TEXT_PARAMS, HqUploadTexts } from 'hq-shared-lib';
import { beforeEach, describe, expect, it } from 'vitest';
import ar from '../../assets/i18n/ar.json';
import en from '../../assets/i18n/en.json';
import hi from '../../assets/i18n/hi.json';
import { uploadTexts } from './hq-shared-lib-texts';

type Member = keyof HqUploadTexts;
const MEMBERS = Object.keys(HQ_UPLOAD_DEFAULT_TEXTS) as Member[];
const call = (texts: HqUploadTexts, member: Member): string =>
  (texts[member] as (...args: unknown[]) => string)(...(HQ_UPLOAD_TEXT_PARAMS[member] ?? []).map((name) => (name === 'max' ? 5 : `<${name}>`)));

describe('upload texts (shared.upload.*)', () => {
  let translate: TranslateService;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ imports: [TranslateModule.forRoot()] });
    translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.setTranslation('hi', hi);
    translate.setTranslation('ar', ar);
  });

  it('translates every text the shared upload components show, with its parameters filled in', () => {
    translate.use('en');
    const texts = TestBed.runInInjectionContext(() => uploadTexts());
    for (const member of MEMBERS) {
      const text = call(texts, member);
      expect(text, member).not.toContain('shared.upload');
      expect(text, member).not.toContain('{{');
    }
    expect(texts.tooLargeNamed('a.pdf', 5)).toBe('a.pdf exceeds the 5 MB limit.');
    expect(texts.limits('CSV', 10)).toBe('CSV · up to 10 MB');
  });

  it('follows a language switch', () => {
    const texts = TestBed.runInInjectionContext(() => uploadTexts());
    translate.use('hi');
    expect(texts.chooseOne()).toBe('फ़ाइल चुनें');
    translate.use('ar');
    expect(texts.chooseOne()).toBe('اختر ملفًا');
  });
});
