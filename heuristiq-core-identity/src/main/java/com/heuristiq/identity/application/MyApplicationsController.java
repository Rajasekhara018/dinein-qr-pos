package com.heuristiq.identity.application;

import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.heuristiq.identity.common.CommonResponseObject;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The session organization's applications for pickers and labels in every web app. Under the identity self-service path,
 * so any signed-in caller may read it without a privilege; the organization always comes from the token.
 */
@RestController
@RequestMapping(ApplicationConstants.LOOKUP_PATH)
@Tag(name = "My applications", description = "Active applications of the organization the session works in.")
public class MyApplicationsController {
    private final ApplicationServiceUseCase applicationService;

    public MyApplicationsController(ApplicationServiceUseCase applicationService) {
        this.applicationService = applicationService;
    }

    @Operation(summary = "Applications of my organization",
            description = "Active applications linked to the session organization: code, name, type, displayOrder and, when set, "
                    + "description and iconHint, sorted by displayOrder. Safe to cache for a minute.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Applications returned (possibly an empty list)"),
            @ApiResponse(responseCode = "401", description = "Not signed in")
    })
    @GetMapping
    public ResponseEntity<CommonResponseObject> mine() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(ApplicationConstants.LOOKUP_CACHE_SECONDS, TimeUnit.SECONDS).cachePrivate())
                .body(CommonResponseObject.success(applicationService.lookup().applications()));
    }
}
