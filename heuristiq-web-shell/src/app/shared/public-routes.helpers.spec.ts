import { isPublicPath } from './public-routes.helpers';

describe('isPublicPath', () => {
  it('matches the public pages with or without fragment, query or sub-path', () => {
    expect(isPublicPath('/track')).toBe(true);
    expect(isPublicPath('/track#abc')).toBe(true);
    expect(isPublicPath('/track?x=1')).toBe(true);
    expect(isPublicPath('/track/more')).toBe(true);
    expect(isPublicPath('/driver-onboarding#submissionId=abc')).toBe(true);
  });

  it('does not match signed-in screens or look-alike paths', () => {
    expect(isPublicPath('/today')).toBe(false);
    expect(isPublicPath('/tracker')).toBe(false);
    expect(isPublicPath('/driver-onboarding-admin')).toBe(false);
    expect(isPublicPath('')).toBe(false);
    expect(isPublicPath(null)).toBe(false);
    expect(isPublicPath(undefined)).toBe(false);
  });
});
