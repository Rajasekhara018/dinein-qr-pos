package com.heuristiq.search.scope;

import com.heuristiq.search.document.SearchDocument;
import com.heuristiq.search.model.SearchModel;
import com.heuristiq.search.query.LogicOp;
import com.heuristiq.search.query.Operator;
import com.heuristiq.search.query.SearchCondition;
import com.heuristiq.search.query.SearchGroup;
import com.heuristiq.search.query.SearchQuery;
import com.heuristiq.search.service.SearchIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantScopedSearchTest {

    private static final CallerScope ORG_A = new CallerScope("org-a", false);

    private SearchIndexService service;
    private CurrentCaller caller;
    private TenantScopedSearch search;

    @BeforeEach
    void setUp() {
        service = mock(SearchIndexService.class);
        caller = mock(CurrentCaller.class);
        search = new TenantScopedSearch(service, caller, new TenantScopePolicy(TenantScopingProperties.defaults()));
        when(service.search(any(), any())).thenAnswer(inv -> new PageImpl<SearchDocument>(List.of(), inv.<Pageable>getArgument(1), 0));
    }

    private SearchQuery sent() {
        ArgumentCaptor<SearchQuery> captor = ArgumentCaptor.forClass(SearchQuery.class);
        verify(service).search(captor.capture(), any());
        return captor.getValue();
    }

    @Test
    void anOrganizationModelIsFilteredToTheCallersOrganization() {
        when(caller.current()).thenReturn(ORG_A);

        search.search(new SearchQuery(SearchModel.USER, null), PageRequest.of(0, 10));

        SearchGroup group = sent().group();
        assertThat(group.conditions()).singleElement().satisfies(c -> {
            assertThat(c.field()).isEqualTo("ownerKey");
            assertThat(c.operator()).isEqualTo(Operator.EQ);
            assertThat(c.value()).isEqualTo("org-a");
        });
    }

    @Test
    void thePlatformAdministratorRoleIsNeverListedToAnOrganization() {
        when(caller.current()).thenReturn(ORG_A);

        search.search(new SearchQuery(SearchModel.ROLE, null), PageRequest.of(0, 10));

        SearchGroup top = sent().group();
        assertThat(top.logic()).isEqualTo(LogicOp.AND);
        assertThat(top.conditions()).singleElement().satisfies(c -> {
            assertThat(c.field()).isEqualTo("text1");
            assertThat(c.operator()).isEqualTo(Operator.NE);
            assertThat(c.value()).isEqualTo("SUPER_ADMIN");
        });
        assertThat(top.groups()).hasSize(1);
    }

    @Test
    void theCallersOwnFilterIsKeptAndTheOrganizationFilterIsAndedToIt() {
        when(caller.current()).thenReturn(ORG_A);
        SearchGroup asked = new SearchGroup(LogicOp.OR, List.of(new SearchCondition("text1", Operator.EQ, "x"), new SearchCondition("text1", Operator.EQ, "y")));

        search.search(new SearchQuery(SearchModel.API_CLIENT, asked), PageRequest.of(0, 10));

        SearchGroup combined = sent().group();
        assertThat(combined.logic()).isEqualTo(LogicOp.AND);
        assertThat(combined.groups()).hasSize(2);
        assertThat(combined.groups().get(0)).isEqualTo(asked);
        assertThat(combined.groups().get(1).conditions().get(0).value()).isEqualTo("org-a");
    }

    @Test
    void anAskedForOwnerOfAnotherOrganizationCannotWidenTheScope() {
        when(caller.current()).thenReturn(ORG_A);
        SearchGroup asked = new SearchGroup(LogicOp.OR, List.of(new SearchCondition("ownerKey", Operator.EQ, "org-b")));

        search.search(new SearchQuery(SearchModel.USER, asked), PageRequest.of(0, 10));

        // org-b OR-ed inside its own group, but the organization filter is AND-ed outside it.
        SearchGroup combined = sent().group();
        assertThat(combined.logic()).isEqualTo(LogicOp.AND);
        assertThat(combined.groups().get(1).conditions().get(0).value()).isEqualTo("org-a");
    }

    @Test
    void rolesAlsoIncludeTheSharedOnesThatHaveNoOwner() {
        when(caller.current()).thenReturn(ORG_A);

        search.search(new SearchQuery(SearchModel.ROLE, null), PageRequest.of(0, 10));

        // Only this organization's roles; legacy unowned roles belong to default.
        SearchGroup group = sent().group().groups().get(0);
        assertThat(group.logic()).isEqualTo(LogicOp.AND);
        assertThat(group.conditions()).singleElement().satisfies(c -> {
            assertThat(c.operator()).isEqualTo(Operator.EQ);
            assertThat(c.value()).isEqualTo("org-a");
        });
    }

    @Test
    void modelsWithoutOrganizationDataAndInternalServicesAreNotFiltered() {
        when(caller.current()).thenReturn(ORG_A);
        search.search(new SearchQuery(SearchModel.APPLICATION, null), PageRequest.of(0, 10));
        assertThat(sent().group()).isNull();

        service = mock(SearchIndexService.class);
        when(service.search(any(), any())).thenAnswer(inv -> new PageImpl<SearchDocument>(List.of(), inv.<Pageable>getArgument(1), 0));
        search = new TenantScopedSearch(service, caller, new TenantScopePolicy(TenantScopingProperties.defaults()));
        when(caller.current()).thenReturn(new CallerScope(null, true));
        search.search(new SearchQuery(SearchModel.USER, null), PageRequest.of(0, 10));
        assertThat(sent().group()).isNull();
    }

    @Test
    void aUserWithoutAnOrganizationIsRefusedForOrganizationModelsButNotOthers() {
        when(caller.current()).thenReturn(new CallerScope(null, false));

        assertThatThrownBy(() -> search.search(new SearchQuery(SearchModel.USER, null), PageRequest.of(0, 10)))
                .isInstanceOf(TenantRequiredException.class);
        search.search(new SearchQuery(SearchModel.APPLICATION, null), PageRequest.of(0, 10));
    }

    @Test
    void fetchingOneRecordOfAnotherOrganizationLooksLikeNotFound() {
        when(caller.current()).thenReturn(ORG_A);
        SearchDocument mine = SearchDocument.builder().model("USER").modelKey("1").ownerKey("org-a").build();
        SearchDocument theirs = SearchDocument.builder().model("USER").modelKey("2").ownerKey("org-b").build();
        SearchDocument sharedRole = SearchDocument.builder().model("ROLE").modelKey("3").build();
        SearchDocument otherRole = SearchDocument.builder().model("ROLE").modelKey("4").ownerKey("org-b").build();
        when(service.findOne("USER", "1")).thenReturn(mine);
        when(service.findOne("USER", "2")).thenReturn(theirs);
        when(service.findOne("ROLE", "3")).thenReturn(sharedRole);
        when(service.findOne("ROLE", "4")).thenReturn(otherRole);

        assertThat(search.findOne("USER", "1")).isSameAs(mine);
        assertThat(search.findOne("USER", "2")).isNull();
        assertThat(search.findOne("ROLE", "3")).isNull();
        assertThat(search.findOne("ROLE", "4")).isNull();
    }

    @Test
    void defaultOrganizationListsItsOwnAndUnownedRecordsForEveryScopedModel() {
        for (SearchModel model : List.of(SearchModel.ROLE, SearchModel.USER, SearchModel.NOTIFICATION_TEMPLATE)) {
            SearchGroup top = search.scoped(new SearchQuery(model, null), new CallerScope("default", false)).group();
            SearchGroup scope = model == SearchModel.ROLE ? top.groups().get(0) : top;
            assertThat(scope.logic()).isEqualTo(LogicOp.OR);
            assertThat(scope.conditions()).extracting(SearchCondition::operator).containsExactly(Operator.EQ, Operator.EMPTY);
            assertThat(scope.conditions().get(0).value()).isEqualTo("default");
        }
    }

    @Test
    void unownedRecordsBelongOnlyToDefaultOnSingleRecordLookups() {
        for (String model : List.of("ROLE", "USER", "NOTIFICATION_TEMPLATE")) {
            for (String owner : new String[]{null, "", "default", "org-a", "org-b"}) {
                SearchDocument document = SearchDocument.builder().model(model).modelKey("1").ownerKey(owner).build();
                when(service.findOne(model, "1")).thenReturn(document);
                when(caller.current()).thenReturn(ORG_A);
                assertThat(search.findOne(model, "1")).isEqualTo("org-a".equals(owner) ? document : null);
                when(caller.current()).thenReturn(new CallerScope("default", false));
                assertThat(search.findOne(model, "1")).isEqualTo(owner == null || owner.isEmpty() || "default".equals(owner) ? document : null);
                when(caller.current()).thenReturn(new CallerScope(null, true));
                assertThat(search.findOne(model, "1")).isSameAs(document);
            }
        }
    }

    @Test
    void whenScopingIsSwitchedOffNothingIsFilteredOrHidden() {
        search = new TenantScopedSearch(service, caller, new TenantScopePolicy(TenantScopingProperties.disabled()));
        when(caller.current()).thenReturn(ORG_A);
        SearchDocument theirs = SearchDocument.builder().model("USER").modelKey("2").ownerKey("org-b").build();
        when(service.findOne("USER", "2")).thenReturn(theirs);

        search.search(new SearchQuery(SearchModel.USER, null), PageRequest.of(0, 10));

        assertThat(sent().group()).isNull();
        assertThat(search.findOne("USER", "2")).isSameAs(theirs);
    }

    @Test
    void anOrganizationWideCallerWhoAskedToSeeAllOrganizationsIsNotFilteredButOneWhoDidNotAskIs() {
        when(caller.current()).thenReturn(new CallerScope("org-a", false, true));
        SearchDocument theirs = SearchDocument.builder().model("USER").modelKey("2").ownerKey("org-b").build();
        when(service.findOne("USER", "2")).thenReturn(theirs);

        search.search(new SearchQuery(SearchModel.USER, null), PageRequest.of(0, 10));

        assertThat(sent().group()).isNull();
        assertThat(search.findOne("USER", "2")).isSameAs(theirs);

        when(caller.current()).thenReturn(new CallerScope("org-a", false, false));
        assertThat(search.findOne("USER", "2")).isNull();
    }
}
