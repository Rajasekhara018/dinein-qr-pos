package com.heuristiq.gateway.audit;

import org.springframework.core.io.buffer.DataBuffer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * Tees the bytes of one body that is flowing through the gateway, up to a cap. It only reads: the buffers are
 * never consumed, copied wholesale or altered, so what is sent on is byte-for-byte what came in. Memory per body
 * is bounded by the cap. A gzip body is kept compressed (also capped) and inflated once at the end.
 */
public final class AuditBodyCapture {

    private final int cap;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private long total;
    private boolean cut;
    private boolean collect = true;

    /**
     * Creates a capture.
     *
     * @param cap most bytes kept
     */
    public AuditBodyCapture(int cap) {
        this.cap = cap;
    }

    /** Stops keeping bytes (the content type or encoding is not capturable); the size is still counted. */
    public synchronized void stopCollecting() {
        collect = false;
    }

    /**
     * Observes one buffer on its way through.
     *
     * @param buffer the data buffer; its read position is not changed
     */
    public synchronized void accept(DataBuffer buffer) {
        int readable = buffer.readableByteCount();
        total += readable;
        if (!collect) {
            return;
        }
        int room = cap - bytes.size();
        if (room <= 0) {
            if (readable > 0) {
                cut = true;
            }
            return;
        }
        int want = Math.min(room, readable);
        if (want < readable) {
            cut = true;
        }
        try (DataBuffer.ByteBufferIterator it = buffer.readableByteBuffers()) {
            while (it.hasNext() && want > 0) {
                ByteBuffer view = it.next().duplicate();
                int n = Math.min(view.remaining(), want);
                byte[] chunk = new byte[n];
                view.get(chunk);
                bytes.write(chunk, 0, n);
                want -= n;
            }
        }
    }

    /** @return the number of body bytes seen */
    public synchronized long totalBytes() {
        return total;
    }

    /** @return true when nothing flowed through */
    public synchronized boolean isEmpty() {
        return total == 0;
    }

    /** Result of {@link #snapshot}. */
    public record Snapshot(String text, boolean truncated, long size) {
    }

    /**
     * @param contentType     the body's content type, used for its charset
     * @param contentEncoding the {@code Content-Encoding} header, or null
     * @return the captured text (null when nothing could be kept), whether it was cut, and the original size
     */
    public synchronized Snapshot snapshot(String contentType, String contentEncoding) {
        if (!collect || bytes.size() == 0) {
            return new Snapshot(null, false, total);
        }
        byte[] raw = bytes.toByteArray();
        Charset charset = charsetOf(contentType);
        String enc = contentEncoding == null ? "" : contentEncoding.toLowerCase(Locale.ROOT).trim();
        if (enc.isEmpty() || enc.equals("identity")) {
            return new Snapshot(new String(raw, charset), cut, total);
        }
        if (enc.equals("gzip") || enc.equals("x-gzip")) {
            return inflate(raw, charset);
        }
        return new Snapshot(null, false, total);
    }

    private Snapshot inflate(byte[] gz, Charset charset) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean truncated = cut;
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            byte[] buf = new byte[2048];
            int n;
            while ((n = in.read(buf)) > 0) {
                int room = cap - out.size();
                if (room <= 0) {
                    truncated = true;
                    break;
                }
                out.write(buf, 0, Math.min(n, room));
                if (n > room) {
                    truncated = true;
                    break;
                }
            }
        } catch (IOException cutStream) {
            // the compressed bytes were cut at the cap: keep what inflated before that
            truncated = true;
        }
        return out.size() == 0 ? new Snapshot(null, false, total) : new Snapshot(new String(out.toByteArray(), charset), truncated, total);
    }

    private static Charset charsetOf(String contentType) {
        if (contentType != null) {
            int i = contentType.toLowerCase(Locale.ROOT).indexOf("charset=");
            if (i >= 0) {
                String name = contentType.substring(i + 8).split("[;\\s]")[0].replace("\"", "");
                try {
                    return Charset.forName(name);
                } catch (RuntimeException unknown) {
                    return StandardCharsets.UTF_8;
                }
            }
        }
        return StandardCharsets.UTF_8;
    }
}
