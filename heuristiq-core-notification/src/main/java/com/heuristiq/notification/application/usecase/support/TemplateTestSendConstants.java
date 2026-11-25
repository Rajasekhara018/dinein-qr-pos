package com.heuristiq.notification.application.usecase.support;

import java.time.Duration;
import java.util.Set;
import java.util.regex.Pattern;

import com.heuristiq.notification.domain.model.NotificationChannel;

/** Fixed values of the template "send a test" feature. */
public final class TemplateTestSendConstants {

    private TemplateTestSendConstants() {
    }

    /** Channels a test can go to; push and in-app need a device token or user id, not a typed recipient. */
    public static final Set<NotificationChannel> TESTABLE_CHANNELS =
            Set.of(NotificationChannel.EMAIL, NotificationChannel.SMS, NotificationChannel.WHATSAPP);

    /** Marks every test so nobody mistakes it for a real notification. */
    public static final String TEST_PREFIX = "[TEST] ";

    public static final Duration RATE_WINDOW = Duration.ofHours(1);

    /** Upper bound on callers remembered by the limiter. */
    public static final int MAX_TRACKED_CALLERS = 5_000;

    public static final Pattern EMAIL = Pattern.compile("^[^@\\s<>,;]+@[^@\\s<>,;]+\\.[^@\\s<>,;]+$");

    /** International format: optional +, 8 to 15 digits (spaces, dashes and brackets are stripped first). */
    public static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{8,15}$");
}
