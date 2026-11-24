import { HqAddress } from './hq-address.model';

/** Indian 6-digit pincode (no leading zero). */
export const HQ_INDIAN_PINCODE_PATTERN = /^[1-9][0-9]{5}$/;

export const HQ_ADDRESS_DEFAULT_COUNTRY = 'India';

export function hqEmptyAddress(): HqAddress {
  return { lines: '', city: '', state: '', country: HQ_ADDRESS_DEFAULT_COUNTRY, pincode: '', landmark: '' };
}
