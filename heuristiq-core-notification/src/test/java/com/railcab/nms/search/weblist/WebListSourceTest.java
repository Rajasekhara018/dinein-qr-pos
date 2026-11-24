package com.railcab.nms.search.weblist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.railcab.nms.adapters.in.web.broadcast.BroadcastController;
import com.railcab.nms.adapters.in.web.event.EventController;
import com.railcab.nms.adapters.in.web.template.TemplateController;
import com.railcab.nms.application.security.NmsPrincipal;
import com.railcab.nms.application.security.TenantAccess;
import com.railcab.nms.domain.repository.TenantScope;
import com.railcab.nms.shared.CommonResponseObject;

/** Rows are read through NMS's own list endpoints, page by page, as a person of the partition's organization. */
class WebListSourceTest {

    private final WebListTenants tenants = Mockito.mock(WebListTenants.class);
    private final TemplateController templates = Mockito.mock(TemplateController.class);
    private final EventController events = Mockito.mock(EventController.class);
    private final BroadcastController broadcasts = Mockito.mock(BroadcastController.class);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private WebListSource source() throws Exception {
        return new WebListSource(WebListPublisherTest.JSON, tenants, templates, events, broadcasts);
    }

    @Test
    void everyListHasOnePartitionPerOrganization() throws Exception {
        Mockito.when(tenants.organizations()).thenReturn(List.of("default", "acme"));

        assertThat(source().partitions(WebListChanges.BROADCAST))
                .containsExactly(WebListSource.Partition.organization("default"), WebListSource.Partition.organization("acme"));
    }

    @Test
    void templatesAreReadPageByPageAsTheOrganizationAndTheCallerIsRestored() throws Exception {
        List<TenantScope> scopes = new ArrayList<>();
        Mockito.when(templates.listAll(any())).thenAnswer(call -> {
            scopes.add(TenantAccess.scope());
            Pageable page = call.getArgument(0);
            List<Map<String, Object>> content = page.getPageNumber() == 0
                    ? List.of(Map.of("id", "t1"), Map.of("id", "t2")) : List.of(Map.of("id", "t3"));
            // 101 rows in all: two pages of 100.
            return ResponseEntity.ok(CommonResponseObject.success(new PageImpl<>(content, page, 101)));
        });
        var caller = new UsernamePasswordAuthenticationToken(new NmsPrincipal("u9", "other", false), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(caller);
        List<Map<String, Object>> rows = new ArrayList<>();

        source().scan(WebListChanges.TEMPLATE, WebListSource.Partition.organization("acme"), rows::add);

        assertThat(rows).extracting(row -> row.get("id")).containsExactly("t1", "t2", "t3");
        assertThat(scopes).containsOnly(TenantScope.limitedTo("acme"));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(caller);
    }

    @Test
    void eventsAreThePlainListInsideTheEnvelope() throws Exception {
        Mockito.when(events.listEvents()).thenReturn(ResponseEntity.ok(CommonResponseObject.success("events",
                List.of(Map.of("id", "e1", "value", "RIDE_BOOKED"), Map.of("id", "e2", "value", "RIDE_CANCELLED")))));
        List<Map<String, Object>> rows = new ArrayList<>();

        source().scan(WebListChanges.EVENT, WebListSource.Partition.organization("default"), rows::add);

        assertThat(rows).extracting(row -> row.get("value")).containsExactly("RIDE_BOOKED", "RIDE_CANCELLED");
        Mockito.verify(events, Mockito.times(1)).listEvents();
    }

    @Test
    void aFailedListAnswerFailsThePartitionInsteadOfEmptyingIt() throws Exception {
        Mockito.when(broadcasts.list(any())).thenReturn(ResponseEntity.ok(CommonResponseObject.failure("boom")));

        assertThatThrownBy(() -> source().scan(WebListChanges.BROADCAST, WebListSource.Partition.organization("acme"), row -> { }))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("boom");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
