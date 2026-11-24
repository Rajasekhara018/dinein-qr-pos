import { formatSize, isAcceptedFile, hqStoredDocument, saveBlob } from './hq-file.util';

const file = (name: string, type = '') => new File(['x'], name, { type });

describe('isAcceptedFile', () => {
  it('accepts anything when the accept list is empty', () => {
    expect(isAcceptedFile(file('a.exe'), '')).toBe(true);
    expect(isAcceptedFile(file('a.exe'), ' , ')).toBe(true);
    expect(isAcceptedFile(file('a.exe'), undefined as unknown as string)).toBe(true);
  });

  it('matches extensions case-insensitively', () => {
    expect(isAcceptedFile(file('Scan.PDF'), '.pdf, .png')).toBe(true);
    expect(isAcceptedFile(file('scan.docx'), '.pdf,.png')).toBe(false);
  });

  it('matches wildcard and exact MIME types', () => {
    expect(isAcceptedFile(file('photo', 'image/jpeg'), 'image/*')).toBe(true);
    expect(isAcceptedFile(file('doc', 'application/pdf'), 'image/*,APPLICATION/PDF')).toBe(true);
    expect(isAcceptedFile(file('doc', 'text/plain'), 'image/*,application/pdf')).toBe(false);
  });

  it('rejects a MIME rule when the file has no type', () => {
    expect(isAcceptedFile(file('unknown'), 'application/pdf')).toBe(false);
  });
});

describe('formatSize', () => {
  it('is empty for no size', () => {
    expect(formatSize(0)).toBe('');
    expect(formatSize(null)).toBe('');
    expect(formatSize(undefined)).toBe('');
  });

  it('shows KB below a megabyte, never less than 1 KB', () => {
    expect(formatSize(100)).toBe('1 KB');
    expect(formatSize(2048)).toBe('2 KB');
    expect(formatSize(1024 * 1024 - 1)).toBe('1024 KB');
  });

  it('shows MB with one decimal from a megabyte up', () => {
    expect(formatSize(1024 * 1024)).toBe('1.0 MB');
    expect(formatSize(1.5 * 1024 * 1024)).toBe('1.5 MB');
  });
});

describe('saveBlob', () => {
  const originalCreate = URL.createObjectURL;
  const originalRevoke = URL.revokeObjectURL;
  let createObjectURL: ReturnType<typeof vi.fn>;
  let revokeObjectURL: ReturnType<typeof vi.fn>;
  let clicked: HTMLAnchorElement[];

  beforeEach(() => {
    vi.useFakeTimers();
    createObjectURL = vi.fn(() => 'blob:http://localhost/file-1');
    revokeObjectURL = vi.fn();
    URL.createObjectURL = createObjectURL as unknown as typeof URL.createObjectURL;
    URL.revokeObjectURL = revokeObjectURL as unknown as typeof URL.revokeObjectURL;
    clicked = [];
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      clicked.push(this);
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    URL.createObjectURL = originalCreate;
    URL.revokeObjectURL = originalRevoke;
  });

  it('clicks a download link for the blob, then revokes the URL', () => {
    const blob = new Blob(['data']);
    saveBlob(blob, 'report.pdf');
    expect(createObjectURL).toHaveBeenCalledWith(blob);
    expect(clicked).toHaveLength(1);
    expect(clicked[0].href).toBe('blob:http://localhost/file-1');
    expect(clicked[0].download).toBe('report.pdf');
    expect(revokeObjectURL).not.toHaveBeenCalled();
    vi.runAllTimers();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:http://localhost/file-1');
  });

  it('names the file "document" when no name is given', () => {
    saveBlob(new Blob(['data']), '');
    expect(clicked[0].download).toBe('document');
  });
});

describe('hqStoredDocument', () => {
  it('builds an application-stored record with the given id, name, type and size', () => {
    const doc = hqStoredDocument('p1', 'Photo 1', 'image/jpeg', 2048);
    expect(doc).toEqual(expect.objectContaining({
      id: 'p1', originalFileName: 'Photo 1', storedFileName: 'Photo 1', contentType: 'image/jpeg', sizeBytes: 2048,
      provider: 'APPLICATION', status: 'ACTIVE', publicUrl: null,
    }));
  });

  it('defaults the size to 0 when the screen does not know it', () => {
    expect(hqStoredDocument('h', 'Horoscope', 'application/pdf').sizeBytes).toBe(0);
  });
});
