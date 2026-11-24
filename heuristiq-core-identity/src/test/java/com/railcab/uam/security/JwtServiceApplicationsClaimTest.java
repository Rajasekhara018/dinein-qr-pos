package com.heuristiq.uam.security;

import com.heuristiq.uam.config.UamProperties;
import com.heuristiq.uam.users.UamUser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceApplicationsClaimTest {

    private JwtService jwtService() {
        UamProperties properties = new UamProperties();
        properties.getAuth().setJwtSecret("0123456789-0123456789-0123456789-0123456789");
        return new JwtService(properties);
    }

    @Test
    void tokenCarriesTheApplicationsTheUserMayUse() {
        JwtService jwt = jwtService();
        UamUser user = UamUser.builder().id("u1").roles(List.of("AGENT")).build();

        var claims = jwt.parse(jwt.generate(user, List.of("MATRIMONY", "RIDER")));

        assertThat(claims.get("apps", List.class)).containsExactly("MATRIMONY", "RIDER");
    }

    @Test
    void tokenWithoutApplicationsHasAnEmptyClaimNotAMissingOne() {
        JwtService jwt = jwtService();
        UamUser user = UamUser.builder().id("u1").roles(List.of("CUSTOMER")).build();

        var claims = jwt.parse(jwt.generate(user));

        assertThat(claims.get("apps", List.class)).isEmpty();
    }

    @Test
    void theOrgWideClaimIsWrittenOnlyForAnOrganizationWideCaller() {
        com.heuristiq.uam.users.UamUser user = com.heuristiq.uam.users.UamUser.builder().id("u1").userName("u1")
                .roles(java.util.List.of("OPS")).build();

        io.jsonwebtoken.Claims wide = jwtService().parse(jwtService().generate(user, java.util.List.of("RIDER"), "org-1", true));
        io.jsonwebtoken.Claims ordinary = jwtService().parse(jwtService().generate(user, java.util.List.of("RIDER"), "org-1", false));
        io.jsonwebtoken.Claims defaultOverload = jwtService().parse(jwtService().generate(user, java.util.List.of("RIDER"), "org-1"));

        org.assertj.core.api.Assertions.assertThat(wide.get(com.heuristiq.uam.organization.OrgWideConstants.CLAIM)).isEqualTo(true);
        org.assertj.core.api.Assertions.assertThat(ordinary.get(com.heuristiq.uam.organization.OrgWideConstants.CLAIM)).isNull();
        org.assertj.core.api.Assertions.assertThat(defaultOverload.get(com.heuristiq.uam.organization.OrgWideConstants.CLAIM)).isNull();
    }
}
