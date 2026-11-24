package com.heuristiq.uam.session;

import java.util.List;

public interface UserSessionServiceUseCase {

    record ListByUserInput(String userId) {}
    record ListByUserOutput(List<UserSession> sessions) {}
    ListByUserOutput listByUser(ListByUserInput input);

    record GetInput(String sessionId) {}
    record GetOutput(UserSession session) {}
    GetOutput get(GetInput input);

    record RevokeInput(String sessionId) {}
    record RevokeOutput() {}
    RevokeOutput revoke(RevokeInput input);

    record RevokeAllForUserInput(String userId) {}
    record RevokeAllForUserOutput() {}
    RevokeAllForUserOutput revokeAllForUser(RevokeAllForUserInput input);
}
