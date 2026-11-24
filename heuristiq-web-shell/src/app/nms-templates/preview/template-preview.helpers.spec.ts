import { describe, expect, it } from 'vitest';
import {
  attributeName, buildPreviewDocument, extractPlaceholders, fillPlaceholders,
  htmlToPlainText, isAdvancedHtml, sampleValueFor,
} from './template-preview.helpers';

describe('template preview helpers', () => {
  it('reads attribute names from the catalog format', () => {
    expect(attributeName('{{otp}}')).toBe('otp');
  });

  it('lists each placeholder once across several texts', () => {
    expect(extractPlaceholders('Hi {{name}}', '{{otp}} and {{name}}', null)).toEqual(['name', 'otp']);
  });

  it('picks sensible sample values', () => {
    expect(sampleValueFor('otp')).toBe('482913');
    expect(sampleValueFor('passengerName')).toBe('Asha Rao');
    expect(sampleValueFor('weirdThing')).toBe('Sample weirdThing');
  });

  it('fills known placeholders, keeps unknown ones, and escapes HTML values', () => {
    const values = { name: '<b>Asha</b>' };
    expect(fillPlaceholders('Hi {{name}} {{other}}', values, true)).toBe('Hi &lt;b&gt;Asha&lt;/b&gt; {{other}}');
    expect(fillPlaceholders('Hi {{name}}', values, false)).toBe('Hi <b>Asha</b>');
  });

  it('flags markup the visual editor would rewrite', () => {
    expect(isAdvancedHtml('<table><tr><td>x</td></tr></table>')).toBe(true);
    expect(isAdvancedHtml('<p>Hello <strong>there</strong></p>')).toBe(false);
  });

  it('wraps fragments but leaves full pages alone', () => {
    expect(buildPreviewDocument('<p>x</p>')).toContain('<body><p>x</p></body>');
    const page = '<html><body>x</body></html>';
    expect(buildPreviewDocument(page)).toBe(page);
  });

  it('turns HTML into plain text with line breaks', () => {
    expect(htmlToPlainText('<p>Line one</p><p>Line two<br>three</p>')).toBe('Line one\nLine two\nthree');
  });
});
