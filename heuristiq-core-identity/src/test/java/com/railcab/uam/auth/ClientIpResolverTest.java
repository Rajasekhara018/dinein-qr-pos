package com.heuristiq.uam.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientIpResolverTest {

    private static MockHttpServletRequest request(String xff, String realIp, String remote) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr(remote);
        if (xff != null) r.addHeader("X-Forwarded-For", xff);
        if (realIp != null) r.addHeader("X-Real-IP", realIp);
        return r;
    }

    @Test
    void theClientAddressComesFromForwardedForNotTheDockerPeer() {
        assertEquals("49.37.10.5", ClientIpResolver.resolve(request("49.37.10.5, 172.23.0.1", null, "172.23.0.2")));
    }

    @Test
    void privateHopsAheadOfThePublicAddressAreSkipped() {
        assertEquals("203.0.113.9", ClientIpResolver.resolve(request("10.0.0.4, 203.0.113.9, 172.23.0.1", null, "172.23.0.2")));
    }

    @Test
    void realIpIsUsedWhenForwardedForHasNoPublicAddress() {
        assertEquals("49.37.10.5", ClientIpResolver.resolve(request("172.23.0.1", "49.37.10.5", "172.23.0.2")));
    }

    @Test
    void aLanAddressIsKeptWhenThereIsNothingPublic() {
        assertEquals("192.168.1.20", ClientIpResolver.resolve(request("192.168.1.20", null, "172.23.0.2")));
    }

    @Test
    void withoutProxyHeadersTheConnectionAddressIsUsed() {
        assertEquals("172.23.0.2", ClientIpResolver.resolve(request(null, null, "172.23.0.2")));
    }

    @Test
    void portsAndIpv6BracketsAreStripped() {
        assertEquals("49.37.10.5", ClientIpResolver.resolve(request("49.37.10.5:51234", null, "172.23.0.2")));
        assertEquals("2001:db8::7", ClientIpResolver.resolve(request("[2001:db8::7]:443", null, "172.23.0.2")));
    }

    @Test
    void junkInTheHeaderIsIgnoredAndNeverResolvedAsAHostName() {
        assertEquals("172.23.0.2", ClientIpResolver.resolve(request("evil.example.com, unknown", null, "172.23.0.2")));
        assertNull(ClientIpResolver.clean("evil.example.com"));
    }

    @Test
    void privateAndLocalRangesAreRecognised() {
        assertTrue(ClientIpResolver.isPrivateOrLocal("172.23.0.2"));
        assertTrue(ClientIpResolver.isPrivateOrLocal("10.1.2.3"));
        assertTrue(ClientIpResolver.isPrivateOrLocal("192.168.0.1"));
        assertTrue(ClientIpResolver.isPrivateOrLocal("127.0.0.1"));
        assertTrue(ClientIpResolver.isPrivateOrLocal("100.64.1.1"));
        assertTrue(ClientIpResolver.isPrivateOrLocal("fd00::1"));
        assertFalse(ClientIpResolver.isPrivateOrLocal("49.37.10.5"));
        assertFalse(ClientIpResolver.isPrivateOrLocal("2001:db8::7"));
    }
}
