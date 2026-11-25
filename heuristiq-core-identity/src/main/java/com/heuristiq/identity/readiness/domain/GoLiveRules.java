package com.heuristiq.identity.readiness.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pure rules of the go-live guard: which steps of a readiness result stop an application from going live.
 * <ul>
 *   <li>Only BLOCK steps count; WARN steps never block.</li>
 *   <li>A BLOCK step that is FAIL or UNKNOWN blocks (UNKNOWN is never treated as passed).</li>
 *   <li>{@code GO_LIVE_REVIEW} is derived from the other steps and is not listed again.</li>
 *   <li>{@code UAM_APPLICATION_IN_ORGANIZATION} is left out: it only says "the link is ACTIVE", which is exactly what the
 *       guard is about to switch on, so it would block every first activation.</li>
 * </ul>
 */
public final class GoLiveRules {

    private static final int FINGERPRINT_LENGTH = 32;

    private GoLiveRules() {
    }

    /** The steps that block going live, in guide order. */
    public static List<GoLiveBlocker> blockers(ApplicationReadiness readiness) {
        return readiness.steps().stream()
                .filter(step -> step.severity() == ReadinessSeverity.BLOCK)
                .filter(step -> !ReadinessCheckIds.GO_LIVE_REVIEW.equals(step.id()))
                .filter(step -> !ReadinessCheckIds.UAM_APPLICATION_IN_ORGANIZATION.equals(step.id()))
                .filter(step -> step.status() == ReadinessStatus.FAIL || step.status() == ReadinessStatus.UNKNOWN)
                .map(step -> new GoLiveBlocker(step.id(), step.status(), step.messageKey(), step.params(), step.fixPath()))
                .toList();
    }

    /** The ids of the blockers, sorted, comma separated. */
    public static String stepIds(List<GoLiveBlocker> blockers) {
        return blockers.stream().map(GoLiveBlocker::stepId).sorted().collect(Collectors.joining(","));
    }

    /** A short stable hash of the blocking set (order independent), used to tell "the same problem" from a new one. */
    public static String fingerprint(List<GoLiveBlocker> blockers) {
        if (blockers.isEmpty()) {
            return null;
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(stepIds(blockers).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, FINGERPRINT_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}
