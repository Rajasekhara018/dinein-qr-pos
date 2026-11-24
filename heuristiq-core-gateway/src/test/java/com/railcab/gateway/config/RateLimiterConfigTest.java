package com.heuristiq.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterConfigTest {

    private final GatewayProperties props = new GatewayProperties();
    private final RateLimiterConfig config = new RateLimiterConfig(props);

    @Test
    void limitersUseConfiguredRates() {
        props.getRateLimit().setReplenishRate(11);
        props.getRateLimit().setBurstCapacity(22);
        props.getRateLimit().setAuthReplenishRate(3);
        props.getRateLimit().setAuthBurstCapacity(4);

        var api = config.apiRateLimiter();
        var auth = config.authRateLimiter();
        RedisRateLimiter.Config apiConfig = (RedisRateLimiter.Config) ReflectionTestUtils.getField(api, "defaultConfig");
        RedisRateLimiter.Config authConfig = (RedisRateLimiter.Config) ReflectionTestUtils.getField(auth, "defaultConfig");
        assertThat(apiConfig.getReplenishRate()).isEqualTo(11);
        assertThat(apiConfig.getBurstCapacity()).isEqualTo(22);
        assertThat(authConfig.getReplenishRate()).isEqualTo(3);
        assertThat(authConfig.getBurstCapacity()).isEqualTo(4);
    }

    @Test
    void keyIsTheUserIdWhenPresent() {
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/x").header("X-User-Id", "u9").build());

        assertThat(config.userOrIpKeyResolver().resolve(ex).block()).isEqualTo("user:u9");
    }

    @Test
    void keyFallsBackToRemoteIpWhenUserIdBlank() {
        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.get("/x")
                .header("X-User-Id", " ").remoteAddress(new InetSocketAddress("10.1.2.3", 80)).build());

        assertThat(config.userOrIpKeyResolver().resolve(ex).block()).isEqualTo("ip:10.1.2.3");
    }

    @Test
    void keyIsUnknownWithoutAnyIdentity() {
        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.get("/x").build());

        assertThat(config.userOrIpKeyResolver().resolve(ex).block()).isEqualTo("ip:unknown");
    }

    @Test
    void loginLimitersUseConfiguredRates() {
        props.getRateLimit().setLoginReplenishRate(2);
        props.getRateLimit().setLoginBurstCapacity(6);
        props.getRateLimit().setLoginGlobalReplenishRate(9);
        props.getRateLimit().setLoginGlobalBurstCapacity(18);

        RedisRateLimiter.Config perAddress = (RedisRateLimiter.Config) ReflectionTestUtils.getField(config.loginRateLimiter(), "defaultConfig");
        RedisRateLimiter.Config global = (RedisRateLimiter.Config) ReflectionTestUtils.getField(config.loginGlobalRateLimiter(), "defaultConfig");
        assertThat(perAddress.getReplenishRate()).isEqualTo(2);
        assertThat(perAddress.getBurstCapacity()).isEqualTo(6);
        assertThat(global.getReplenishRate()).isEqualTo(9);
        assertThat(global.getBurstCapacity()).isEqualTo(18);
    }

    @Test
    void defaultsKeepLoginBelowWhatOneCpuOfUamCanHashTimesTwo() {
        assertThat(props.getRateLimit().getLoginReplenishRate()).isEqualTo(2);
        assertThat(props.getRateLimit().getLoginGlobalReplenishRate()).isEqualTo(10);
        assertThat(props.getRateLimit().getTrustedProxyHops()).isEqualTo(1);
    }

    @Test
    void behindTheProxyEveryCallerGetsItsOwnKey() {
        MockServerWebExchange alice = MockServerWebExchange.from(MockServerHttpRequest.post("/login")
                .header("X-Forwarded-For", "203.0.113.7").remoteAddress(new InetSocketAddress("172.17.0.1", 80)).build());
        MockServerWebExchange bob = MockServerWebExchange.from(MockServerHttpRequest.post("/login")
                .header("X-Forwarded-For", "198.51.100.9").remoteAddress(new InetSocketAddress("172.17.0.1", 80)).build());

        assertThat(config.loginIpKeyResolver().resolve(alice).block()).isEqualTo("login-ip:203.0.113.7");
        assertThat(config.loginIpKeyResolver().resolve(bob).block()).isEqualTo("login-ip:198.51.100.9");
        assertThat(config.authIpKeyResolver().resolve(alice).block()).isEqualTo("auth-ip:203.0.113.7");
        assertThat(config.userOrIpKeyResolver().resolve(alice).block()).isEqualTo("ip:203.0.113.7");
    }

    @Test
    void aCallerCannotChooseItsKeyWithAForgedHeaderFromAPublicConnection() {
        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.post("/login")
                .header("X-Forwarded-For", "1.2.3.4").remoteAddress(new InetSocketAddress("8.8.8.8", 80)).build());

        assertThat(config.loginIpKeyResolver().resolve(ex).block()).isEqualTo("login-ip:8.8.8.8");
    }

    @Test
    void trustingNoProxyUsesTheConnectionAddress() {
        props.getRateLimit().setTrustedProxyHops(0);
        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.post("/login")
                .header("X-Forwarded-For", "203.0.113.7").remoteAddress(new InetSocketAddress("172.17.0.1", 80)).build());

        assertThat(config.loginIpKeyResolver().resolve(ex).block()).isEqualTo("login-ip:172.17.0.1");
    }

    @Test
    void theGlobalLoginKeyIsOneConstantAndEveryLimiterHasItsOwnPrefix() {
        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.post("/login")
                .remoteAddress(new InetSocketAddress("10.1.2.3", 80)).build());

        assertThat(config.loginGlobalKeyResolver().resolve(ex).block()).isEqualTo("login:global");
        assertThat(java.util.Set.of(config.userOrIpKeyResolver().resolve(ex).block(), config.authIpKeyResolver().resolve(ex).block(),
                config.loginIpKeyResolver().resolve(ex).block(), config.loginGlobalKeyResolver().resolve(ex).block(),
                config.refreshIpKeyResolver().resolve(ex).block())).hasSize(5);
    }

    @Test
    void refreshLimiterUsesConfiguredRatesAndIsMoreGenerousThanTheAuthSurfaceByDefault() {
        GatewayProperties.RateLimit defaults = new GatewayProperties().getRateLimit();
        assertThat(defaults.getRefreshReplenishRate()).isGreaterThan(defaults.getAuthReplenishRate());
        assertThat(defaults.getRefreshBurstCapacity()).isGreaterThan(defaults.getAuthBurstCapacity());

        props.getRateLimit().setRefreshReplenishRate(40);
        props.getRateLimit().setRefreshBurstCapacity(80);
        RedisRateLimiter.Config refresh = (RedisRateLimiter.Config) ReflectionTestUtils.getField(config.refreshRateLimiter(), "defaultConfig");
        assertThat(refresh.getReplenishRate()).isEqualTo(40);
        assertThat(refresh.getBurstCapacity()).isEqualTo(80);
    }

    @Test
    void theRefreshKeyIsTheClientAddressInItsOwnBucket() {
        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/uam/identity/auth/refresh")
                .remoteAddress(new InetSocketAddress("203.0.113.9", 443)).build());

        assertThat(config.refreshIpKeyResolver().resolve(ex).block()).startsWith("refresh-ip:");
    }

    @Test
    void signUpLimiterTurnsTheHourlyBudgetIntoTokensPerSignUp() {
        RedisRateLimiter.Config byDefault = (RedisRateLimiter.Config) ReflectionTestUtils.getField(config.registerRateLimiter(), "defaultConfig");
        // 20 an hour: one token a second, 180 tokens per sign-up, room for 10 back to back.
        assertThat(byDefault.getReplenishRate()).isEqualTo(1);
        assertThat(byDefault.getRequestedTokens()).isEqualTo(180);
        assertThat(byDefault.getBurstCapacity()).isEqualTo(1800);

        props.getRateLimit().setRegisterPerHour(5);
        props.getRateLimit().setRegisterBurst(3);
        RedisRateLimiter.Config strict = (RedisRateLimiter.Config) ReflectionTestUtils.getField(config.registerRateLimiter(), "defaultConfig");
        assertThat(strict.getRequestedTokens()).isEqualTo(720);
        assertThat(strict.getBurstCapacity()).isEqualTo(2160);
    }

    @Test
    void signUpLimiterRefusesAnImpossibleBudgetAtStartup() {
        for (int[] bad : new int[][] {{0, 10}, {3601, 10}, {20, 0}}) {
            props.getRateLimit().setRegisterPerHour(bad[0]);
            props.getRateLimit().setRegisterBurst(bad[1]);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> config.registerRateLimiter())
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("register-per-hour");
        }
    }

    @Test
    void theSignUpKeyIsTheClientAddressInItsOwnBucket() {
        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/uam/identity/auth/register")
                .remoteAddress(new InetSocketAddress("203.0.113.9", 443)).build());

        String key = config.registerIpKeyResolver().resolve(ex).block();
        assertThat(key).startsWith("register-ip:").contains("203.0.113.9");
        assertThat(key).isNotEqualTo(config.authIpKeyResolver().resolve(ex).block());
    }
}
