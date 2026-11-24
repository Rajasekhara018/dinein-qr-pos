package com.heuristiq.paymentservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayElevationTest {

    private static MockHttpServletRequest request(String elevated, String token) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        if (elevated != null) r.addHeader("X-User-Elevated", elevated);
        if (token != null) r.addHeader("X-Gateway-Token", token);
        return r;
    }

    @Test
    void elevatedOnlyWithTheHeaderAndTheMatchingGatewayKey() {
        GatewayElevation elevation = new GatewayElevation("forward-key");
        assertTrue(elevation.isElevated(request("true", "forward-key")));
    }

    @Test
    void aForgedHeaderWithoutTheKeyOrWithAWrongKeyIsIgnored() {
        GatewayElevation elevation = new GatewayElevation("forward-key");
        assertFalse(elevation.isElevated(request("true", null)));
        assertFalse(elevation.isElevated(request("true", "other-key")));
        assertFalse(elevation.isElevated(request(null, "forward-key")));
        assertFalse(elevation.isElevated(request("false", "forward-key")));
    }

    @Test
    void nobodyIsElevatedWhenNoKeyIsConfigured() {
        assertFalse(new GatewayElevation("").isElevated(request("true", "")));
        assertFalse(new GatewayElevation(null).isElevated(request("true", "x")));
    }
}
