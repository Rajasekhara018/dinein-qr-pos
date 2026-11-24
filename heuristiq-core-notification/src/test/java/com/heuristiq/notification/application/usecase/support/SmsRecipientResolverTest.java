package com.heuristiq.notification.application.usecase.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.heuristiq.notification.application.dto.NotificationRequest;
import com.heuristiq.notification.application.port.out.UserPhoneDirectory;
import com.heuristiq.notification.domain.model.NotificationChannel;

/** SMS goes to an explicit phone, else to the numbers UAM holds for the request's users (D12 reminders). */
class SmsRecipientResolverTest {

    private final UserPhoneDirectory phones = mock(UserPhoneDirectory.class);
    private final SmsRecipientResolver resolver = new SmsRecipientResolver(phones);

    private static NotificationRequest request(String toPhone, List<String> userIds) {
        NotificationRequest request = new NotificationRequest();
        request.setToPhone(toPhone);
        request.setUserIds(userIds);
        return request;
    }

    @Test
    void anExplicitPhoneWinsAndUamIsNotAsked() {
        assertThat(resolver.channel()).isEqualTo(NotificationChannel.SMS);
        assertThat(resolver.resolve(request("+919876543210", List.of("u1")))).containsExactly("+919876543210");
        verifyNoInteractions(phones);
    }

    @Test
    void withoutAPhoneTheUsersNumbersAreLookedUp() {
        Map<String, String> found = new LinkedHashMap<>();
        found.put("u1", "+919876543210");
        found.put("u2", "+919812345678");
        when(phones.phonesOf(List.of("u1", "u2", "u3"))).thenReturn(found);

        assertThat(resolver.resolve(request(" ", List.of("u1", "u2", "u3")))).containsExactly("+919876543210", "+919812345678");
    }

    @Test
    void noPhoneAndNoUsersOrAnUnansweredLookupSendsNothing() {
        when(phones.phonesOf(any())).thenReturn(Map.of());

        assertThat(resolver.resolve(request(null, null))).isEmpty();
        assertThat(resolver.resolve(request(null, List.of("u1")))).isEmpty();
        verify(phones).phonesOf(List.of("u1"));
        assertThat(new SmsRecipientResolver().resolve(request(null, List.of("u1")))).isEmpty();
    }
}
