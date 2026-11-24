package com.heuristiq.documents.documenttype.adapter.in;

import com.heuristiq.documents.common.DmsCommonConstants;
import com.heuristiq.documents.documenttype.application.CallerContext;
import com.heuristiq.documents.security.DmsPrincipal;
import com.heuristiq.documents.security.DmsSecurityContext;

/** Resolves the caller from the verified session (gateway headers) and the request's own bearer token. Stateless. */
final class CallerContextResolver {

    private CallerContextResolver() {
    }

    /**
     * @param authorization the request's {@code Authorization} header value, may be null
     * @return the caller; organization and user come from the principal only, never from the request body
     */
    static CallerContext resolve(String authorization) {
        DmsPrincipal principal = DmsSecurityContext.currentPrincipal();
        String token = authorization != null && authorization.regionMatches(true, 0, DmsCommonConstants.BEARER_PREFIX, 0,
                DmsCommonConstants.BEARER_PREFIX.length())
                ? authorization.substring(DmsCommonConstants.BEARER_PREFIX.length()).trim() : null;
        return new CallerContext(principal.userId(), principal.tenantId(), token);
    }
}
