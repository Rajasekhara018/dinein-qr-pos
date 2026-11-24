// Builds the geography reference files of the franchise plan form (assets/geo):
//   subdivisions.json         { "IN": [["TG", "Telangana"], ...] }                states by country, ISO 3166-2 code without the country prefix
//   districts/<CC>.json       { "TG": [["540", "Warangal Rural"], ...] }          districts of one country, by state code
//
// Sources (see assets/geo/README.md for licences):
//   --iso        iso3166-2-db  data/iso3166-2.json       (MIT)     countries, states, their codes and GeoNames ids
//   --admin1     GeoNames      admin1CodesASCII.txt      (CC BY 4.0) first-level divisions with GeoNames ids
//   --admin2     GeoNames      admin2Codes.txt           (CC BY 4.0) second-level divisions (districts) with their codes
//   --out        the assets/geo folder to write
//
// A district is placed under a state by matching the GeoNames id of its first-level division to the GeoNames id the ISO
// data holds for the state. Districts whose state cannot be matched are left out rather than guessed.
import { mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const args = Object.fromEntries(process.argv.slice(2).reduce((pairs, arg, i, all) => (arg.startsWith('--') ? [...pairs, [arg.slice(2), all[i + 1]]] : pairs), []));
for (const key of ['iso', 'admin1', 'admin2', 'out']) {
  if (!args[key]) {
    throw new Error(`missing --${key}`);
  }
}

const iso = JSON.parse(readFileSync(args.iso, 'utf8'));
const byName = (a, b) => a[1].localeCompare(b[1]);

// states, and the ISO code each GeoNames id stands for
const subdivisions = {};
const isoByGeonamesId = new Map();
for (const [country, info] of Object.entries(iso)) {
  const states = [];
  for (const region of info.regions ?? []) {
    const code = String(region.iso ?? '').trim();
    const name = String(region.names?.en ?? region.name ?? '').trim();
    if (!code || !name) continue;
    states.push([code, name]);
    const id = region.reference?.geonames;
    if (id) isoByGeonamesId.set(`${country}|${id}`, code);
  }
  if (states.length > 0) subdivisions[country] = states.sort(byName);
}

// GeoNames first-level code (IN.40) -> ISO state code
const isoByAdmin1 = new Map();
for (const line of readFileSync(args.admin1, 'utf8').split('\n')) {
  const [code, , , id] = line.split('\t');
  if (!code || !id) continue;
  const iso2 = code.split('.')[0];
  const state = isoByGeonamesId.get(`${iso2}|${id.trim()}`);
  if (state) isoByAdmin1.set(code, state);
}

// districts by country and state
const districts = {};
let placed = 0;
let skipped = 0;
for (const line of readFileSync(args.admin2, 'utf8').split('\n')) {
  const [code, name] = line.split('\t');
  if (!code || !name) continue;
  const [country, admin1, admin2] = code.split('.');
  const state = isoByAdmin1.get(`${country}.${admin1}`);
  if (!state || !admin2) {
    skipped++;
    continue;
  }
  ((districts[country] ??= {})[state] ??= []).push([admin2, name.trim()]);
  placed++;
}

rmSync(join(args.out, 'districts'), { recursive: true, force: true });
mkdirSync(join(args.out, 'districts'), { recursive: true });
writeFileSync(join(args.out, 'subdivisions.json'), JSON.stringify(subdivisions));
for (const [country, states] of Object.entries(districts)) {
  for (const list of Object.values(states)) list.sort(byName);
  writeFileSync(join(args.out, 'districts', `${country}.json`), JSON.stringify(states));
}

const stateCount = Object.values(subdivisions).reduce((n, list) => n + list.length, 0);
console.log(`${Object.keys(subdivisions).length} countries, ${stateCount} states; ${placed} districts in ${Object.keys(districts).length} countries (${skipped} districts skipped: state not matched)`);
const india = districts.IN ?? {};
console.log(`India: ${Object.keys(india).length} states with districts, ${Object.values(india).reduce((n, l) => n + l.length, 0)} districts; TG:`, JSON.stringify((india.TG ?? []).slice(0, 4)));
