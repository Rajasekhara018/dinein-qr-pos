package com.heuristiq.uam.organization.subscription;

import java.util.List;

public interface TenantApplicationServiceUseCase {

    record ListInput(String tenantId) {}
    record ListOutput(List<TenantApplicationView> subscriptions) {}
    /** Every subscription of the organization, cancelled ones included, with application names. */
    ListOutput list(ListInput input);

    /**
     * @param setup true when the subscription is part of creating the application itself (the application cannot have any
     *              setup done before it exists, so linking it again is not a go-live); false for every other caller
     */
    record SubscribeInput(String tenantId, SubscribeTenantApplicationRequest request, String actor, boolean setup) {
        public SubscribeInput(String tenantId, SubscribeTenantApplicationRequest request, String actor) {
            this(tenantId, request, actor, false);
        }
    }
    record SubscribeOutput(TenantApplication subscription) {}
    /**
     * Gives the organization the application, or turns a cancelled subscription back on. An application the organization
     * already uses is returned as it is. Refused when the organization has reached its application limit.
     */
    SubscribeOutput subscribe(SubscribeInput input);

    record UpdateInput(String tenantId, String applicationCode, UpdateTenantApplicationRequest request, String actor) {}
    record UpdateOutput(TenantApplication subscription) {}
    UpdateOutput update(UpdateInput input);

    record CancelInput(String tenantId, String applicationCode, String actor) {}
    record CancelOutput(TenantApplication subscription) {}
    /** Takes the application away from the organization. The row is kept (marked cancelled). Cancelling twice is a no-op. */
    CancelOutput cancel(CancelInput input);

    /** Codes of the applications the organization may use today: active subscriptions that have not passed their end date. */
    List<String> activeApplicationCodes(String tenantId);

    /** Codes of every application linked to the organization that is not cancelled (active or suspended), sorted. */
    List<String> linkedApplicationCodes(String tenantId);

    /** True when an organization other than {@code tenantId} has a link to the application that is not cancelled. */
    boolean linkedElsewhere(String applicationCode, String tenantId);

    /** Makes sure the organization uses the application (used when setting up the default organization); never limited. */
    void ensureSubscribed(String tenantId, String applicationCode, String actor);

    /** The organization's link to the application (any status), if it has one. */
    java.util.Optional<TenantApplication> find(String tenantId, String applicationCode);

    /** Every organization's ACTIVE link to the application that has not passed its end date, across organizations. */
    List<TenantApplication> activeLinks(String applicationCode);

    /** Replaces the organization's owner team for the application (user ids); an empty list clears it. */
    TenantApplication updateOwnerTeam(String tenantId, String applicationCode, List<String> ownerTeamUserIds, String actor);

    /**
     * Switches the application on for the organization although blocking steps fail, and records the override on the link.
     * Does NOT run the go-live guard: the override use case has already checked who may do this.
     */
    TenantApplication activateWithOverride(String tenantId, String applicationCode, GoLiveOverride override, String actor);

    /** Remembers when an alert of this kind was last sent for which blocking set; a null fingerprint clears the state. */
    void recordAlert(String tenantId, String applicationCode, AlertKind kind, java.time.LocalDateTime at, String fingerprint);

    /** The two alert kinds whose throttle state is kept on the link. */
    enum AlertKind { BLOCKED, REFUSED }

    /** What a SUPER_ADMIN override stores on the link. */
    record GoLiveOverride(String by, java.time.LocalDateTime at, String reason, String blockingSteps) {}
}
