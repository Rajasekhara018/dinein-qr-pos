// Regenerates src/app/home/app-features.generated.ts. Fails (non-zero) when a privilege is not covered by any feature.
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildAppFeatures, renderGenerated } from './app-features.lib.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rules = JSON.parse(readFileSync(resolve(root, 'src/app/home/app-feature-rules.json'), 'utf8'));
const privileges = JSON.parse(readFileSync(resolve(root, '../Rider_ApiGateway/src/main/resources/privilege.json'), 'utf8'));

const { apps, errors } = buildAppFeatures(rules, privileges);
if (errors.length) {
  console.error(errors.join('\n'));
  console.error(`\n${errors.length} problem(s); fix app-feature-rules.json.`);
  process.exit(1);
}
writeFileSync(resolve(root, 'src/app/home/app-features.generated.ts'), renderGenerated(apps), 'utf8');
const total = Object.values(apps).reduce((n, f) => n + f.length, 0);
console.log(`ok: ${Object.keys(apps).length} applications, ${total} features, ${privileges.length} privileges`);
