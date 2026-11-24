/// <reference types="node" />
import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { buildAppFeatures, PrivilegeRow, renderGenerated, AppRule } from '../../../scripts/app-features.lib.mjs';
import { APP_FEATURES, HUB_EXTRA_APPS, PRODUCT_APP_KEYS } from './home.constants';

const root = process.cwd();
const read = (relative: string): string => readFileSync(join(root, relative), 'utf8');
const rules = JSON.parse(read('src/app/home/app-feature-rules.json')) as { apps: AppRule[] };
const privileges = JSON.parse(read('../Rider_ApiGateway/src/main/resources/privilege.json')) as PrivilegeRow[];
const LANGUAGES = ['en', 'hi', 'ta', 'te', 'kn', 'ml', 'ar'];

type Texts = Record<string, unknown>;
const homeApps = (lang: string): { features: Record<string, Record<string, Texts>>; name: Record<string, string>; tagline: Record<string, string>; showAll: string; showFewer: string } =>
  JSON.parse(read(`src/assets/i18n/${lang}.json`)).home.apps;

describe('application features (from privilege.json)', () => {
  const built = buildAppFeatures(rules, privileges);

  it('puts every privilege of the catalogue in exactly one application and one feature', () => {
    expect(built.errors).toEqual([]);
    const placed = Object.values(built.apps).flatMap((features) => features.flatMap((feature) => feature.privileges));
    expect(placed).toHaveLength(privileges.length);
    expect(new Set(placed).size).toBe(privileges.length);
  });

  it('keeps the generated file in step with the rules and the catalogue (run `npm run gen:app-features`)', () => {
    expect(read('src/app/home/app-features.generated.ts').replace(/\r\n/g, '\n')).toBe(renderGenerated(built.apps));
    expect(Object.keys(APP_FEATURES).sort()).toEqual(rules.apps.map((app) => app.key).sort());
  });

  it('takes the products from the catalogue prefixes and leaves the rest shared', () => {
    expect([...PRODUCT_APP_KEYS].sort()).toEqual(['admin', 'food', 'franchise', 'ims', 'matrimony', 'train', 'wallet']);
    const productSources = rules.apps.filter((app) => PRODUCT_APP_KEYS.includes(app.key)).flatMap((app) => app.sources.map((s) => s.codePrefix));
    expect(productSources.every((prefix) => prefix !== 'SHARED')).toBe(true);
    const sharedSources = rules.apps.filter((app) => !PRODUCT_APP_KEYS.includes(app.key)).flatMap((app) => app.sources.map((s) => s.codePrefix));
    expect(sharedSources.every((prefix) => prefix === 'SHARED')).toBe(true);
  });

  it('has an application for every extra hub card', () => {
    for (const extra of HUB_EXTRA_APPS) {
      expect(APP_FEATURES[extra.key], extra.key).toBeDefined();
    }
  });

  describe.each(LANGUAGES)('translations (%s)', (lang) => {
    const texts = homeApps(lang);

    it('describes every feature with a title and a description', () => {
      const missing: string[] = [];
      for (const app of rules.apps) {
        for (const feature of app.features) {
          const entry = texts.features?.[app.key]?.[feature.key] as { title?: string; desc?: string } | undefined;
          if (!entry?.title?.trim() || !entry?.desc?.trim()) missing.push(`${app.key}.${feature.key}`);
        }
      }
      expect(missing).toEqual([]);
    });

    it('names and describes the extra cards and has the show-all labels', () => {
      for (const extra of HUB_EXTRA_APPS) {
        expect(texts.name?.[extra.key], `name ${extra.key}`).toBeTruthy();
        expect(texts.tagline?.[extra.key], `tagline ${extra.key}`).toBeTruthy();
      }
      expect(texts.showAll).toContain('{{count}}');
      expect(texts.showFewer).toBeTruthy();
    });
  });
});
