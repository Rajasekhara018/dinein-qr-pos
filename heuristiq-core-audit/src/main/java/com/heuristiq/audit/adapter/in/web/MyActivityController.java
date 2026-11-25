package com.heuristiq.audit.adapter.in.web;

import static com.heuristiq.audit.adapter.in.web.WebConstants.*;
import com.heuristiq.audit.application.AuditQueryService;
import com.heuristiq.audit.application.CallerIdentity;
import com.heuristiq.audit.application.EventQuery;
import com.heuristiq.audit.common.CommonResponseObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service view of the caller's own audit trail (the profile "Activity" tab). The user id always comes from the
 * verified token, never from a request parameter, so nobody can read another user's events. The path has no entry in
 * the gateway's privilege catalog, which makes it open to any signed-in user (customer, driver or staff).
 */
@RestController
@RequestMapping(MY_EVENTS_BASE)
@Tag(name = "My activity", description = "The signed-in user's own audit events.")
public class MyActivityController {

    private final AuditQueryService queries;

    /**
     * Creates the controller.
     *
     * @param queries query service
     */
    public MyActivityController(AuditQueryService queries) {
        this.queries = queries;
    }

    @Operation(summary = "List my audit events",
            description = "Newest first, for the authenticated caller only. Optional eventType (ACCESS, SCREEN_VIEW, "
                    + "COMMUNICATION, PAYMENT_GATEWAY, DOMAIN) and outcome filters; size is capped at 50.")
    @GetMapping
    public ResponseEntity<CommonResponseObject> mine(@RequestParam(required = false) String eventType,
                                                      @RequestParam(required = false) String outcome,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size) {
        CallerIdentity user = CurrentUser.require();
        EventQuery q = new EventQuery(user.userId(), null, null, null, eventType, outcome, null, null, null, null,
                null, null, null);
        return ResponseEntity.ok(CommonResponseObject.success(
                queries.search(user, q, page, Math.min(Math.max(size, 1), MY_EVENTS_MAX_PAGE_SIZE))));
    }
}
