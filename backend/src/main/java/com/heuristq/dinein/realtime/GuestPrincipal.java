package com.heuristq.dinein.realtime;

import java.security.Principal;

/** STOMP session principal for an anonymous guest (identified by the signed guest-session cookie). */
public record GuestPrincipal(String sessionId) implements Principal {

    @Override
    public String getName() {
        return "guest:" + sessionId;
    }
}
