import { InjectionToken } from '@angular/core';
import { HqImageCropper } from './hq-common.models';

/** What a consuming app supplies through HqSharedLibModule.forRoot({...}). */
export interface HqSharedLibConfig {
  /** Rider_ApiGateway base, e.g. 'http://host:8999/api'. DMS and UAM are reached through it
   *  (DMS only trusts gateway-injected identity headers, so it is never called directly). */
  gatewayUrl: string;
  /** Rider_AdminAPI base used for the vendor / vehicle lookups, e.g. 'http://host:9005/api/v1'
   *  (direct) or '<gatewayUrl>/v1/admin' (via the gateway's admin route). */
  adminApiUrl: string;
  /** Id of the signed-in user -- the default DMS ownerId for uploads. */
  currentUserId: () => string | null | undefined;
}

export const HQ_CONFIG = new InjectionToken<HqSharedLibConfig>('HQ_CONFIG');

/** Resolved endpoint bases derived from the config. */
export function hqUrls(config: HqSharedLibConfig) {
  const gateway = config.gatewayUrl.replace(/\/+$/, '');
  return {
    /** gateway dms-api route: /api/v1/dms/** -> Rider_DMS /api/v1/** */
    dms: `${gateway}/v1/dms/documents`,
    /** gateway uam-api route: /api/v1/uam/** -> Rider_UAM /api/v1/uam/** */
    uam: `${gateway}/v1/uam`,
    /** gateway search-api route: /api/v1/search/** -> Rider_SearchAPI; the index's getall API is /elastic/{model}/getall */
    search: `${gateway}/v1/search`,
    admin: config.adminApiUrl.replace(/\/+$/, ''),
  };
}

/** Mirrors Rider_DMS DocumentValidationService defaults (allowed extensions, 10 MB max). The
 *  gateway caps the whole multipart body at 10 MB too, so the client stays just under it. */
export const HQ_DMS_ACCEPT = '.pdf,.png,.jpg,.jpeg,.webp,.doc,.docx,.xls,.xlsx,.csv,.txt';
export const HQ_DMS_MAX_SIZE_MB = 9.9;

/** Optional: an app that bundles an image cropper provides this; hq-doc-upload's enableCrop
 *  is a no-op without it. */
export const HQ_IMAGE_CROPPER = new InjectionToken<HqImageCropper>('HQ_IMAGE_CROPPER');
