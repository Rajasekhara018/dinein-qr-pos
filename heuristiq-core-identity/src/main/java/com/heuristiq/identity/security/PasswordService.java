package com.heuristiq.identity.security;

import com.heuristiq.identity.common.UamException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * BCrypt is CPU-bound (~200ms/op at strength 11 on typical server hardware -- measured ~403ms at
 * the previous strength 12), unlike the rest of this service's I/O-bound work that virtual threads
 * are suited for. Two changes from the naive {@code new BCryptPasswordEncoder(12)}:
 *
 * <p><b>Strength 12 -> 11.</b> OWASP's Password Storage Cheat Sheet sets bcrypt's floor at 10
 * ("as large as verification server performance will allow"), with bcrypt itself now legacy-tier
 * behind Argon2id -- migrating hash algorithms is a separate initiative (existing stored hashes
 * are bcrypt-formatted), not a throughput fix. Strength 11 stays one full factor above that floor
 * while roughly halving per-op cost (bcrypt cost is 2^N iterations).
 *
 * <p><b>Bounded worker pool.</b> {@code availableProcessors()} platform threads, at least 1 (matches whatever
 * CPU limit the container gets: production gives UAM one CPU per hash thread);
 * {@code railcab.uam.auth.password.hash-pool-threads} sets the count explicitly, for example to leave a core free
 * for token checks and health checks on a bigger host. CPU-bound
 * work must not run on virtual threads or an unbounded pool: virtual threads don't help work that
 * never blocks, and letting every concurrent login pin its own core, unbounded, is what let a
 * burst of logins destabilize the whole pod (see Stage 0 finding) rather than just queuing.
 * Overflow queues briefly (bounded, so it can't grow without limit) and a submission that can't
 * get a result within the configured timeout fails fast with a 503 rather than piling up.
 */
@Slf4j
@Service
public class PasswordService {

    private final PasswordEncoder encoder;
    private final ExecutorService hashingExecutor;
    private final long timeoutMs;

    /** Matches PasswordServiceTest's direct {@code new PasswordService()} usage. */
    public PasswordService() {
        this(SecurityConstants.DEFAULT_BCRYPT_STRENGTH, SecurityConstants.DEFAULT_QUEUE_CAPACITY,
                SecurityConstants.DEFAULT_TIMEOUT_MS, SecurityConstants.AUTO_HASH_POOL_THREADS);
    }

    /**
     * @param bcryptStrength BCrypt cost factor
     * @param queueCapacity  password-hash jobs that may wait for a worker
     * @param timeoutMs      how long a caller waits for its job
     * @param poolThreads    worker threads; {@link SecurityConstants#AUTO_HASH_POOL_THREADS} (0) or less means
     *                       one per available core, at least one
     */
    @Autowired
    public PasswordService(
            @Value("${railcab.uam.auth.password.bcrypt-strength:11}") int bcryptStrength,
            @Value("${railcab.uam.auth.password.hash-pool-queue-capacity:64}") int queueCapacity,
            @Value("${railcab.uam.auth.password.hash-timeout-ms:2000}") long timeoutMs,
            @Value("${railcab.uam.auth.password.hash-pool-threads:0}") int poolThreads) {
        this.encoder = new BCryptPasswordEncoder(bcryptStrength);
        this.hashingExecutor = boundedExecutor(poolSize(poolThreads, Runtime.getRuntime().availableProcessors()),
                queueCapacity);
        this.timeoutMs = timeoutMs;
    }

    /** Visible for testing -- substitute a fast encoder/direct executor without paying real BCrypt cost. */
    PasswordService(PasswordEncoder encoder, ExecutorService hashingExecutor, long timeoutMs) {
        this.encoder = encoder;
        this.hashingExecutor = hashingExecutor;
        this.timeoutMs = timeoutMs;
    }

    /**
     * The worker count: the configured one when positive, otherwise every core but one (at least one).
     *
     * @param configured the configured thread count, 0 or less for automatic
     * @param cores      the available processors
     * @return the number of bcrypt worker threads
     */
    static int poolSize(int configured, int cores) {
        if (configured > 0) {
            return configured;
        }
        return Math.max(SecurityConstants.MIN_HASH_POOL_THREADS, cores - SecurityConstants.HASH_POOL_RESERVED_CORES);
    }

    private static ExecutorService boundedExecutor(int threads, int queueCapacity) {
        log.info("BCrypt worker pool: {} thread(s), queue {}", threads, queueCapacity);
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                Thread.ofPlatform().daemon(true).name("bcrypt-worker-", 0).factory());
    }

    public String hash(String rawPassword) {
        return submit(() -> encoder.encode(rawPassword));
    }

    public boolean matches(String rawPassword, String hash) {
        return submit(() -> encoder.matches(rawPassword, hash));
    }

    private <T> T submit(Callable<T> task) {
        Future<T> future;
        try {
            future = hashingExecutor.submit(task);
        } catch (RejectedExecutionException ex) {
            throw overloaded(ex);
        }
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw overloaded(ex);
        } catch (ExecutionException ex) {
            throw new UamException("Password verification failed: " + ex.getCause(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new UamException("Password verification interrupted", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private UamException overloaded(Exception cause) {
        log.warn("BCrypt worker pool overloaded ({}): {}", cause.getClass().getSimpleName(), cause.getMessage());
        return new UamException(
                "Password verification is temporarily overloaded; please retry.", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
