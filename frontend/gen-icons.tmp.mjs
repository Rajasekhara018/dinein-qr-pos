// Renders the DineIn app icon (brand-orange rounded square + the same lucide "chef-hat" glyph used
// in the app's own login screens) to every PNG size the manifest/index.html need, plus a favicon.ico.
import { chromium } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

const OUT = 'd:/Personal/Important/Heuristq/dinein-qr-pos/frontend/public';
const BRAND = '#c2410c'; // matches src/styles.css --brand default

// Exact path data decoded from @lucide/angular's ChefHat icon (24x24 viewBox), so the app icon matches
// the glyph already used as the brand mark on the admin/kitchen/waiter sign-in screens.
const CHEF_HAT_PATHS = [
  'M17 21a1 1 0 0 0 1-1v-5.35c0-.457.316-.844.727-1.041a4 4 0 0 0-2.134-7.589 5 5 0 0 0-9.186 0 4 4 0 0 0-2.134 7.588c.411.198.727.585.727 1.041V20a1 1 0 0 0 1 1Z',
  'M6 17h12',
];

function iconSvg({ size, radius, padded }) {
  // `padded`: keep the glyph inside an ~66% safe zone so Android's maskable-icon circle crop never clips it.
  const glyphScale = padded ? 0.5 : 0.62;
  const glyph = size * glyphScale;
  const offset = (size - glyph) / 2;
  // stroke-width="2" is the icon's OWN value in its native 24-unit box (same as every other lucide icon in this
  // app); it must live on the same group as the scale transform so the transform scales the rendered stroke along
  // with the path geometry, exactly as it would for an inline <svg lucideChefHat> in the app itself. Setting a
  // pre-multiplied width on an ancestor of the scaled group double-scales it into a solid blob.
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
    <rect width="${size}" height="${size}" rx="${radius}" fill="${BRAND}" />
    <g transform="translate(${offset} ${offset}) scale(${glyph / 24})" fill="none" stroke="#fff" stroke-width="2"
       stroke-linecap="round" stroke-linejoin="round">
      <path d="${CHEF_HAT_PATHS[0]}" />
      <path d="${CHEF_HAT_PATHS[1]}" />
    </g>
  </svg>`;
}

// Minimal single-image .ico container wrapping a PNG payload (the modern, universally-supported form of ICO).
function pngToIco(pngBuffer, size) {
  const header = Buffer.alloc(6);
  header.writeUInt16LE(0, 0); // reserved
  header.writeUInt16LE(1, 2); // type: icon
  header.writeUInt16LE(1, 4); // 1 image
  const entry = Buffer.alloc(16);
  entry.writeUInt8(size >= 256 ? 0 : size, 0);
  entry.writeUInt8(size >= 256 ? 0 : size, 1);
  entry.writeUInt8(0, 2);
  entry.writeUInt8(0, 3);
  entry.writeUInt16LE(1, 4); // color planes
  entry.writeUInt16LE(32, 6); // bits per pixel
  entry.writeUInt32LE(pngBuffer.length, 8);
  entry.writeUInt32LE(header.length + entry.length, 12); // offset
  return Buffer.concat([header, entry, pngBuffer]);
}

const browser = await chromium.launch();
const page = await browser.newPage();

async function renderPng(size, radius, padded) {
  await page.setViewportSize({ width: size, height: size });
  await page.setContent(
    `<!doctype html><html><body style="margin:0">${iconSvg({ size, radius, padded })}</body></html>`,
  );
  return page.screenshot({ omitScreenshotTaint: true, clip: { x: 0, y: 0, width: size, height: size } });
}

const sizes = [72, 96, 128, 144, 152, 192, 384, 512];
for (const size of sizes) {
  const radius = Math.round(size * 0.22); // matches the app's rounded-2xl-ish brand-icon boxes
  const png = await renderPng(size, radius, true);
  fs.writeFileSync(path.join(OUT, `icons/icon-${size}x${size}.png`), png);
  console.log('wrote', size);
}

// apple-touch-icon: iOS applies its own rounding, so a full-bleed square (no radius) looks right.
const appleTouch = await renderPng(192, 0, true);
fs.writeFileSync(path.join(OUT, 'icons/apple-touch-icon.png'), appleTouch);

// favicon: small sizes read better slightly less padded (glyph a touch larger) and with a visible radius.
const favicon32 = await renderPng(32, 6, false);
fs.writeFileSync(path.join(OUT, 'favicon.ico'), pngToIco(favicon32, 32));

await browser.close();
console.log('done');
