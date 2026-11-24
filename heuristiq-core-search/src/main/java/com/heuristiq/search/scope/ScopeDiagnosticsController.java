package com.heuristiq.search.scope;

import com.heuristiq.search.api.SearchApiConstants;
import com.heuristiq.search.common.dto.CommonResponseObject;
import com.heuristiq.search.model.SearchModel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tells the caller how organization scoping treats THEM: whether scoping is on, which organization the search sees them in, and
 * whether they are unrestricted. It reveals nothing about anyone else, and exists so "I see another organization's data" can be
 * diagnosed from a browser in seconds.
 */
@RestController
@RequestMapping(SearchApiConstants.SCOPE_PATH)
@Tag(name = "Scope diagnostics", description = "How organization scoping applies to the caller")
public class ScopeDiagnosticsController {

    private final TenantScopePolicy policy;
    private final CurrentCaller currentCaller;

    public ScopeDiagnosticsController(TenantScopePolicy policy, CurrentCaller currentCaller) {
        this.policy = policy;
        this.currentCaller = currentCaller;
    }

    @Operation(summary = "How scoping applies to me")
    @GetMapping
    public CommonResponseObject mine() {
        CallerScope caller = currentCaller.current();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scopingEnabled", policy.enabled());
        out.put("tenantId", caller.tenantId());
        out.put("internalCaller", caller.internal());
        out.put("viewingAllOrganizations", caller.allOrganizations());
        out.put("unrestricted", caller.unrestricted());
        out.put("userScoping", policy.of(SearchModel.USER.name()).name());
        out.put("roleScoping", policy.of(SearchModel.ROLE.name()).name());
        out.put("filterApplied", policy.enabled() && !caller.unrestricted() && caller.hasTenant());
        return CommonResponseObject.success(out);
    }
}
