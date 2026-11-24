/**
 * Matches Rider_UAM's RAddress shape (lines/city/state/country/pincode/landmark) -- keep both in
 * sync. The platform is India-only today, so pincode validation is the Indian 6-digit format.
 */
export interface HqAddress {
  lines: string;
  city: string;
  state: string;
  country: string;
  pincode: string;
  landmark?: string;
}
