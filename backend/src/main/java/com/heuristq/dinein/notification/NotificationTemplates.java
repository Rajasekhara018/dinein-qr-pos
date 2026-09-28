package com.heuristq.dinein.notification;

import com.heuristq.dinein.settings.SettingsService;
import com.heuristq.dinein.shared.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Plain-English text per event and channel. Data keys: {@code orderId}, {@code token}, {@code orderNumber},
 * {@code table}, {@code reason}. SMS is kept within one 160-character segment by shortening the restaurant name.
 * Links are app-relative; {@link #absolute(String)} turns them into full URLs for email and push.
 */
@Slf4j
@Component
public class NotificationTemplates {

    public static final int SMS_MAX = 160;

    public record Message(String subject, String body, String link) {
    }

    private final SettingsService settingsService;
    private final String baseUrl;

    public NotificationTemplates(SettingsService settingsService, AppProperties appProperties) {
        this.settingsService = settingsService;
        String url = appProperties.publicBaseUrl();
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public String restaurantName(Long restaurantId) {
        try {
            return settingsService.forRestaurant(restaurantId).getName();
        } catch (RuntimeException e) {
            log.warn("notification.restaurant_name_unavailable error={}", e.getMessage());
            return "Restaurant";
        }
    }

    public String absolute(String link) {
        return link == null ? null : baseUrl + link;
    }

    public Message render(NotificationEvent event, NotificationChannel channel, Map<String, String> data,
                          String restaurant) {
        String orderId = data.getOrDefault("orderId", "");
        String token = data.getOrDefault("token", "?");
        String orderNumber = data.getOrDefault("orderNumber", "");
        String table = data.getOrDefault("table", "-");
        String reason = data.getOrDefault("reason", "unknown reason");
        String adminLink = "/admin/orders/" + orderId;
        String guestLink = "/menu/orders/" + orderId;
        return switch (event) {
            case ORDER_CONFIRMED -> new Message("New order #" + token + ", table " + table,
                    "Order " + orderNumber + " is paid and has been sent to the kitchen.", adminLink);
            case ORDER_READY -> switch (channel) {
                case SMS -> new Message(null, sms("Your order #" + token + " is ready.", restaurant), null);
                // In-app goes to the waiters' inbox: time to take the food to the table.
                case IN_APP -> new Message("Order #" + token + " is ready, table " + table,
                        "Order " + orderNumber + " is ready to be served.", "/waiter");
                default -> new Message("Your order #" + token + " is ready", restaurant + ": your order is ready.",
                        guestLink);
            };
            case ORDER_CANCELLED -> channel == NotificationChannel.SMS
                    ? new Message(null, sms("Your order #" + token + " was cancelled and a refund has been initiated.",
                    restaurant), null)
                    : new Message("Your order #" + token + " was cancelled",
                    restaurant + ": your order was cancelled and a refund has been initiated.", guestLink);
            case PAYMENT_FLAGGED -> channel == NotificationChannel.EMAIL
                    ? new Message(restaurant + ": payment needs attention for order " + orderNumber,
                    "Order " + orderNumber + " (token #" + token + ", table " + table + ") was flagged: " + reason
                            + ".\n\nThe order has not been sent to the kitchen. Please review it in the admin panel:\n"
                            + absolute(adminLink) + "\n\n- " + restaurant, adminLink)
                    : new Message("Payment flagged: order #" + token, reason, adminLink);
            case REFUND_FAILED -> channel == NotificationChannel.EMAIL
                    ? new Message(restaurant + ": refund failed for order " + orderNumber,
                    "The refund for cancelled order " + orderNumber + " (token #" + token + ") could not be completed."
                            + "\n\nRetry it by cancelling the order again in the admin panel, or refund it from the "
                            + "payment provider's dashboard:\n" + absolute(adminLink) + "\n\n- " + restaurant, adminLink)
                    : new Message("Refund failed: order #" + token,
                    "Refund for order " + orderNumber + " failed. Retry from the order page.", adminLink);
        };
    }

    /** Appends the restaurant name as signature, shortening it (never the message) to stay within one SMS. */
    static String sms(String core, String restaurant) {
        String signed = core + " - " + restaurant;
        if (signed.length() <= SMS_MAX) {
            return signed;
        }
        int room = SMS_MAX - core.length() - 3;
        if (room >= 4) {
            return core + " - " + restaurant.substring(0, room);
        }
        return core.length() <= SMS_MAX ? core : core.substring(0, SMS_MAX);
    }
}
