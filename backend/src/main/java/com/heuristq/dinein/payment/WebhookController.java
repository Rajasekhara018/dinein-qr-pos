package com.heuristq.dinein.payment;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code POST /api/webhooks/{provider}} (e.g. {@code /api/webhooks/razorpay}). The body is read as raw bytes
 * <em>before</em> any parsing, because signatures are computed over the exact bytes sent.
 */
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private static final int MAX_BODY_BYTES = 256 * 1024;

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/{provider}")
    public ResponseEntity<Map<String, String>> receive(@PathVariable String provider, HttpServletRequest request)
            throws IOException {
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            return ResponseEntity.status(413).build();
        }
        Map<String, String> headers = new TreeMap<>();
        for (String name : Collections.list(request.getHeaderNames())) {
            headers.put(name.toLowerCase(Locale.ROOT), request.getHeader(name));
        }
        WebhookService.Result result = webhookService.handle(provider.toUpperCase(Locale.ROOT), body, headers);
        if (result == WebhookService.Result.INVALID_SIGNATURE) {
            return ResponseEntity.badRequest().body(Map.of("status", "invalid_signature"));
        }
        return ResponseEntity.ok(Map.of("status", result.name().toLowerCase(Locale.ROOT)));
    }
}
