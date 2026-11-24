import { describe, expect, it } from 'vitest';
import { carriesPlatformToken, platformOrigins } from './auth-interceptor.helpers';

describe('auth-interceptor.helpers', () => {
  const origins = platformOrigins(
    { apiUrl: 'https://ops.example.net/api', nested: { other: 'https://gw.example.net/x' }, socket: 'ws://host:1', note: 'not a url', n: 3 },
    'https://page.example.net',
  );

  it('collects the origin of every http(s) address in the settings and the page itself', () => {
    expect([...origins].sort()).toEqual(['https://gw.example.net', 'https://ops.example.net', 'https://page.example.net']);
  });

  it('lets the token go to this site and to the platform servers', () => {
    expect(carriesPlatformToken('/api/v1/uam/me', origins)).toBe(true);
    expect(carriesPlatformToken('assets/i18n/en.json', origins)).toBe(true);
    expect(carriesPlatformToken('https://ops.example.net/api/v1/x?a=1', origins)).toBe(true);
    expect(carriesPlatformToken('https://gw.example.net/other', origins)).toBe(true);
  });

  it('never sends the token anywhere else, such as a signed storage link', () => {
    expect(carriesPlatformToken('https://bucket.s3.ap-south-1.amazonaws.com/doc.png?X-Amz-Signature=abc', origins)).toBe(false);
    expect(carriesPlatformToken('https://evil.example.org/', origins)).toBe(false);
    expect(carriesPlatformToken('https://ops.example.net.evil.org/', origins)).toBe(false);
  });

  it('refuses an address that cannot be read and ignores settings that cannot be read', () => {
    expect(carriesPlatformToken('http://', origins)).toBe(false);
    expect(platformOrigins({ a: 'http://', b: null }, 'https://p.example').size).toBe(1);
  });
});
