package com.heuristiq.uam.refreshToken;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.config.UamProperties;
import com.heuristiq.uam.enums.UamEnums.PrincipalType;
import com.heuristiq.uam.helper.AuditInfo;
import com.heuristiq.uam.refreshToken.RefreshTokenServiceUseCase.FindRedeemableByHashInput;
import com.heuristiq.uam.refreshToken.RefreshTokenServiceUseCase.FindValidByHashInput;
import com.heuristiq.uam.refreshToken.RefreshTokenServiceUseCase.IssueInput;
import com.heuristiq.uam.refreshToken.RefreshTokenServiceUseCase.ListActiveForPrincipalInput;
import com.heuristiq.uam.refreshToken.RefreshTokenServiceUseCase.RevokeAllForPrincipalInput;
import com.heuristiq.uam.refreshToken.RefreshTokenServiceUseCase.RevokeInput;
import com.heuristiq.uam.refreshToken.RefreshTokenServiceUseCase.RotateInput;
import com.heuristiq.uam.refreshToken.RefreshTokenServiceUseCase.RotateOutput;
import com.heuristiq.uam.refreshToken.config.RefreshTokenProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Rotation, grace-window replay, reuse detection and races, against an in-memory store with atomic conditional updates. */
class RefreshTokenServiceTest {
    private static final Instant START = Instant.parse("2026-11-08T10:00:00Z");
    private static final String SECRET = "test-secret-test-secret-test-secret-test-secret";

    private MutableClock clock;
    private InMemoryRefreshTokens repository;
    private RefreshTokenSuccessorDeriver deriver;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        repository = new InMemoryRefreshTokens();
        deriver = new RefreshTokenSuccessorDeriver(properties(SECRET));
        service = new RefreshTokenService(repository, deriver, new RefreshTokenProperties(30, 1), clock);
    }

    // ---------------------------------------------------------------- issue / revoke / list (unchanged behaviour)

    @Test
    void issueBuildsAnActiveTokenThatStartsItsOwnFamily() {
        LocalDateTime exp = now().plusDays(1);

        RefreshToken token = service.issue(new IssueInput("u1", PrincipalType.USER, "web", "hash", "s1", "d1", exp, "1.2.3.4", "UA")).token();

        assertThat(token.getTokenId()).isNotBlank();
        assertThat(token.getFamilyId()).isEqualTo(token.getTokenId());
        assertThat(token.getTokenHash()).isEqualTo("hash");
        assertThat(token.getRevoked()).isFalse();
        assertThat(token.getRotationCount()).isZero();
        assertThat(token.getExpiresAt()).isEqualTo(exp);
        assertThat(token.getIssuedAt()).isEqualTo(now());
        assertThat(token.getAudit().getCreatedAt()).isEqualTo(now());
    }

    @Test
    void revokeMarksTokenWithReasonAndTimeAndFailsWhenUnknown() {
        RefreshToken token = repository.save(RefreshToken.builder().tokenId("t").audit(AuditInfo.builder().build()).build());

        service.revoke(new RevokeInput("t"));

        RefreshToken stored = repository.findByTokenId("t").orElseThrow();
        assertThat(stored.getRevoked()).isTrue();
        assertThat(stored.getRevokedReason()).isEqualTo(RefreshTokenConstants.REASON_REVOKED_BY_ADMIN);
        assertThat(stored.getRevokedAt()).isEqualTo(now());
        assertThat(stored.getAudit().getUpdatedAt()).isEqualTo(now());
        assertThat(token.getId()).isNotNull();
        assertThatThrownBy(() -> service.revoke(new RevokeInput("x")))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void revokeWorksForTokenWithoutAudit() {
        repository.save(RefreshToken.builder().tokenId("t").build());
        service.revoke(new RevokeInput("t"));
        assertThat(repository.findByTokenId("t").orElseThrow().getRevoked()).isTrue();
    }

    @Test
    void revokeAllForPrincipalRevokesEveryActiveTokenOfThatPrincipalOnly() {
        repository.save(RefreshToken.builder().tokenId("a").principalId("u1").audit(AuditInfo.builder().build()).build());
        repository.save(RefreshToken.builder().tokenId("b").principalId("u1").build());
        repository.save(RefreshToken.builder().tokenId("c").principalId("u2").build());

        service.revokeAllForPrincipal(new RevokeAllForPrincipalInput("u1"));

        assertThat(repository.findByTokenId("a").orElseThrow().getRevoked()).isTrue();
        assertThat(repository.findByTokenId("b").orElseThrow().getRevoked()).isTrue();
        assertThat(repository.findByTokenId("c").orElseThrow().getRevoked()).isFalse();
    }

    @Test
    void listActiveReturnsOnlyActiveTokensOfThePrincipal() {
        repository.save(RefreshToken.builder().tokenId("a").principalId("u1").build());
        repository.save(RefreshToken.builder().tokenId("b").principalId("u1").revoked(true).build());
        assertThat(service.listActiveForPrincipal(new ListActiveForPrincipalInput("u1")).tokens())
                .extracting(RefreshToken::getTokenId).containsExactly("a");
    }

    @Test
    void findValidByHashRejectsRevokedAndExpiredTokens() {
        repository.save(RefreshToken.builder().tokenId("1").tokenHash("ok").expiresAt(now().plusHours(1)).build());
        repository.save(RefreshToken.builder().tokenId("2").tokenHash("ne").build());
        repository.save(RefreshToken.builder().tokenId("3").tokenHash("rv").revoked(true).build());
        repository.save(RefreshToken.builder().tokenId("4").tokenHash("ex").expiresAt(now().minusMinutes(1)).build());

        assertThat(service.findValidByHash(new FindValidByHashInput("ok")).token()).isPresent();
        assertThat(service.findValidByHash(new FindValidByHashInput("ne")).token()).isPresent();
        assertThat(service.findValidByHash(new FindValidByHashInput("rv")).token()).isEmpty();
        assertThat(service.findValidByHash(new FindValidByHashInput("ex")).token()).isEmpty();
        assertThat(service.findValidByHash(new FindValidByHashInput("none")).token()).isEmpty();
    }

    @Test
    void findRedeemableAcceptsActiveAndRotatedButNotLogoutRevokedOrExpired() {
        repository.save(RefreshToken.builder().tokenId("1").tokenHash("active").build());
        repository.save(RefreshToken.builder().tokenId("2").tokenHash("rotated").revoked(true)
                .revokedReason(RefreshTokenConstants.REASON_ROTATED).build());
        repository.save(RefreshToken.builder().tokenId("3").tokenHash("logout").revoked(true)
                .revokedReason(RefreshTokenConstants.REASON_REVOKED_BY_ADMIN).build());
        repository.save(RefreshToken.builder().tokenId("4").tokenHash("expired").expiresAt(now().minusSeconds(1)).build());

        assertThat(service.findRedeemableByHash(new FindRedeemableByHashInput("active")).token()).isPresent();
        assertThat(service.findRedeemableByHash(new FindRedeemableByHashInput("rotated")).token()).isPresent();
        assertThat(service.findRedeemableByHash(new FindRedeemableByHashInput("logout")).token()).isEmpty();
        assertThat(service.findRedeemableByHash(new FindRedeemableByHashInput("expired")).token()).isEmpty();
        assertThat(service.findRedeemableByHash(new FindRedeemableByHashInput("missing")).token()).isEmpty();
    }

    // ---------------------------------------------------------------- rotation

    @Test
    void rotateRevokesThePresentedTokenAndIssuesADerivedSuccessorInTheSameFamily() {
        RefreshToken first = signIn("raw-1");

        RotateOutput out = service.rotate(new RotateInput(first, "raw-1", now().plusDays(30)));

        assertThat(out.graceReplay()).isFalse();
        assertThat(out.rawToken()).isEqualTo(deriver.deriveSuccessor("raw-1", out.token().getTokenId()));
        assertThat(out.token().getTokenHash()).isEqualTo(RefreshTokenHashing.sha256Hex(out.rawToken()));
        assertThat(out.token().getFamilyId()).isEqualTo(first.getFamilyId());
        assertThat(out.token().getRotationCount()).isEqualTo(1);
        assertThat(out.token().getSessionId()).isEqualTo("s1");
        assertThat(out.token().getExpiresAt()).isEqualTo(now().plusDays(30));
        RefreshToken old = stored(first);
        assertThat(old.getRevoked()).isTrue();
        assertThat(old.getRevokedReason()).isEqualTo(RefreshTokenConstants.REASON_ROTATED);
        assertThat(old.getRevokedAt()).isEqualTo(now());
        assertThat(old.getSuccessorTokenId()).isEqualTo(out.token().getTokenId());
        assertThat(repository.activeCount()).isEqualTo(1);
    }

    @Test
    void rotateOfALegacyTokenWithoutFamilyOrCountStartsAFamilyAtThatToken() {
        RefreshToken legacy = repository.save(RefreshToken.builder().tokenId("legacy").principalId("u1").rotationCount(null)
                .tokenHash(RefreshTokenHashing.sha256Hex("raw")).build());

        RotateOutput out = service.rotate(new RotateInput(legacy, "raw", null));

        assertThat(out.token().getRotationCount()).isEqualTo(1);
        assertThat(out.token().getFamilyId()).isEqualTo("legacy");
        assertThat(stored(legacy).getFamilyId()).isEqualTo("legacy");
    }

    // ---------------------------------------------------------------- grace window

    @Test
    void replayInsideTheGraceWindowSucceedsOnceAndReturnsTheSameSuccessor() {
        RefreshToken first = signIn("raw-1");
        RotateOutput rotated = service.rotate(new RotateInput(first, "raw-1", now().plusDays(30)));
        clock.advance(Duration.ofSeconds(10));

        RotateOutput replay = service.rotate(new RotateInput(stored(first), "raw-1", now().plusDays(30)));

        assertThat(replay.graceReplay()).isTrue();
        assertThat(replay.rawToken()).isEqualTo(rotated.rawToken());
        assertThat(replay.token().getTokenId()).isEqualTo(rotated.token().getTokenId());
        assertThat(repository.size()).as("no new token minted").isEqualTo(2);
        assertThat(stored(first).getGraceRedemptions()).isEqualTo(1);

        // the allowance (1) is used up: a further replay is reuse and revokes the family
        assertThatThrownBy(() -> service.rotate(new RotateInput(stored(first), "raw-1", now().plusDays(30))))
                .satisfies(RefreshTokenServiceTest::isRefreshInvalid);
        assertThat(repository.activeCount()).isZero();
        assertThat(stored(rotated.token()).getRevokedReason()).isEqualTo(RefreshTokenConstants.REASON_REUSE_DETECTED);
    }

    @Test
    void replayInsideTheWindowFollowsAFurtherRotationToTheCurrentToken() {
        RefreshToken first = signIn("raw-1");
        RotateOutput second = service.rotate(new RotateInput(first, "raw-1", now().plusDays(30)));
        clock.advance(Duration.ofSeconds(5));
        RotateOutput third = service.rotate(new RotateInput(second.token(), second.rawToken(), now().plusDays(30)));
        clock.advance(Duration.ofSeconds(5));

        RotateOutput replay = service.rotate(new RotateInput(stored(first), "raw-1", now().plusDays(30)));

        assertThat(replay.token().getTokenId()).isEqualTo(third.token().getTokenId());
        assertThat(replay.rawToken()).isEqualTo(third.rawToken());
    }

    @Test
    void reuseAfterTheGraceWindowRevokesTheWholeFamilyButNotOtherSignIns() {
        RefreshToken first = signIn("raw-1");
        RefreshToken otherDevice = repository.save(RefreshToken.builder().tokenId("other").familyId("other").principalId("u1")
                .sessionId("s2").tokenHash("h2").build());
        RotateOutput rotated = service.rotate(new RotateInput(first, "raw-1", now().plusDays(30)));
        clock.advance(Duration.ofSeconds(31));

        assertThatThrownBy(() -> service.rotate(new RotateInput(stored(first), "raw-1", now().plusDays(30))))
                .satisfies(RefreshTokenServiceTest::isRefreshInvalid);

        RefreshToken successor = stored(rotated.token());
        assertThat(successor.getRevoked()).isTrue();
        assertThat(successor.getRevokedReason()).isEqualTo(RefreshTokenConstants.REASON_REUSE_DETECTED);
        assertThat(stored(otherDevice).getRevoked()).isFalse();
    }

    @Test
    void reuseDetectionRevokesTheFamilyOnlyForALateReplay() {
        RefreshToken first = signIn("raw-1");
        assertThat(service.revokeFamilyOnReuse(first)).as("an active token is not a reuse").isFalse();

        RotateOutput rotated = service.rotate(new RotateInput(first, "raw-1", now().plusDays(30)));
        assertThat(service.revokeFamilyOnReuse(stored(first))).as("a replay inside the window is not a reuse").isFalse();
        assertThat(stored(rotated.token()).getRevoked()).isFalse();

        clock.advance(Duration.ofSeconds(31));
        assertThat(service.revokeFamilyOnReuse(stored(first))).isTrue();
        assertThat(stored(rotated.token()).getRevoked()).isTrue();
        assertThat(stored(rotated.token()).getRevokedReason()).isEqualTo(RefreshTokenConstants.REASON_REUSE_DETECTED);
        assertThat(service.revokeFamilyOnReuse(null)).isFalse();
    }

    @Test
    void zeroGraceSecondsTurnsTheWindowOff() {
        service = new RefreshTokenService(repository, deriver, new RefreshTokenProperties(0, 1), clock);
        RefreshToken first = signIn("raw-1");
        service.rotate(new RotateInput(first, "raw-1", null));

        assertThatThrownBy(() -> service.rotate(new RotateInput(stored(first), "raw-1", null)))
                .satisfies(RefreshTokenServiceTest::isRefreshInvalid);
        assertThat(repository.activeCount()).isZero();
    }

    @Test
    void legacyRotatedTokenWithoutFamilyRevokesItsSessionOnReuse() {
        repository.save(RefreshToken.builder().tokenId("old").principalId("u1").sessionId("s1").revoked(true)
                .revokedReason(RefreshTokenConstants.REASON_ROTATED).tokenHash("x").build());
        RefreshToken sameSession = repository.save(RefreshToken.builder().tokenId("cur").principalId("u1").sessionId("s1").build());
        RefreshToken otherSession = repository.save(RefreshToken.builder().tokenId("oth").principalId("u1").sessionId("s9").build());

        assertThatThrownBy(() -> service.rotate(new RotateInput(repository.findByTokenId("old").orElseThrow(), "raw", null)))
                .satisfies(RefreshTokenServiceTest::isRefreshInvalid);

        assertThat(stored(sameSession).getRevoked()).isTrue();
        assertThat(stored(otherSession).getRevoked()).isFalse();
    }

    @Test
    void legacyRotatedTokenWithoutSessionRevokesAllOfThePrincipalsTokens() {
        repository.save(RefreshToken.builder().tokenId("old").principalId("u1").revoked(true)
                .revokedReason(RefreshTokenConstants.REASON_ROTATED).build());
        RefreshToken any = repository.save(RefreshToken.builder().tokenId("cur").principalId("u1").sessionId("s1").build());

        assertThatThrownBy(() -> service.rotate(new RotateInput(repository.findByTokenId("old").orElseThrow(), "raw", null)))
                .isInstanceOf(UamException.class);
        assertThat(stored(any).getRevoked()).isTrue();
    }

    @Test
    void graceReplayFailsWhenTheSuccessorWasLoggedOutOrExpiredOrCannotBeDerived() {
        // logged out after rotation
        RefreshToken a = signIn("raw-a");
        RotateOutput aNext = service.rotate(new RotateInput(a, "raw-a", null));
        service.revoke(new RevokeInput(aNext.token().getTokenId()));
        assertThatThrownBy(() -> service.rotate(new RotateInput(stored(a), "raw-a", null)))
                .satisfies(RefreshTokenServiceTest::isRefreshInvalid);

        // successor expired
        RefreshToken b = signIn("raw-b");
        service.rotate(new RotateInput(b, "raw-b", now().plusSeconds(1)));
        clock.advance(Duration.ofSeconds(2));
        assertThatThrownBy(() -> service.rotate(new RotateInput(stored(b), "raw-b", null)))
                .satisfies(RefreshTokenServiceTest::isRefreshInvalid);

        // a successor whose hash does not match the derivation (issued before derivation existed) or is missing
        repository.save(RefreshToken.builder().tokenId("c").familyId("c").principalId("u1").revoked(true)
                .revokedReason(RefreshTokenConstants.REASON_ROTATED).revokedAt(now()).successorTokenId("c2").build());
        repository.save(RefreshToken.builder().tokenId("c2").familyId("c").principalId("u1").tokenHash("random").build());
        assertThatThrownBy(() -> service.rotate(new RotateInput(repository.findByTokenId("c").orElseThrow(), "raw-c", null)))
                .satisfies(RefreshTokenServiceTest::isRefreshInvalid);
        repository.save(RefreshToken.builder().tokenId("d").familyId("d").principalId("u1").revoked(true)
                .revokedReason(RefreshTokenConstants.REASON_ROTATED).revokedAt(now()).successorTokenId("gone").build());
        assertThatThrownBy(() -> service.rotate(new RotateInput(repository.findByTokenId("d").orElseThrow(), "raw-d", null)))
                .satisfies(RefreshTokenServiceTest::isRefreshInvalid);
    }

    @Test
    void aTokenRevokedForAnotherReasonFailsWithoutRevokingAnything() {
        RefreshToken survivor = repository.save(RefreshToken.builder().tokenId("live").familyId("f").principalId("u1").build());
        RefreshToken logout = repository.save(RefreshToken.builder().tokenId("lo").familyId("f").principalId("u1").revoked(true)
                .revokedReason(RefreshTokenConstants.REASON_REVOKED_BY_ADMIN).build());

        assertThatThrownBy(() -> service.rotate(new RotateInput(logout, "raw", null)))
                .satisfies(RefreshTokenServiceTest::isRefreshInvalid);
        assertThat(stored(survivor).getRevoked()).isFalse();
    }

    @Test
    void aTokenThatVanishedAfterLosingTheRaceIsInvalid() {
        RefreshToken ghost = RefreshToken.builder().id("nope").tokenId("ghost").build();
        repository.forceLoseNextRotation = true;
        assertThatThrownBy(() -> service.rotate(new RotateInput(ghost, "raw", null)))
                .satisfies(RefreshTokenServiceTest::isRefreshInvalid);
    }

    // ---------------------------------------------------------------- concurrency

    @Test
    void twoConcurrentRotationsProduceExactlyOneNewTokenAndTheOtherGetsTheGraceResult() throws Exception {
        RefreshToken first = signIn("raw-1");
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Supplier<RotateOutput> attempt = () -> {
                try {
                    // each request read the token on its own before rotating, like two HTTP requests would
                    RefreshToken mine = stored(first);
                    barrier.await();
                    return service.rotate(new RotateInput(mine, "raw-1", now().plusDays(30)));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            };
            Future<RotateOutput> a = pool.submit(attempt::get);
            Future<RotateOutput> b = pool.submit(attempt::get);
            RotateOutput ra = a.get();
            RotateOutput rb = b.get();

            assertThat(List.of(ra.graceReplay(), rb.graceReplay())).containsExactlyInAnyOrder(false, true);
            assertThat(ra.rawToken()).isEqualTo(rb.rawToken());
            assertThat(ra.token().getTokenId()).isEqualTo(rb.token().getTokenId());
            assertThat(repository.activeCount()).as("exactly one live successor").isEqualTo(1);
            assertThat(stored(first).getSuccessorTokenId()).isEqualTo(ra.token().getTokenId());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void theLoserOfARaceRetiresTheSuccessorItStored() {
        RefreshToken first = signIn("raw-1");
        RotateOutput winner = service.rotate(new RotateInput(first, "raw-1", null));
        // a second request that read the token while it was still active
        RefreshToken staleCopy = RefreshToken.builder().id(first.getId()).tokenId(first.getTokenId()).familyId(first.getFamilyId())
                .principalId("u1").revoked(false).build();

        RotateOutput loser = service.rotate(new RotateInput(staleCopy, "raw-1", null));

        assertThat(loser.graceReplay()).isTrue();
        assertThat(loser.token().getTokenId()).isEqualTo(winner.token().getTokenId());
        assertThat(repository.all()).filteredOn(t -> RefreshTokenConstants.REASON_ROTATION_LOST.equals(t.getRevokedReason()))
                .hasSize(1);
        assertThat(repository.activeCount()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- wiring

    @Test
    void springConstructorUsesTheContextClockOrTheSystemClock() {
        @SuppressWarnings("unchecked")
        ObjectProvider<Clock> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique(any())).thenReturn(clock);
        RefreshTokenService wired = new RefreshTokenService(repository, deriver, new RefreshTokenProperties(30, 1), provider);
        assertThat(wired.issue(new IssueInput("u", PrincipalType.USER, "web", "h", "s", "d", null, null, null)).token().getIssuedAt())
                .isEqualTo(now());
    }

    @Test
    void deriverIsKeyedBySecretAndSuccessorIdAndRefusesAMissingSecret() {
        RefreshTokenSuccessorDeriver other = new RefreshTokenSuccessorDeriver(properties("another-secret-another-secret-another"));
        assertThat(deriver.deriveSuccessor("raw", "id")).isEqualTo(deriver.deriveSuccessor("raw", "id"))
                .isNotEqualTo(deriver.deriveSuccessor("raw", "id2"))
                .isNotEqualTo(other.deriveSuccessor("raw", "id"))
                .doesNotContain("=", "+", "/");
        assertThatThrownBy(() -> new RefreshTokenSuccessorDeriver(properties(" "))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void hashingComparesInConstantTimeAndHandlesNulls() {
        assertThat(RefreshTokenHashing.sameHash("a", "a")).isTrue();
        assertThat(RefreshTokenHashing.sameHash("a", "b")).isFalse();
        assertThat(RefreshTokenHashing.sameHash(null, "a")).isFalse();
        assertThat(RefreshTokenHashing.sameHash("a", null)).isFalse();
        assertThat(RefreshTokenHashing.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    // ---------------------------------------------------------------- helpers

    static void isRefreshInvalid(Throwable thrown) {
        assertThat(thrown).isInstanceOfSatisfying(UamException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(e.getErrorCode()).isEqualTo(RefreshTokenConstants.ERROR_REFRESH_TOKEN_INVALID);
        });
    }

    private RefreshToken signIn(String raw) {
        return service.issue(new IssueInput("u1", PrincipalType.USER, "web", RefreshTokenHashing.sha256Hex(raw), "s1", "d1",
                now().plusDays(30), "ip", "ua")).token();
    }

    private RefreshToken stored(RefreshToken token) {
        return repository.findByTokenId(token.getTokenId()).orElseThrow();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static UamProperties properties(String secret) {
        UamProperties properties = new UamProperties();
        properties.getAuth().setJwtSecret(secret);
        return properties;
    }

    /** A clock tests can move forward. */
    static final class MutableClock extends Clock {
        private volatile Instant instant;

        MutableClock(Instant start) {
            this.instant = start;
        }

        void advance(Duration by) {
            instant = instant.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    /** Store with the same contract as the Mongo/JPA adapters: copies in and out, conditional updates are atomic. */
    static final class InMemoryRefreshTokens implements RefreshTokenRepository {
        private final Map<String, RefreshToken> rows = new ConcurrentHashMap<>();
        volatile boolean forceLoseNextRotation;

        @Override
        public synchronized RefreshToken save(RefreshToken token) {
            if (token.getId() == null) {
                token.setId(UUID.randomUUID().toString());
            }
            rows.put(token.getId(), copy(token));
            return copy(token);
        }

        @Override
        public List<RefreshToken> saveAll(List<RefreshToken> tokens) {
            return tokens.stream().map(this::save).toList();
        }

        @Override
        public synchronized Optional<RefreshToken> findByTokenId(String tokenId) {
            return rows.values().stream().filter(t -> tokenId.equals(t.getTokenId())).findFirst().map(InMemoryRefreshTokens::copy);
        }

        @Override
        public synchronized Optional<RefreshToken> findByTokenHash(String tokenHash) {
            return rows.values().stream().filter(t -> tokenHash.equals(t.getTokenHash())).findFirst().map(InMemoryRefreshTokens::copy);
        }

        @Override
        public synchronized List<RefreshToken> findByPrincipalIdAndRevokedFalse(String principalId) {
            return rows.values().stream().filter(t -> principalId.equals(t.getPrincipalId()) && !Boolean.TRUE.equals(t.getRevoked()))
                    .map(InMemoryRefreshTokens::copy).toList();
        }

        @Override
        public synchronized List<RefreshToken> findByFamilyIdAndRevokedFalse(String familyId) {
            return rows.values().stream().filter(t -> familyId.equals(t.getFamilyId()) && !Boolean.TRUE.equals(t.getRevoked()))
                    .map(InMemoryRefreshTokens::copy).toList();
        }

        @Override
        public synchronized boolean markRotated(String id, String successorTokenId, String familyId, LocalDateTime at) {
            RefreshToken row = rows.get(id);
            if (forceLoseNextRotation) {
                forceLoseNextRotation = false;
                return false;
            }
            if (row == null || Boolean.TRUE.equals(row.getRevoked())) {
                return false;
            }
            row.setRevoked(true);
            row.setRevokedReason(RefreshTokenConstants.REASON_ROTATED);
            row.setRevokedAt(at);
            row.setLastUsedAt(at);
            row.setSuccessorTokenId(successorTokenId);
            row.setFamilyId(familyId);
            return true;
        }

        @Override
        public synchronized boolean claimGraceRedemption(String id, int maxRedemptions) {
            RefreshToken row = rows.get(id);
            int used = row.getGraceRedemptions() == null ? 0 : row.getGraceRedemptions();
            if (used >= maxRedemptions) {
                return false;
            }
            row.setGraceRedemptions(used + 1);
            return true;
        }

        synchronized int size() {
            return rows.size();
        }

        synchronized long activeCount() {
            return rows.values().stream().filter(t -> !Boolean.TRUE.equals(t.getRevoked())).count();
        }

        synchronized Collection<RefreshToken> all() {
            return new ArrayList<>(rows.values());
        }

        private static RefreshToken copy(RefreshToken t) {
            AuditInfo audit = t.getAudit() == null ? null
                    : AuditInfo.builder().createdAt(t.getAudit().getCreatedAt()).updatedAt(t.getAudit().getUpdatedAt()).build();
            return t.toBuilder().audit(audit).build();
        }
    }
}
