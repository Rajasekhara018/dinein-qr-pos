package com.heuristiq.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private static ServerHttpRequest request(String peer, String forwardedFor) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("/x");
        if (peer != null) {
            builder.remoteAddress(new InetSocketAddress(peer, 443));
        }
        if (forwardedFor != null) {
            builder.header("X-Forwarded-For", forwardedFor);
        }
        return builder.build();
    }

    @Test
    void usesTheConnectionAddressWhenNoProxyIsTrusted() {
        assertThat(ClientIpResolver.resolve(request("10.0.0.5", "203.0.113.7"), 0)).isEqualTo("10.0.0.5");
    }

    @Test
    void isUnknownWithoutAConnectionAddress() {
        assertThat(ClientIpResolver.resolve(request(null, "203.0.113.7"), 1)).isEqualTo("unknown");
    }

    @Test
    void readsTheAddressTheTrustedProxyAdded() {
        assertThat(ClientIpResolver.resolve(request("172.17.0.1", "203.0.113.7"), 1)).isEqualTo("203.0.113.7");
    }

    @Test
    void ignoresWhatTheCallerPutOnTheLeft() {
        assertThat(ClientIpResolver.resolve(request("172.17.0.1", "6.6.6.6, 1.1.1.1, 203.0.113.7"), 1)).isEqualTo("203.0.113.7");
    }

    @Test
    void countsHopsFromTheRightWhenThereAreTwoProxies() {
        assertThat(ClientIpResolver.resolve(request("10.0.0.5", "9.9.9.9, 203.0.113.7, 172.31.0.9"), 2)).isEqualTo("203.0.113.7");
    }

    @Test
    void doesNotTrustTheHeaderFromAPublicConnection() {
        assertThat(ClientIpResolver.resolve(request("8.8.8.8", "203.0.113.7"), 1)).isEqualTo("8.8.8.8");
    }

    @Test
    void fallsBackToTheConnectionWhenTheHeaderIsMissingShortOrBlank() {
        assertThat(ClientIpResolver.resolve(request("10.0.0.5", null), 1)).isEqualTo("10.0.0.5");
        assertThat(ClientIpResolver.resolve(request("10.0.0.5", "  "), 1)).isEqualTo("10.0.0.5");
        assertThat(ClientIpResolver.resolve(request("10.0.0.5", "203.0.113.7"), 2)).isEqualTo("10.0.0.5");
    }

    @Test
    void neverUsesAnEntryThatIsNotAnAddressLiteral() {
        for (String bad : new String[] {"evil.example.com", "999.1.1.1", "1.2.3", "1.2.3.4.5", "<script>", "unknown", "1.2.3.4:80"}) {
            assertThat(ClientIpResolver.resolve(request("10.0.0.5", bad), 1)).as(bad).isEqualTo("10.0.0.5");
        }
    }

    @Test
    void acceptsIpv6Literals() {
        assertThat(ClientIpResolver.resolve(request("10.0.0.5", "2001:db8::1"), 1)).isEqualTo("2001:db8::1");
    }

    @Test
    void recognisesInternalAddresses() throws UnknownHostException {
        for (String internal : new String[] {"127.0.0.1", "10.1.2.3", "172.17.0.1", "172.31.0.9", "192.168.1.1", "169.254.1.1"}) {
            assertThat(ClientIpResolver.isInternal(InetAddress.getByName(internal))).as(internal).isTrue();
        }
        for (String external : new String[] {"8.8.8.8", "203.0.113.7", "172.32.0.1"}) {
            assertThat(ClientIpResolver.isInternal(InetAddress.getByName(external))).as(external).isFalse();
        }
    }
}
