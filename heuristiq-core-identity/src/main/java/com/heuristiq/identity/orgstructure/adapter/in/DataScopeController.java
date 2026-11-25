package com.heuristiq.identity.orgstructure.adapter.in;

import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.orgstructure.application.DataScopeResolver;
import com.heuristiq.identity.orgstructure.domain.DataScope;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Tells a signed-in person how much of their organization they may see, so list screens can limit themselves. It sits
 * under the self-service identity path (any signed-in person may ask about themselves), never takes a user id from the
 * request, and reports only the caller's own scope.
 */
@RestController
@RequestMapping("/api/v1/uam/identity/data-scope")
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
@Tag(name = "Organization Structure", description = "Departments, position ladders, people and reporting lines.")
public class DataScopeController {

    private final DataScopeResolver scopes;
    private final CallerContext caller;

    public DataScopeController(DataScopeResolver scopes, CallerContext caller) {
        this.scopes = scopes;
        this.caller = caller;
    }

    /** What the response says: whether the caller is limited, and if so to which department ids. */
    public record DataScopeView(boolean restricted, Set<String> unitIds) {}

    @Operation(summary = "My data scope", description = "Whether the signed-in person is limited to their department and below, and the department ids they may see.")
    @GetMapping
    public CommonResponseObject mine() {
        DataScope scope = scopes.resolve(caller.tenant(), CallerContext.userId(), CallerContext.roles());
        return CommonResponseObject.success(new DataScopeView(scope.restricted(), scope.subtree()));
    }
}
