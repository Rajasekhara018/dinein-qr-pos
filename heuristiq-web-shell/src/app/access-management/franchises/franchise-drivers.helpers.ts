import { DRIVER_ERROR_KEYS, DRIVER_FALLBACK_ERROR_KEY } from './franchise-drivers.constants';

/** The translation key of the message for a failed driver list, from the HTTP status of the answer. */
export function driverErrorKey(status: number | undefined): string {
  return (status !== undefined && DRIVER_ERROR_KEYS[status]) || DRIVER_FALLBACK_ERROR_KEY;
}

/** The query parameters that preset the franchise on the driver onboarding form; the code only when it is known. */
export function driverOnboardingQuery(franchiseId: string, franchiseCode: string): Record<string, string> {
  return franchiseCode ? { franchiseId, franchiseCode } : { franchiseId };
}
