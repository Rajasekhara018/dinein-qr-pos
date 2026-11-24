import { LanguageEnum } from '../models/uam.models';

/**
 * The language a user saves on their account (UAM's LanguageEnum) for each language the web app can show (file name under
 * assets/i18n). Kannada is KA on the account and kn in the app; Odia (OD) is on the account but the app has no Odia text yet.
 */
export const LANGUAGE_ENUM_BY_CODE: Readonly<Record<string, LanguageEnum>> = {
  en: 'EN',
  hi: 'HI',
  ta: 'TA',
  te: 'TE',
  kn: 'KA',
  ml: 'ML',
  ar: 'AR',
};
