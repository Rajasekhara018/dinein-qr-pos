/// <reference types="node" />
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { DATE_DISPLAY_ALLOWLIST, DATE_DISPLAY_FORBIDDEN } from './date-display-guard.constants';

/** Every non-spec .ts and .html file under src/, as [path relative to the app root, text]. */
function sourceFiles(): Array<[string, string]> {
  const root = process.cwd();
  const found: Array<[string, string]> = [];
  const walk = (dir: string): void => {
    for (const name of readdirSync(dir)) {
      const full = join(dir, name);
      if (statSync(full).isDirectory()) {
        walk(full);
      } else if ((name.endsWith('.ts') || name.endsWith('.html')) && !name.endsWith('.spec.ts') && !name.endsWith('.constants.ts')) {
        found.push([relative(root, full).split(sep).join('/'), readFileSync(full, 'utf8')]);
      }
    }
  };
  walk(join(root, 'src'));
  return found;
}

describe('date display guard', { timeout: 30_000 }, () => {
  it('no screen formats a date without going through the user timezone', () => {
    const offenders: string[] = [];
    for (const [path, text] of sourceFiles()) {
      if (path in DATE_DISPLAY_ALLOWLIST) continue;
      for (const rule of DATE_DISPLAY_FORBIDDEN) {
        if (rule.pattern.test(text)) offenders.push(`${path}: ${rule.name}`);
      }
    }
    expect(offenders).toEqual([]);
  });

  it('every allowlisted file still exists and still needs its exception', () => {
    const files = new Map(sourceFiles());
    for (const path of Object.keys(DATE_DISPLAY_ALLOWLIST)) {
      const text = files.get(path);
      expect(text, `${path} no longer exists: remove it from the allowlist`).toBeDefined();
      const stillNeeded = DATE_DISPLAY_FORBIDDEN.some((rule) => rule.pattern.test(text ?? ''));
      expect(stillNeeded, `${path} no longer needs its exception: remove it from the allowlist`).toBe(true);
    }
  });
});
