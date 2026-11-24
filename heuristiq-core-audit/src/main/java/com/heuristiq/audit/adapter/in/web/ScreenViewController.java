package com.railcab.audit.adapter.in.web;

import static com.railcab.audit.adapter.in.web.WebConstants.*;
import com.railcab.audit.application.AuditIngestService;
import com.railcab.audit.application.CallerIdentity;
import com.railcab.audit.application.ClientInfo;
import com.railcab.audit.application.IngestResult;
import com.railcab.audit.application.ScreenViewInput;
import com.railcab.audit.common.CommonResponseObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Screen-view reports from the Angular and Flutter clients. Open to any signed-in customer, driver or staff user:
 * the gateway's catalog has no privilege entry for it (unmatched paths are authenticated-only). The user id and
 * roles always come from the verified token, never from the body.
 */
@RestController
@RequestMapping(API_BASE)
@Tag(name = "Screen views", description = "Reports which screen a signed-in user opened.")
public class ScreenViewController {

    private final AuditIngestService ingest;

    /**
     * Creates the controller.
     *
     * @param ingest ingest service
     */
    public ScreenViewController(AuditIngestService ingest) {
        this.ingest = ingest;
    }

    /** Request body of the screen-view endpoint. */
    @Schema(description = "A batch of 1 to 50 screen views.")
    public record ScreenViewBatch(List<ScreenViewInput> events) {
    }

    @Operation(summary = "Report screen views",
            description = "Up to 50 per call and 60 events per user per minute; extra events are dropped and the call "
                    + "answers 429 with the counts (accepted events are still stored). Clients should treat the call "
                    + "as fire-and-forget and never block the UI on it.")
    @PostMapping(value = SCREEN_VIEWS, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CommonResponseObject> screenViews(@RequestBody ScreenViewBatch batch, HttpServletRequest request) {
        CallerIdentity user = CurrentUser.require();
        ClientInfo client = new ClientInfo(request.getHeader(CORRELATION_HEADER), request.getHeader(REQUEST_ID_HEADER),
                request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));
        IngestResult result = ingest.ingestScreenViews(user, batch == null ? null : batch.events(), client);
        if (result.dropped() > 0) {
            CommonResponseObject body = CommonResponseObject.failure(
                    result.dropped() + " screen view(s) dropped: rate limit exceeded", RATE_LIMITED_CODE);
            body.setResponseObject(result);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(RETRY_AFTER_HEADER, RETRY_AFTER_SECONDS).body(body);
        }
        return ResponseEntity.ok(CommonResponseObject.success(result));
    }
}
