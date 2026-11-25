package com.heuristiq.notification.search.weblist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.heuristiq.notification.application.security.NmsPrincipal;

/** A successful change sends the lists it affects again within seconds: the caller's organization when that is certain, else all of it. */
class WebListChangesTest {

    private static final String TEMPLATE = WebListChanges.TEMPLATE;
    private static final String EVENT_TEMPLATE = WebListChanges.EVENT_TEMPLATE;
    private static final String EVENT = WebListChanges.EVENT;
    private static final String BROADCAST = WebListChanges.BROADCAST;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static WebListChanges.Change whole(String model) {
        return new WebListChanges.Change(model, false);
    }

    @Test
    void eachControllersChangesMapToTheirLists() {
        assertThat(WebListChanges.of("POST", "/api/v1/nms/templates")).containsExactly(whole(TEMPLATE), whole(EVENT_TEMPLATE));
        assertThat(WebListChanges.of("PUT", "/api/v1/nms/templates/t1")).containsExactly(whole(TEMPLATE), whole(EVENT_TEMPLATE));
        assertThat(WebListChanges.of("DELETE", "/api/v1/nms/templates/t1/")).containsExactly(whole(TEMPLATE), whole(EVENT_TEMPLATE));
        assertThat(WebListChanges.of("POST", "/api/v1/nms/events")).containsExactly(whole(EVENT));
        assertThat(WebListChanges.of("PUT", "/api/v1/nms/events/e1")).containsExactly(whole(EVENT));
        assertThat(WebListChanges.of("POST", "/api/v1/nms/events/reset-defaults")).containsExactly(whole(EVENT));
        assertThat(WebListChanges.of("POST", "/api/v1/nms/broadcasts")).containsExactly(new WebListChanges.Change(BROADCAST, true));
        assertThat(WebListChanges.of("PATCH", "/api/v1/nms/broadcasts/b1/cancel")).containsExactly(whole(BROADCAST));
        assertThat(WebListChanges.of("POST", "/api/v1/nms/search/reindex"))
                .containsExactly(whole(TEMPLATE), whole(EVENT_TEMPLATE), whole(EVENT), whole(BROADCAST));
    }

    @Test
    void aChangeOutsideTheListsSendsNothing() {
        assertThat(WebListChanges.of("POST", "/api/v1/nms/templates/test-send")).isEmpty();
        assertThat(WebListChanges.of("POST", "/api/v1/nms/notify")).isEmpty();
        assertThat(WebListChanges.of("POST", "/api/v1/nms/devices")).isEmpty();
        assertThat(WebListChanges.of("POST", "/api/v1/nms/templatesX")).isEmpty();
        assertThat(WebListChanges.of("POST", "/api/v1/other/templates")).isEmpty();
        assertThat(WebListChanges.of("POST", null)).isEmpty();
    }

    @Test
    void theInterceptorSendsOnlySuccessfulChangesWithTheCallersOrganization() {
        WebListPublisher publisher = Mockito.mock(WebListPublisher.class);
        WebListChangeInterceptor interceptor = new WebListChangeInterceptor(publisher);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new NmsPrincipal("u1", "acme", false), null, List.of()));

        interceptor.afterCompletion(request("POST", "/api/v1/nms/broadcasts"), response(201), null, null);
        interceptor.afterCompletion(request("PUT", "/api/v1/nms/templates/t1"), response(200), null, null);
        // Not changes, or not successful ones:
        interceptor.afterCompletion(request("GET", "/api/v1/nms/templates"), response(200), null, null);
        interceptor.afterCompletion(request("POST", "/api/v1/nms/events"), response(400), null, null);
        interceptor.afterCompletion(request("POST", "/api/v1/nms/events"), response(200), null, new IllegalStateException("x"));

        Mockito.verify(publisher).changed(BROADCAST, "acme");
        Mockito.verify(publisher).changed(TEMPLATE, null);
        Mockito.verify(publisher).changed(EVENT_TEMPLATE, null);
        Mockito.verifyNoMoreInteractions(publisher);
    }

    @Test
    void aCallerWithoutAnOrganizationSendsTheWholeList() {
        WebListPublisher publisher = Mockito.mock(WebListPublisher.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new NmsPrincipal("internal-service", null, true), null, List.of()));

        new WebListChangeInterceptor(publisher).afterCompletion(request("POST", "/api/v1/nms/broadcasts"), response(201), null, null);

        Mockito.verify(publisher).changed(BROADCAST, null);
    }

    @Test
    void changesCloseTogetherAreSentOnceSoonAfterwards() throws Exception {
        WebListSource source = Mockito.mock(WebListSource.class);
        WebListTransport transport = Mockito.mock(WebListTransport.class);
        WebListProperties properties = new WebListProperties(true, "http://search", "token", 600_000, 3_600_000, 50);
        WebListPublisher publisher = new WebListPublisher(source, transport, properties);
        WebListSource.Partition fallback = WebListSource.Partition.organization("default");
        WebListSource.Partition acme = WebListSource.Partition.organization("acme");
        Mockito.when(source.partitions(TEMPLATE)).thenReturn(List.of(fallback, acme));
        Mockito.when(source.spec(any())).thenReturn(WebListPublisherTest.JSON.createObjectNode());

        publisher.start();
        try {
            publisher.changed(TEMPLATE, null);
            publisher.changed(TEMPLATE, null);
            publisher.changed(TEMPLATE, "acme");
            publisher.changed(BROADCAST, "acme");
            publisher.changed(BROADCAST, "acme");

            Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
            while (Mockito.mockingDetails(transport).getInvocations().stream()
                    .noneMatch(i -> "COMPLETE".equals(i.getArgument(0)) && BROADCAST.equals(i.getArgument(1)))
                    && Instant.now().isBefore(deadline)) {
                Thread.sleep(20);
            }
            Thread.sleep(100);
        } finally {
            publisher.stop();
        }

        // The whole template list once (three changes, one send) and marked complete; only acme's broadcasts, once.
        Mockito.verify(transport, Mockito.times(1)).send(eq("BEGIN"), eq(TEMPLATE), eq(fallback), anyString(), anyList());
        Mockito.verify(transport, Mockito.times(1)).send(eq("BEGIN"), eq(TEMPLATE), eq(acme), anyString(), anyList());
        Mockito.verify(transport).send(eq("MODEL_COMPLETE"), eq(TEMPLATE), any(), anyString(), anyList());
        Mockito.verify(transport, Mockito.times(1)).send(eq("BEGIN"), eq(BROADCAST), eq(acme), anyString(), anyList());
        Mockito.verify(transport, Mockito.never()).send(eq("MODEL_COMPLETE"), eq(BROADCAST), any(), anyString(), anyList());
        Mockito.verify(source, Mockito.never()).partitions(BROADCAST);
    }

    @Test
    void nothingIsQueuedWhilePublishingIsNotRunning() {
        WebListTransport transport = Mockito.mock(WebListTransport.class);
        new WebListPublisher(Mockito.mock(WebListSource.class), transport, WebListPublisherTest.ON).changed(TEMPLATE, null);
        Mockito.verifyNoInteractions(transport);
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }

    private static MockHttpServletResponse response(int status) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(status);
        return response;
    }
}
