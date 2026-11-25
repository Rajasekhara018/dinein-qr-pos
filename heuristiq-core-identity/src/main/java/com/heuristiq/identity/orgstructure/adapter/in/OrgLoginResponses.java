package com.heuristiq.identity.orgstructure.adapter.in;

import java.util.List;

import com.heuristiq.identity.orgstructure.domain.LoginModels.LoginCandidate;

/** Response shapes of the login API. */
final class OrgLoginResponses {
    private OrgLoginResponses() {
    }

    /**
     * An employee without a login. Each issue is "CODE" or "CODE:detail" (for example {@code UNKNOWN_ROLE:AUDITOR});
     * the code is stable so the UI can translate it. A candidate is ready when it has no issues.
     */
    record LoginCandidateResponse(String employeeId, String employeeCode, String fullName, String email, String unitId,
                                  String positionId, List<String> roles, List<String> issues, boolean ready) {
        static LoginCandidateResponse of(LoginCandidate candidate) {
            return new LoginCandidateResponse(candidate.candidate().employeeId(), candidate.candidate().employeeCode(),
                    candidate.candidate().fullName(), candidate.candidate().email(), candidate.candidate().unitId(),
                    candidate.candidate().positionId(), candidate.roles(), candidate.issues(), candidate.ready());
        }
    }
}
