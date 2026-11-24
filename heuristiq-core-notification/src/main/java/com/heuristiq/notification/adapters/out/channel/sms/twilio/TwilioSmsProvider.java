package com.railcab.nms.adapters.out.channel.sms.twilio;

import com.railcab.nms.adapters.out.channel.VendorPhoneFormat;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.railcab.nms.infrastructure.config.NmsProperties;
import com.railcab.nms.adapters.out.channel.sms.SmsProvider;
import com.railcab.nms.application.port.out.ProviderSendResult;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;

import lombok.RequiredArgsConstructor;

import static com.railcab.nms.application.port.out.ChannelProviderConstants.*;

@Component("twilioSmsProvider")
@RequiredArgsConstructor
public class TwilioSmsProvider implements SmsProvider {

    private static final Logger logger = LoggerFactory.getLogger(TwilioSmsProvider.class);

    private final NmsProperties props;

    @Override
    public String getName() { return PROVIDER_TWILIO; }

    @Override
    public boolean send(String to, String body, String templateId) {
        return sendDetailed(to, body, templateId).success();
    }

    @Override
    public ProviderSendResult sendDetailed(String to, String body, String templateId) {
        NmsProperties.Twilio twilio = props.getTwilio();
        if (!StringUtils.hasText(twilio.getAccountSid())) {
            logger.error("TwilioSmsProvider: credentials not configured");
            return ProviderSendResult.failed(PROVIDER_TWILIO, CODE_NOT_CONFIGURED, "Twilio credentials not configured");
        }
        logger.info("TwilioSmsProvider.send to a recipient ending {}", tail(to));
        try {
            Twilio.init(twilio.getAccountSid(), twilio.getAuthToken());
            MessageCreator creator = Message.creator(
                    new PhoneNumber(VendorPhoneFormat.e164(to)),
                    new PhoneNumber(twilio.getSmsFrom()),
                    body);
            String callback = props.getCallbacks().getTwilioCallbackUrl();
            if (StringUtils.hasText(callback)) {
                creator.setStatusCallback(URI.create(callback));
            }
            Message message = creator.create();
            logger.info("TwilioSmsProvider: sent sid={}, status={}", message.getSid(), message.getStatus());
            return ProviderSendResult.ok(PROVIDER_TWILIO, message.getSid(), String.valueOf(message.getStatus()));
        } catch (Exception e) {
            logger.error("TwilioSmsProvider.send failed for a recipient ending {}: {}", tail(to), e.getMessage(), e);
            return ProviderSendResult.failed(PROVIDER_TWILIO, ProviderSendResult.CODE_EXCEPTION, e.getMessage());
        }
    }

    private static String tail(String value) {
        return value == null || value.length() <= 4 ? "****" : value.substring(value.length() - 4);
    }
}
