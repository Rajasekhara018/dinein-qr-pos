package com.heuristiq.search.projection;

import com.heuristiq.search.model.SearchModel;
import com.heuristiq.search.query.*;
import com.heuristiq.search.scope.CallerScope;
import com.heuristiq.search.scope.TenantRequiredException;
import com.heuristiq.search.service.SearchIndexUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSearchPolicyTest {
    private ProjectionIngressService ingress;
    private WebSearchPolicy policy;
    @BeforeEach void setUp() {
        ingress = mock(ProjectionIngressService.class);
        when(ingress.ready(anyString(), anyString())).thenReturn(true);
        when(ingress.ready(anyString())).thenReturn(true);
        policy = new WebSearchPolicy(ingress);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private SearchGroup or(SearchCondition... conditions) {
        return new SearchGroup(LogicOp.OR, List.of(conditions), List.of());
    }
    @Test void organizationScopeCannotBeBypassedByAnOrGroupOrAnOrgWideClaim() {
        var incoming = or(new SearchCondition("ownerKey", Operator.EQ, "other-org"));
        var result = policy.apply(new SearchQuery(SearchModel.WEB_ADMIN_DRIVER, incoming), new CallerScope("org-a", false, true));
        assertThat(result.group().logic()).isEqualTo(LogicOp.AND);
        assertThat(result.group().conditions()).contains(new SearchCondition("ownerKey", Operator.EQ, "org-a"));
        assertThat(result.group().groups()).containsExactly(incoming);
    }
    @Test void selfListsAreSealedToTheSignedInUser() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user-a", null, List.of()));
        var incoming = or(new SearchCondition("text14", Operator.EQ, "user-b"));
        var result = policy.apply(new SearchQuery(SearchModel.WEB_WALLET_SELF_ENTRY, incoming), new CallerScope("org-a", false));
        assertThat(result.group().conditions()).contains(new SearchCondition("text14", Operator.EQ, "user-a"),
                new SearchCondition("ownerKey", Operator.EQ, "org-a"));
        assertThat(result.group().groups()).containsExactly(incoming);
    }
    @Test void accountParentIsRequiredAndCannotBeOrEdAway() {
        var incoming = or(new SearchCondition("text12", Operator.EQ, "account-a"), new SearchCondition("text1", Operator.EQ, "credit"));
        var result = policy.apply(new SearchQuery(SearchModel.WEB_WALLET_ACCOUNT_ENTRY, incoming), new CallerScope("org-a", false));
        assertThat(result.group().conditions()).contains(new SearchCondition("text12", Operator.EQ, "account-a"));
        assertThatThrownBy(() -> policy.apply(new SearchQuery(SearchModel.WEB_WALLET_ACCOUNT_ENTRY, null), new CallerScope("org-a", false)))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void multipleAccountParentsAreRejected() {
        var incoming = or(new SearchCondition("text12", Operator.EQ, "account-a"), new SearchCondition("text12", Operator.EQ, "account-b"));
        assertThatThrownBy(() -> policy.apply(new SearchQuery(SearchModel.WEB_WALLET_ACCOUNT_ENTRY, incoming), new CallerScope("org-a", false)))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void allOrganizationWalletListsRequireThePlatformOrganization() {
        assertThatThrownBy(() -> policy.apply(new SearchQuery(SearchModel.WEB_WALLET_ACCOUNT, null), new CallerScope("org-a", false, true)))
                .isInstanceOf(TenantRequiredException.class);
    }
    @Test void pendingBackfillReturnsUnavailableRatherThanAnEmptySuccessfulList() {
        when(ingress.ready("WEB_ADMIN_DRIVER", "org-a")).thenReturn(false);
        assertThatThrownBy(() -> policy.apply(new SearchQuery(SearchModel.WEB_ADMIN_DRIVER, null), new CallerScope("org-a", false)))
                .isInstanceOf(SearchIndexUnavailableException.class);
    }
}
