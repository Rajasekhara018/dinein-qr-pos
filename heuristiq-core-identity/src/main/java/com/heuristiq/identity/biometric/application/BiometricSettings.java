package com.heuristiq.identity.biometric.application;

import java.time.Duration;

/**
 * The deployment's biometric sign-in settings as the use cases see them (bound from {@code railcab.uam.biometric.*}).
 *
 * @param reauthWindow      how recent an interactive sign-in must be to enrol without the current password
 * @param challengeTtl      how long a challenge may be answered
 * @param inactivityExpiry  an enrolled device unused for this long expires
 * @param maxFailedAttempts failed sign-ins per installation allowed in {@code attemptWindow}; the next is refused (429)
 * @param attemptWindow     length of the failed-attempt window, from the first failure
 */
public record BiometricSettings(Duration reauthWindow, Duration challengeTtl, Duration inactivityExpiry, int maxFailedAttempts,
                                Duration attemptWindow) {
}
