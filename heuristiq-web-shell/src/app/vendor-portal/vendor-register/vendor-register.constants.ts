/**
 * Translation keys of the message a touched field shows, by validator error, in the order they are checked. A field
 * shows only the first message that applies (the old screen could stack several under one box). '*' matches any
 * error, for fields that show one message whenever they are invalid.
 */
export type VendorFieldErrorKeys = ReadonlyArray<readonly [errors: readonly string[], key: string]>;

export const VENDOR_FIELD_ERROR_KEYS: Readonly<Record<string, VendorFieldErrorKeys>> = {
  business_name: [[['*'], 'vendorPortal.register.business.name.required']],
  owner_name: [[['*'], 'vendorPortal.register.business.ownerName.required']],
  email: [[['*'], 'vendorPortal.register.business.email.required']],
  city: [[['*'], 'vendorPortal.register.business.city.required']],
  gst_number: [
    [['required'], 'vendorPortal.register.tax.gst.required'],
    [['minlength', 'maxlength'], 'vendorPortal.register.tax.gst.length'],
    [['pattern'], 'vendorPortal.register.tax.gst.invalid'],
  ],
  pan_number: [
    [['required'], 'vendorPortal.register.tax.pan.required'],
    [['minlength', 'maxlength'], 'vendorPortal.register.tax.pan.length'],
  ],
  account_number: [[['*'], 'vendorPortal.register.bank.accountNumber.required']],
  ifsc: [
    [['required'], 'vendorPortal.register.bank.ifsc.required'],
    [['minlength', 'maxlength'], 'vendorPortal.register.bank.ifsc.length'],
  ],
  account_name: [[['*'], 'vendorPortal.register.bank.accountName.required']],
};
