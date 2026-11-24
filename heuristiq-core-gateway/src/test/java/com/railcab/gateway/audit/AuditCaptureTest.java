package com.heuristiq.gateway.audit;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** The capture only observes: bytes, order and buffer state must be exactly what they were without it. */
class AuditCaptureTest {

    private static final DefaultDataBufferFactory FACTORY = new DefaultDataBufferFactory();

    private static DataBuffer buffer(String text) {
        return FACTORY.wrap(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(List<DataBuffer> buffers) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (DataBuffer b : buffers) {
            byte[] bytes = new byte[b.readableByteCount()];
            b.read(bytes);
            out.writeBytes(bytes);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void captureDoesNotMoveTheReadPositionOrConsumeTheBuffer() {
        AuditBodyCapture capture = new AuditBodyCapture(100);
        DataBuffer b = buffer("{\"a\":1}");
        capture.accept(b);
        assertThat(b.readableByteCount()).isEqualTo(7);
        assertThat(((DefaultDataBuffer) b).readPosition()).isZero();
        assertThat(capture.snapshot("application/json", null).text()).isEqualTo("{\"a\":1}");
    }

    @Test
    void bodiesAreCutAtTheCapAndFlaggedWithTheirOriginalSize() {
        AuditBodyCapture capture = new AuditBodyCapture(10);
        capture.accept(buffer("0123456"));
        capture.accept(buffer("789abcdef"));
        capture.accept(buffer("more"));
        AuditBodyCapture.Snapshot s = capture.snapshot("text/plain", null);
        assertThat(s.text()).isEqualTo("0123456789");
        assertThat(s.truncated()).isTrue();
        assertThat(s.size()).isEqualTo(7 + 9 + 4);
    }

    @Test
    void aBodyExactlyAtTheCapIsNotTruncated() {
        AuditBodyCapture capture = new AuditBodyCapture(5);
        capture.accept(buffer("12345"));
        assertThat(capture.snapshot(null, null).truncated()).isFalse();
    }

    @Test
    void nonCollectableBodiesOnlyCountBytes() {
        AuditBodyCapture capture = new AuditBodyCapture(100);
        capture.stopCollecting();
        capture.accept(buffer("binary"));
        AuditBodyCapture.Snapshot s = capture.snapshot("image/png", null);
        assertThat(s.text()).isNull();
        assertThat(s.size()).isEqualTo(6);
    }

    @Test
    void gzipBodiesAreInflatedAndCutStreamsKeepWhatInflated() throws Exception {
        String json = "{\"name\":\"" + "abcdefghij".repeat(500) + "\"}";
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(gz)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }
        byte[] compressed = gz.toByteArray();

        AuditBodyCapture whole = new AuditBodyCapture(100_000);
        whole.accept(FACTORY.wrap(compressed));
        assertThat(whole.snapshot("application/json", "gzip").text()).isEqualTo(json);

        AuditBodyCapture capped = new AuditBodyCapture(40);
        capped.accept(FACTORY.wrap(compressed));
        AuditBodyCapture.Snapshot cut = capped.snapshot("application/json", "gzip");
        assertThat(cut.truncated()).isTrue();
        assertThat(cut.text() == null || json.startsWith(cut.text())).isTrue();
        assertThat(new AuditBodyCapture(10).snapshot("application/json", "br").text()).isNull();
    }

    @Test
    void requestDecoratorForwardsTheExactBytesAndCapturesThem() {
        AuditBodyCapture capture = new AuditBodyCapture(1000);
        ServerHttpRequest original = MockServerHttpRequest.method(HttpMethod.POST, "/x")
                .header("Content-Type", "application/json")
                .body(Flux.just(buffer("{\"pass"), buffer("word\":\"x\"}")));
        ServerHttpRequest decorated = AuditCaptureDecorators.request(original, capture);

        List<DataBuffer> seen = new ArrayList<>();
        StepVerifier.create(decorated.getBody().doOnNext(seen::add)).expectNextCount(2).verifyComplete();
        assertThat(read(seen)).isEqualTo("{\"password\":\"x\"}");
        assertThat(capture.snapshot("application/json", null).text()).isEqualTo("{\"password\":\"x\"}");
        assertThat(decorated.getHeaders().getFirst("Content-Type")).isEqualTo("application/json");
        assertThat(decorated.getURI().getPath()).isEqualTo("/x");
    }

    @Test
    void requestDecoratorDoesNotCollectBinaryUploads() {
        AuditBodyCapture capture = new AuditBodyCapture(1000);
        ServerHttpRequest original = MockServerHttpRequest.method(HttpMethod.POST, "/upload")
                .header("Content-Type", "multipart/form-data; boundary=x").body(Flux.just(buffer("--x\r\nfile bytes")));
        ServerHttpRequest decorated = AuditCaptureDecorators.request(original, capture);
        StepVerifier.create(decorated.getBody()).expectNextCount(1).verifyComplete();
        assertThat(capture.snapshot("multipart/form-data", null).text()).isNull();
        assertThat(capture.totalBytes()).isEqualTo(15);
    }

    @Test
    void requestErrorsPassThrough() {
        AuditBodyCapture capture = new AuditBodyCapture(1000);
        ServerHttpRequest original = MockServerHttpRequest.method(HttpMethod.POST, "/x")
                .header("Content-Type", "application/json")
                .body(Flux.concat(Flux.just(buffer("abc")), Flux.error(new IllegalStateException("client reset"))));
        StepVerifier.create(AuditCaptureDecorators.request(original, capture).getBody())
                .expectNextCount(1).expectErrorMessage("client reset").verify();
    }

    @Test
    void responseDecoratorWritesTheExactBytesAndCapturesThem() {
        AuditBodyCapture capture = new AuditBodyCapture(1000);
        MockServerHttpResponse original = new MockServerHttpResponse();
        original.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        ServerHttpResponse decorated = AuditCaptureDecorators.response(original, capture);

        StepVerifier.create(decorated.writeWith(Flux.just(buffer("{\"token\""), buffer(":\"t\"}")))).verifyComplete();
        StepVerifier.create(original.getBodyAsString()).expectNext("{\"token\":\"t\"}").verifyComplete();
        assertThat(capture.snapshot("application/json", null).text()).isEqualTo("{\"token\":\"t\"}");
    }

    @Test
    void responseDecoratorSkipsBinaryTypesButStillWritesThem() {
        AuditBodyCapture capture = new AuditBodyCapture(1000);
        MockServerHttpResponse original = new MockServerHttpResponse();
        original.getHeaders().setContentType(MediaType.APPLICATION_PDF);
        StepVerifier.create(AuditCaptureDecorators.response(original, capture).writeWith(Flux.just(buffer("%PDF-1.4"))))
                .verifyComplete();
        StepVerifier.create(original.getBodyAsString()).expectNext("%PDF-1.4").verifyComplete();
        assertThat(capture.snapshot("application/pdf", null).text()).isNull();
        assertThat(capture.totalBytes()).isEqualTo(8);
    }

    @Test
    void responseDecoratorSupportsFlushingWrites() {
        AuditBodyCapture capture = new AuditBodyCapture(1000);
        MockServerHttpResponse original = new MockServerHttpResponse();
        original.getHeaders().setContentType(MediaType.TEXT_PLAIN);
        StepVerifier.create(AuditCaptureDecorators.response(original, capture)
                .writeAndFlushWith(Flux.just(Flux.just(buffer("one")), Flux.just(buffer("two"))))).verifyComplete();
        assertThat(capture.snapshot("text/plain", null).text()).isEqualTo("onetwo");
    }
}
