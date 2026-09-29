package com.heuristq.dinein.notification;

import com.heuristq.dinein.notification.NotificationTemplates.Message;
import com.heuristq.dinein.settings.SettingsService;
import com.heuristq.dinein.shared.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NotificationTemplatesTest {

    private static final Map<String, String> DATA = Map.of("orderId", "42", "token", "17",
            "orderNumber", "260926-017", "table", "T3", "reason", "Captured 100 paise but order total is 200 paise");

    private final NotificationTemplates templates = new NotificationTemplates(mock(SettingsService.class),
            new AppProperties("https://dine.example.com/", null, null, null, null, null, null, null));

    @Test
    void readySmsContainsTokenAndRestaurant() {
        Message m = templates.render(NotificationEvent.ORDER_READY, NotificationChannel.SMS, DATA, "Spice Garden");

        assertThat(m.body()).isEqualTo("Your order #17 is ready. - Spice Garden");
    }

    @Test
    void smsStaysWithin160CharactersWithLongRestaurantName() {
        String longName = "The Very Long Named Multi Cuisine Family Restaurant And Banquet Hall Of Downtown Hyderabad "
                + "Near The Old Clock Tower";
        for (NotificationEvent event : new NotificationEvent[]{NotificationEvent.ORDER_READY,
                NotificationEvent.ORDER_CANCELLED}) {
            Message m = templates.render(event, NotificationChannel.SMS, DATA, longName);

            assertThat(m.body()).hasSizeLessThanOrEqualTo(NotificationTemplates.SMS_MAX).contains("#17");
        }
    }

    @Test
    void cancelledSmsMentionsRefund() {
        Message m = templates.render(NotificationEvent.ORDER_CANCELLED, NotificationChannel.SMS, DATA, "Spice Garden");

        assertThat(m.body()).contains("cancelled").contains("refund");
    }

    @Test
    void confirmedInAppNamesTokenAndTable() {
        Message m = templates.render(NotificationEvent.ORDER_CONFIRMED, NotificationChannel.IN_APP, DATA, "Spice Garden");

        assertThat(m.subject()).isEqualTo("New order #17, table T3");
        assertThat(m.link()).isEqualTo("/admin/orders/42");
    }

    @Test
    void readyInAppIsAddressedToWaiters() {
        Message m = templates.render(NotificationEvent.ORDER_READY, NotificationChannel.IN_APP, DATA, "Spice Garden");

        assertThat(m.subject()).isEqualTo("Order #17 is ready, table T3");
        assertThat(m.link()).isEqualTo("/waiter");
    }

    @Test
    void flaggedEmailHasReasonAndAbsoluteLink() {
        Message m = templates.render(NotificationEvent.PAYMENT_FLAGGED, NotificationChannel.EMAIL, DATA, "Spice Garden");

        assertThat(m.subject()).contains("260926-017");
        assertThat(m.body()).contains(DATA.get("reason")).contains("https://dine.example.com/admin/orders/42");
    }
}
