package com.railcab.nms.adapters.out.channel.whatsapp.twilio;

import com.railcab.nms.adapters.out.channel.VendorPhoneFormat;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.railcab.nms.infrastructure.config.NmsProperties;
import com.railcab.nms.adapters.out.channel.whatsapp.WhatsAppProvider;
import com.railcab.nms.adapters.out.channel.ChannelAdapterConstants;
import com.railcab.nms.application.port.out.ChannelProviderConstants;
import com.railcab.nms.application.port.out.ProviderSendResult;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;

import lombok.RequiredArgsConstructor;

@Component("twilioWhatsAppProvider")
@RequiredArgsConstructor
public class TwilioWhatsAppProvider implements WhatsAppProvider {

    private static final Logger logger = LoggerFactory.getLogger(TwilioWhatsAppProvider.class);

    private final NmsProperties props;

    @Override
    public String getName() { return ChannelProviderConstants.PROVIDER_TWILIO; }

    @Override
    public boolean send(String to, String body) {
        return sendDetailed(to, body).success();
    }

    @Override
    public ProviderSendResult sendDetailed(String to, String body) {
        NmsProperties.Twilio twilio = props.getTwilio();
        if (!StringUtils.hasText(twilio.getAccountSid())) {
            logger.error("TwilioWhatsAppProvider: credentials not configured");
            return ProviderSendResult.failed(ChannelProviderConstants.PROVIDER_TWILIO, ChannelProviderConstants.CODE_NOT_CONFIGURED, "Twilio credentials not configured");
        }
        logger.info("TwilioWhatsAppProvider.send to a recipient ending {}", tail(to));
        try {
            Twilio.init(twilio.getAccountSid(), twilio.getAuthToken());
            String from = StringUtils.hasText(twilio.getWhatsappFrom())
                    ? twilio.getWhatsappFrom()
                    : ChannelAdapterConstants.TWILIO_WHATSAPP_SANDBOX_FROM;
            String waTo = to.startsWith(ChannelAdapterConstants.TWILIO_WHATSAPP_PREFIX) ? to
                    : ChannelAdapterConstants.TWILIO_WHATSAPP_PREFIX + VendorPhoneFormat.e164(to);
            MessageCreator creator = Message.creator(
                    new PhoneNumber(waTo),
                    new PhoneNumber(from),
                    body);
            String callback = props.getCallbacks().getTwilioCallbackUrl();
            if (StringUtils.hasText(callback)) {
                creator.setStatusCallback(URI.create(callback));
            }
            Message message = creator.create();
            logger.info("TwilioWhatsAppProvider: sent sid={}", message.getSid());
            return ProviderSendResult.ok(ChannelProviderConstants.PROVIDER_TWILIO, message.getSid(), String.valueOf(message.getStatus()));
        } catch (Exception e) {
            logger.error("TwilioWhatsAppProvider.send failed for a recipient ending {}: {}", tail(to), e.getMessage(), e);
            return ProviderSendResult.failed(ChannelProviderConstants.PROVIDER_TWILIO, ProviderSendResult.CODE_EXCEPTION, e.getMessage());
        }
    }

    private static String tail(String value) {
        return value == null || value.length() <= 4 ? "****" : value.substring(value.length() - 4);
    }
}
