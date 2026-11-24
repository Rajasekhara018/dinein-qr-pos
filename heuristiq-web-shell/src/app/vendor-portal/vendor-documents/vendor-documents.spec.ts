import { HqDocument } from 'hq-shared-lib';
import { vi } from 'vitest';
import { VendorDocuments } from './vendor-documents';

const doc = (id: string) => ({ id }) as HqDocument;

describe('VendorDocuments', () => {
  const router = { getCurrentNavigation: vi.fn((): unknown => null), navigate: vi.fn() };
  const notify = { error: vi.fn(), success: vi.fn() };
  const translate = { instant: vi.fn((key: string, params?: object) => key + (params ? JSON.stringify(params) : '')) };

  function make(vendorId: string | null = 'v1') {
    router.getCurrentNavigation.mockReturnValue(vendorId ? { extras: { state: { vendor_id: vendorId } } } : null);
    const component = new VendorDocuments(router as never, notify as never, translate as never);
    component.ngOnInit();
    return component;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    history.replaceState(null, '');
    window.scrollTo = vi.fn() as never;
  });

  it('lists the five required documents with translated labels', () => {
    const c = make();
    expect(c.docs.map((d) => d.id)).toEqual(['gst_cert', 'pan_card', 'trade_licence', 'cancelled_cheque', 'fleet_agreement']);
    expect(c.docs[0].label).toBe('vendorPortal.documents.requirements.gst_cert');
    expect(c.categoryFor('pan_card')).toBe('vendor-pan_card');
  });

  describe('finding the application id', () => {
    it('takes it from the navigation state', () => {
      expect(make('nav1').vendorId).toBe('nav1');
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('falls back to the history state after a reload', () => {
      history.replaceState({ vendor_id: 'hist1' }, '');
      expect(make(null).vendorId).toBe('hist1');
    });

    it('falls back to the id saved at registration', () => {
      sessionStorage.setItem('railcab_vendor_application_id', 'ses1');
      expect(make(null).vendorId).toBe('ses1');
    });

    it('sends the visitor back to registration when there is no application', () => {
      const c = make(null);
      expect(c.vendorId).toBeNull();
      expect(router.navigate).toHaveBeenCalledWith(['/vendor/register']);
    });
  });

  describe('progress', () => {
    it('counts uploaded documents and reports what is missing', () => {
      const c = make();
      expect(c.uploadedCount).toBe(0);
      expect(c.allUploaded).toBe(false);
      c.onChange('gst_cert', doc('d1'));
      c.onChange('pan_card', doc('d2'));
      expect(c.uploadedCount).toBe(2);
      expect(c.progressPercent).toBe(40);
      expect(c.hasFile('gst_cert')).toBe(true);
      expect(c.hasFile('trade_licence')).toBe(false);
      expect(c.missingDocs.map((d) => d.id)).toEqual(['trade_licence', 'cancelled_cheque', 'fleet_agreement']);
    });

    it('removing a document takes it out of the count', () => {
      const c = make();
      c.onChange('gst_cert', doc('d1'));
      c.onChange('gst_cert', null);
      expect(c.uploadedCount).toBe(0);
    });

    it('flags missing documents only after a submit attempt', () => {
      const c = make();
      expect(c.isMissing('gst_cert')).toBe(false);
      c.onSubmit();
      expect(c.isMissing('gst_cert')).toBe(true);
      c.onChange('gst_cert', doc('d1'));
      expect(c.isMissing('gst_cert')).toBe(false);
    });
  });

  describe('submit', () => {
    it('refuses and lists the missing documents', () => {
      const c = make();
      c.onChange('gst_cert', doc('d1'));
      c.onSubmit();
      expect(c.isSuccess).toBe(false);
      const message = notify.error.mock.calls[0][0] as string;
      expect(message).toContain('vendorPortal.documents.messages.uploadAllRequired');
      expect(message).toContain('vendorPortal.documents.requirements.pan_card, vendorPortal.documents.requirements.trade_licence');
    });

    it('succeeds once everything is uploaded and forgets the saved application id', () => {
      sessionStorage.setItem('railcab_vendor_application_id', 'v1');
      const c = make();
      c.docs.forEach((d) => c.onChange(d.id, doc(d.id)));
      expect(c.allUploaded).toBe(true);
      c.onSubmit();
      expect(c.isSuccess).toBe(true);
      expect(notify.success).toHaveBeenCalledWith('vendorPortal.documents.messages.uploadSuccess');
      expect(sessionStorage.getItem('railcab_vendor_application_id')).toBeNull();
      expect(window.scrollTo).toHaveBeenCalled();
    });

    it('goes back to registration when the application id has been lost', () => {
      const c = make();
      c.vendorId = null;
      router.navigate.mockClear();
      c.onSubmit();
      expect(notify.error).toHaveBeenCalledWith('vendorPortal.documents.messages.vendorIdMissing');
      expect(router.navigate).toHaveBeenCalledWith(['/vendor/register']);
    });
  });

  it('shows a rejected upload as an error', () => {
    make().onRejected('Too big');
    expect(notify.error).toHaveBeenCalledWith('Too big');
  });
});
