package com.heuristq.dinein.image;

import com.heuristq.dinein.shared.exception.ApiException;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Validates and re-encodes uploads. The raw upload is never stored or served: images are decoded, auto-rotated by
 * EXIF orientation, resized and written fresh (which also strips EXIF/GPS metadata).
 */
@Component
public class ImageProcessor {

    public static final int MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
    static final int MAIN_MAX_PX = 800;
    static final int THUMB_MAX_PX = 200;
    private static final long MAX_PIXELS = 40_000_000L;
    private static final double JPEG_QUALITY = 0.8;

    public enum SourceFormat { JPEG, PNG, WEBP }

    public record Processed(String contentType, byte[] main, byte[] thumbnail, int width, int height) {
    }

    /** Detects the real format from magic bytes; the client's filename and Content-Type are ignored. */
    public static SourceFormat detectFormat(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return null;
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return SourceFormat.JPEG;
        }
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return SourceFormat.PNG;
        }
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return SourceFormat.WEBP;
        }
        return null;
    }

    public Processed process(byte[] upload) {
        if (upload == null || upload.length == 0) {
            throw ApiException.badRequest("EMPTY_FILE", "The file is empty");
        }
        if (upload.length > MAX_UPLOAD_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File must be 5 MB or smaller");
        }
        if (detectFormat(upload) == null) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE",
                    "Only JPEG, PNG and WEBP images are allowed");
        }
        guardDimensions(upload);
        try {
            // Decode once with EXIF orientation applied (Thumbnailator reads the orientation tag for JPEGs).
            BufferedImage oriented = Thumbnails.of(new ByteArrayInputStream(upload))
                    .scale(1.0)
                    .useExifOrientation(true)
                    .asBufferedImage();
            boolean alpha = oriented.getColorModel().hasAlpha();
            String format = alpha ? "png" : "jpg";
            String contentType = alpha ? "image/png" : "image/jpeg";
            BufferedImage mainImage = resize(oriented, MAIN_MAX_PX, alpha);
            byte[] main = encode(mainImage, format);
            byte[] thumb = encode(resize(mainImage, THUMB_MAX_PX, alpha), format);
            return new Processed(contentType, main, thumb, mainImage.getWidth(), mainImage.getHeight());
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException apiException) {
                throw apiException;
            }
            throw ApiException.badRequest("INVALID_IMAGE", "The image could not be read. Is the file corrupted?");
        }
    }

    private BufferedImage resize(BufferedImage source, int maxPx, boolean alpha) throws IOException {
        int w = source.getWidth();
        int h = source.getHeight();
        int imageType = alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        if (w <= maxPx && h <= maxPx) {
            return Thumbnails.of(source).scale(1.0).imageType(imageType).asBufferedImage();
        }
        return Thumbnails.of(source).size(maxPx, maxPx).keepAspectRatio(true).imageType(imageType).asBufferedImage();
    }

    private byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.Builder<BufferedImage> builder = Thumbnails.of(image).scale(1.0).outputFormat(format);
        if ("jpg".equals(format)) {
            builder.outputQuality(JPEG_QUALITY);
        }
        builder.toOutputStream(out);
        return out.toByteArray();
    }

    /** Reads only the header to reject decompression bombs before allocating pixel buffers. */
    private void guardDimensions(byte[] upload) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw ApiException.badRequest("INVALID_IMAGE", "The image could not be read");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw ApiException.badRequest("IMAGE_TOO_LARGE", "Image dimensions are too large");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw ApiException.badRequest("INVALID_IMAGE", "The image could not be read");
        }
    }
}
