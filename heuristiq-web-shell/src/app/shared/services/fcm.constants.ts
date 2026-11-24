/** localStorage keys for this browser's push registration. */
export const FCM_TOKEN_KEY = 'railcab_web_fcm_token';
/** JSON {userId, at} -- who the stored token was last registered for, and when. */
export const FCM_REGISTRATION_KEY = 'railcab_web_fcm_registration';

/** Identifies this client to NMS (the Rider_Web shell, as opposed to the customer/driver mobile apps). */
export const FCM_APP_ID = 'RIDER_WEB';

/** Re-register at least this often so NMS's "last seen" for the device stays meaningful. */
export const FCM_REREGISTER_AFTER_MS = 24 * 60 * 60 * 1000;
