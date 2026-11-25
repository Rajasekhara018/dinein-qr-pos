package com.heuristiq.identity.readiness.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.heuristiq.identity.organization.subscription.TenantApplication;
import com.heuristiq.identity.organization.subscription.TenantApplicationServiceUseCase;
import com.heuristiq.identity.readiness.application.port.SchedulerLease;
import com.heuristiq.identity.readiness.config.GovernanceProperties;
import com.heuristiq.identity.readiness.domain.ApplicationReadiness;
import com.heuristiq.identity.readiness.domain.GoLiveBlocker;
import com.heuristiq.identity.readiness.domain.GoLiveRules;
import com.heuristiq.identity.readiness.domain.ReadinessProfiles;
import com.heuristiq.identity.security.TenantContext;

/**
 * Re-evaluates the readiness of every ACTIVE organization-application link and alerts on blocking steps (see
 * {@link ReadinessAlertService} for recipients and throttling). Properties of a run:
 * <ul>
 *   <li>one instance at a time: a database lease ({@link SchedulerLease}) is taken first; another instance skips the round;</li>
 *   <li>idempotent: alerts are throttled by state saved on the link, so running twice sends nothing twice;</li>
 *   <li>bounded: at most {@code recheckMaxLinks} links per run and {@code recheckConcurrency} checks at once, each check
 *       already under the readiness time budget; the cache is bypassed;</li>
 *   <li>per organization: every check names its organization explicitly and sets the tenant context for the duration (there
 *       is no signed-in caller), and one organization's blocking is never alerted to another organization's owners;</li>
 *   <li>a failing check is logged and counted; it never stops the others.</li>
 * </ul>
 */
public class ReadinessRecheckService {

    private static final Logger log = LoggerFactory.getLogger(ReadinessRecheckService.class);

    /** Name of the lease. */
    public static final String LEASE_NAME = "uam-readiness-recheck";

    private final TenantApplicationServiceUseCase links;
    private final ApplicationReadinessUseCase readiness;
    private final ReadinessAlertService alerts;
    private final SchedulerLease lease;
    private final Predicate<String> organizationActive;
    private final GovernanceProperties properties;

    /**
     * @param organizationActive tells whether an organization is still in service (archived or suspended ones are skipped)
     */
    public ReadinessRecheckService(TenantApplicationServiceUseCase links, ApplicationReadinessUseCase readiness,
                                   ReadinessAlertService alerts, SchedulerLease lease, Predicate<String> organizationActive,
                                   GovernanceProperties properties) {
        this.links = links;
        this.readiness = readiness;
        this.alerts = alerts;
        this.lease = lease;
        this.organizationActive = organizationActive;
        this.properties = properties;
    }

    /**
     * What one run did.
     *
     * @param ran     false when another instance holds the lease
     * @param checked links evaluated
     * @param alerted links an alert was sent for
     * @param failed  links whose check threw
     */
    public record RunResult(boolean ran, int checked, int alerted, int failed) {
    }

    /** One re-check round. */
    public RunResult runOnce() {
        if (!lease.tryAcquire(LEASE_NAME, properties.leaseTtl())) {
            log.debug("readiness.recheck.skipped another instance holds the lease");
            return new RunResult(false, 0, 0, 0);
        }
        try {
            List<TenantApplication> due = activeLinks();
            if (due.isEmpty()) {
                return new RunResult(true, 0, 0, 0);
            }
            return evaluate(due);
        } finally {
            lease.release(LEASE_NAME);
        }
    }

    private List<TenantApplication> activeLinks() {
        List<TenantApplication> all = new ArrayList<>();
        for (String code : ReadinessProfiles.applicationCodes()) {
            for (TenantApplication link : links.activeLinks(code)) {
                if (link.getTenantId() != null && organizationActive.test(link.getTenantId())) {
                    all.add(link);
                }
            }
        }
        all.sort(Comparator.comparing(TenantApplication::getTenantId).thenComparing(TenantApplication::getApplicationCode));
        return all.size() > properties.recheckMaxLinks() ? all.subList(0, properties.recheckMaxLinks()) : all;
    }

    private RunResult evaluate(List<TenantApplication> due) {
        int checked = 0;
        int alerted = 0;
        int failed = 0;
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(properties.recheckConcurrency(), due.size()));
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (TenantApplication link : due) {
                futures.add(pool.submit(() -> checkOne(link)));
            }
            for (Future<Outcome> future : futures) {
                try {
                    Outcome outcome = future.get();
                    if (outcome == Outcome.FAILED) {
                        failed++;
                    } else {
                        checked++;
                        if (outcome == Outcome.ALERTED) {
                            alerted++;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failed++;
                    break;
                } catch (ExecutionException e) {
                    failed++;
                    log.warn("readiness.recheck.task-failed reason={}", e.getCause() == null ? e.toString() : e.getCause().toString());
                }
            }
        } finally {
            pool.shutdownNow();
        }
        log.info("readiness.recheck.done checked={} alerted={} failed={}", checked, alerted, failed);
        return new RunResult(true, checked, alerted, failed);
    }

    private enum Outcome { OK, ALERTED, FAILED }

    private Outcome checkOne(TenantApplication link) {
        String tenantId = link.getTenantId();
        String code = link.getApplicationCode();
        TenantContext.set(tenantId);
        try {
            ApplicationReadiness result = readiness.evaluateFor(tenantId, code);
            List<GoLiveBlocker> blockers = GoLiveRules.blockers(result);
            if (blockers.isEmpty()) {
                alerts.cleared(tenantId, code, link);
                return Outcome.OK;
            }
            return alerts.blocked(tenantId, code, result.applicationName(), blockers) ? Outcome.ALERTED : Outcome.OK;
        } catch (RuntimeException e) {
            log.warn("readiness.recheck.failed tenant={} application={} reason={}", tenantId, code, e.toString());
            return Outcome.FAILED;
        } finally {
            TenantContext.clear();
        }
    }
}
