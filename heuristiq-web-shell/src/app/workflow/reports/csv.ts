/**
 * Builds CSV text: quoted where needed, and safe to open in a spreadsheet. A cell that starts with = + - or @ would be
 * run as a formula there (a step or person named "=HYPERLINK(...)" could do harm), so it is prefixed with an apostrophe.
 */
export function csvCell(value: unknown): string {
  let text = value === null || value === undefined ? '' : String(value);
  if (typeof value === 'string' && /^[=+\-@\t\r]/.test(text)) {
    text = "'" + text;
  }
  return /[",\r\n]/.test(text) ? '"' + text.replace(/"/g, '""') + '"' : text;
}

export function csvRow(cells: unknown[]): string {
  return cells.map(csvCell).join(',');
}

/** Saves text as a file the user downloads (UTF-8 with a BOM so spreadsheets read non-English text correctly). */
export function downloadCsv(fileName: string, lines: string[]): void {
  const blob = new Blob(['\uFEFF' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
}
