package com.heuristiq.documents.storage;

import com.heuristiq.documents.exception.DmsException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StorageHealthIndicatorTest {

    @Test
    void reportsUpDownAndErrorWithProviderName() {
        StorageProviderRegistry reg = mock(StorageProviderRegistry.class);
        StorageProvider provider = mock(StorageProvider.class);
        when(reg.currentProviderName()).thenReturn("local");
        when(reg.current()).thenReturn(provider);
        StorageHealthIndicator ind = new StorageHealthIndicator(reg);

        when(provider.isHealthy()).thenReturn(true);
        assertEquals(Status.UP, ind.health().getStatus());
        when(provider.isHealthy()).thenReturn(false);
        assertEquals(Status.DOWN, ind.health().getStatus());

        when(reg.current()).thenThrow(DmsException.badRequest("Unsupported"));
        var h = ind.health();
        assertEquals(Status.DOWN, h.getStatus());
        assertEquals("Unsupported", h.getDetails().get("error"));
        assertEquals("local", h.getDetails().get("provider"));
    }
}
