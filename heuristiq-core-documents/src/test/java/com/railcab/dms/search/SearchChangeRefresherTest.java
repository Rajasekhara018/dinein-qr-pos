package com.railcab.dms.search;

import com.railcab.dms.search.projection.ProjectionConstants;
import com.railcab.dms.search.projection.WebProjectionPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/** A change made through the DMS API reaches its search-backed lists within seconds, without a re-index. */
class SearchChangeRefresherTest {

    private final WebProjectionPublisher publisher = Mockito.mock(WebProjectionPublisher.class);

    private long publishes() {
        return Mockito.mockingDetails(publisher).getInvocations().stream().filter(i -> i.getMethod().getName().equals("publish")).count();
    }

    private void await(long count) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (publishes() < count && Instant.now().isBefore(deadline)) {
            Thread.sleep(10);
        }
        Thread.sleep(100);
    }

    @Test
    void theRulesNameTheListsAChangeAffects() {
        assertThat(SearchChangeRefresher.affected("/api/v1/dms/document-types/t1")).containsExactly(ProjectionConstants.MODEL_DOCUMENT_TYPE);
        assertThat(SearchChangeRefresher.affected("/api/v1/dms/internal/document-types")).containsExactly(ProjectionConstants.MODEL_DOCUMENT_TYPE);
        assertThat(SearchChangeRefresher.affected("/api/v1/dms/documents/d1/approve")).containsExactly(ProjectionConstants.MODEL_LIFECYCLE_DOCUMENT);
        assertThat(SearchChangeRefresher.affected("/api/v1/dms/search/reindex")).isEmpty();
        assertThat(SearchChangeRefresher.affected(null)).isEmpty();
    }

    @Test
    void changesCloseTogetherAreSentOnceForTheOrganization() throws Exception {
        Mockito.when(publisher.models()).thenReturn(List.of(ProjectionConstants.MODEL_DOCUMENT_TYPE));
        Mockito.when(publisher.publish(any(), any(), any())).thenReturn(
                new WebProjectionPublisher.Outcome(WebProjectionPublisher.Status.PUBLISHED, 4, 0, null));
        SearchChangeRefresher refresher = new SearchChangeRefresher(publisher, 50, 50, 5);

        refresher.changed(List.of(ProjectionConstants.MODEL_DOCUMENT_TYPE), "acme");
        refresher.changed(List.of(ProjectionConstants.MODEL_DOCUMENT_TYPE), "acme");
        refresher.changed(List.of(ProjectionConstants.MODEL_DOCUMENT_TYPE), null);
        await(1);

        Mockito.verify(publisher, Mockito.times(1)).publish(eq(ProjectionConstants.MODEL_DOCUMENT_TYPE), eq("acme"), any());
        refresher.stop();
    }

    @Test
    void aFailedSendIsTriedAgainAndThenGivenUp() throws Exception {
        Mockito.when(publisher.models()).thenReturn(List.of(ProjectionConstants.MODEL_LIFECYCLE_DOCUMENT));
        Mockito.when(publisher.publish(any(), anyString(), any())).thenReturn(
                new WebProjectionPublisher.Outcome(WebProjectionPublisher.Status.FAILED, 0, 0, "search unreachable"));
        SearchChangeRefresher refresher = new SearchChangeRefresher(publisher, 10, 20, 3);

        refresher.changed(List.of(ProjectionConstants.MODEL_LIFECYCLE_DOCUMENT), "acme");
        await(3);

        Mockito.verify(publisher, Mockito.times(3)).publish(eq(ProjectionConstants.MODEL_LIFECYCLE_DOCUMENT), eq("acme"), any());
        refresher.stop();
    }
}
