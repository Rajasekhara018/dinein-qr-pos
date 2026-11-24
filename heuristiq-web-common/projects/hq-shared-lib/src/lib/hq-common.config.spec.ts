import { HQ_DMS_ACCEPT, HQ_DMS_MAX_SIZE_MB, hqUrls } from './hq-common.config';

describe('hqUrls', () => {
  it('derives the DMS, UAM and search routes from the gateway and keeps the admin base', () => {
    const urls = hqUrls({ gatewayUrl: 'http://gw:8999/api', adminApiUrl: 'http://admin:9005/api/v1', currentUserId: () => null });
    expect(urls).toEqual({
      dms: 'http://gw:8999/api/v1/dms/documents',
      uam: 'http://gw:8999/api/v1/uam',
      search: 'http://gw:8999/api/v1/search',
      admin: 'http://admin:9005/api/v1',
    });
  });

  it('strips trailing slashes so routes never contain a double slash', () => {
    const urls = hqUrls({ gatewayUrl: 'http://gw/api///', adminApiUrl: 'http://gw/api/v1/admin/', currentUserId: () => undefined });
    expect(urls.dms).toBe('http://gw/api/v1/dms/documents');
    expect(urls.admin).toBe('http://gw/api/v1/admin');
  });
});

describe('DMS upload limits', () => {
  it('stay just under the gateway 10 MB cap and list the DMS extensions', () => {
    expect(HQ_DMS_MAX_SIZE_MB).toBeLessThan(10);
    expect(HQ_DMS_ACCEPT.split(',')).toContain('.pdf');
    expect(HQ_DMS_ACCEPT.split(',').every((ext) => ext.startsWith('.'))).toBe(true);
  });
});
