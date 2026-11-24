import { ADVANCED_HTML_PATTERN, PLACEHOLDER_PATTERN, SAMPLE_VALUE_RULES } from './template-preview.constants';

/** Strips the {{ }} around an attribute from the event catalog ("{{otp}}" -> "otp"). */
export function attributeName(attribute: string): string {
  return attribute.replace(/^\{\{|\}\}$/g, '').trim();
}

/** Unique placeholder names used in the given texts, in order of first use. */
export function extractPlaceholders(...texts: Array<string | null | undefined>): string[] {
  const found = new Set<string>();
  for (const text of texts) {
    if (!text) continue;
    for (const match of text.matchAll(PLACEHOLDER_PATTERN)) found.add(match[1]);
  }
  return [...found];
}

export function sampleValueFor(name: string): string {
  const key = name.toLowerCase();
  const rule = SAMPLE_VALUE_RULES.find(r => r.test.test(key));
  return rule ? rule.value : `Sample ${name}`;
}

export function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/**
 * Replaces every {{name}} with its sample value. Names without a value are left as typed, like the
 * server does. Values are HTML-escaped when the target is HTML so a sample can never inject markup.
 */
export function fillPlaceholders(text: string, values: Record<string, string>, asHtml: boolean): string {
  return text.replace(PLACEHOLDER_PATTERN, (whole, name: string) => {
    const value = values[name];
    if (value === undefined) return whole;
    return asHtml ? escapeHtml(value) : value;
  });
}

export function isAdvancedHtml(html: string | null | undefined): boolean {
  return !!html && ADVANCED_HTML_PATTERN.test(html);
}

/** Wraps a body fragment in a minimal page so the preview frame looks like an email client. */
export function buildPreviewDocument(html: string): string {
  if (/<\s*html\b/i.test(html)) return html;
  return '<!doctype html><html><head><meta charset="utf-8">' +
    '<meta name="viewport" content="width=device-width,initial-scale=1">' +
    '<style>body{margin:0;padding:16px;font-family:Arial,Helvetica,sans-serif;font-size:14px;color:#333;line-height:1.5}' +
    'img{max-width:100%}</style></head><body>' + html + '</body></html>';
}

/** Text a phone would show for an HTML body (SMS, WhatsApp and push have no markup). */
export function htmlToPlainText(html: string): string {
  const withBreaks = html
    .replace(/<\s*br\s*\/?>/gi, '\n')
    .replace(/<\/\s*(p|div|h[1-6]|li|tr)\s*>/gi, '\n');
  const doc = new DOMParser().parseFromString(withBreaks, 'text/html');
  return (doc.body.textContent ?? '').replace(/\n{3,}/g, '\n\n').trim();
}
