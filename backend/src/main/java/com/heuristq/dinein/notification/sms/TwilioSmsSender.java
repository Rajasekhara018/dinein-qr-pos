package com.heuristq.dinein.notification.sms;

import com.heuristq.dinein.notification.DeliveryException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Twilio Programmable Messaging: {@code POST /2010-04-01/Accounts/{sid}/Messages.json} with HTTP basic auth
 * (account SID + auth token) and a form body ({@code To}, {@code Body}, {@code From} or {@code MessagingServiceSid}).
 */
@Component
public class TwilioSmsSender implements SmsSender {

    public static final String CODE = "TWILIO";

    private final TwilioProperties props;
    private final RestClient restClient;

    public TwilioSmsSender(TwilioProperties props, RestClient.Builder restClientBuilder) {
        this.props = props;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public String provider() {
        return CODE;
    }

    @Override
    public void send(SmsMessage message) {
        if (!props.isConfigured()) {
            throw new IllegalStateException("Twilio is not configured");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", message.to());
        form.add("Body", message.text());
        if (TwilioProperties.notBlank(props.messagingServiceSid())) {
            form.add("MessagingServiceSid", props.messagingServiceSid());
        } else {
            form.add("From", props.fromNumber());
        }
        try {
            restClient.post()
                    .uri(props.baseUrl() + "/2010-04-01/Accounts/{sid}/Messages.json", props.accountSid())
                    .headers(h -> h.setBasicAuth(props.accountSid(), props.authToken()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            // Twilio error bodies echo the phone number, so only the status goes into the log table.
            throw new DeliveryException("Twilio HTTP " + e.getStatusCode().value());
        }
    }
}
