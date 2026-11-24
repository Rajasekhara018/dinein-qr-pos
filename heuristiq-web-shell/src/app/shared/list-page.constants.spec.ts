import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZE_OPTIONS } from './list-page.constants';

describe('list-page constants', () => {
  it('offers ascending page sizes', () => {
    expect(LIST_PAGE_SIZE_OPTIONS).toEqual([10, 20, 50]);
  });

  it('starts every list on a size the footer actually offers', () => {
    expect(DEFAULT_LIST_PAGE_SIZE).toBe(10);
    expect(LIST_PAGE_SIZE_OPTIONS).toContain(DEFAULT_LIST_PAGE_SIZE);
  });
});
