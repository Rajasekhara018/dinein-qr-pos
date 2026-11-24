package com.heuristiq.gateway.audit;

import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Decorators that let an {@link AuditBodyCapture} watch a request or response body stream. They only observe
 * ({@code doOnNext}): the original publisher, its buffers, ordering, back-pressure and errors are passed through
 * untouched, so the bytes forwarded to the backend or the client are identical with or without capture.
 */
public final class AuditCaptureDecorators {

    private AuditCaptureDecorators() {
    }

    /**
     * @param delegate the request
     * @param capture  where the body bytes are teed
     * @return a request whose {@code getBody()} feeds the capture
     */
    public static ServerHttpRequest request(ServerHttpRequest delegate, AuditBodyCapture capture) {
        HttpHeaders headers = delegate.getHeaders();
        if (!isCollectable(headers.getFirst(HttpHeaders.CONTENT_TYPE), headers.getFirst(HttpHeaders.CONTENT_ENCODING))) {
            capture.stopCollecting();
        }
        return new ServerHttpRequestDecorator(delegate) {
            @Override
            public Flux<DataBuffer> getBody() {
                return super.getBody().doOnNext(capture::accept);
            }
        };
    }

    /**
     * @param delegate the response
     * @param capture  where the body bytes are teed
     * @return a response whose written body feeds the capture
     */
    public static ServerHttpResponse response(ServerHttpResponse delegate, AuditBodyCapture capture) {
        AtomicBoolean decided = new AtomicBoolean();
        return new ServerHttpResponseDecorator(delegate) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                decide();
                return super.writeWith(Flux.from(body).doOnNext(capture::accept));
            }

            @Override
            public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
                decide();
                return super.writeAndFlushWith(Flux.from(body).map(inner -> Flux.from(inner).doOnNext(capture::accept)));
            }

            private void decide() {
                if (decided.compareAndSet(false, true)) {
                    HttpHeaders h = getHeaders();
                    if (!isCollectable(h.getFirst(HttpHeaders.CONTENT_TYPE), h.getFirst(HttpHeaders.CONTENT_ENCODING))) {
                        capture.stopCollecting();
                    }
                }
            }
        };
    }

    /** @return true when a body of this type and encoding is worth keeping (JSON, text, form; plain or gzip) */
    static boolean isCollectable(String contentType, String contentEncoding) {
        if (!AuditPayloadRedactor.isCapturableContentType(contentType)) {
            return false;
        }
        if (contentEncoding == null || contentEncoding.isBlank()) {
            return true;
        }
        String e = contentEncoding.toLowerCase(Locale.ROOT).trim();
        return e.equals("identity") || e.equals("gzip") || e.equals("x-gzip");
    }
}
