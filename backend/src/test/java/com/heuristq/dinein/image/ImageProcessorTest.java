package com.heuristq.dinein.image;

import com.heuristq.dinein.shared.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessorTest {

    private final ImageProcessor processor = new ImageProcessor();

    private static byte[] image(int w, int h, int type, String format) throws IOException {
        BufferedImage img = new BufferedImage(w, h, type);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(200, 80, 20, type == BufferedImage.TYPE_INT_ARGB ? 128 : 255));
        g.fillRect(0, 0, w / 2, h / 2);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    @Test
    void detectsFormatsByMagicBytesOnly() throws IOException {
        assertThat(ImageProcessor.detectFormat(image(20, 20, BufferedImage.TYPE_INT_RGB, "jpg"))).isEqualTo(ImageProcessor.SourceFormat.JPEG);
        assertThat(ImageProcessor.detectFormat(image(20, 20, BufferedImage.TYPE_INT_ARGB, "png"))).isEqualTo(ImageProcessor.SourceFormat.PNG);
        byte[] webpHeader = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(ImageProcessor.detectFormat(webpHeader)).isEqualTo(ImageProcessor.SourceFormat.WEBP);
        assertThat(ImageProcessor.detectFormat(image(20, 20, BufferedImage.TYPE_INT_RGB, "gif"))).isNull();
        assertThat(ImageProcessor.detectFormat("<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes())).isNull();
    }

    @Test
    void rejectsNonImagesEvenWithImageExtension() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> processor.process(html))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void rejectsFilesOverFiveMegabytes() {
        byte[] big = new byte[ImageProcessor.MAX_UPLOAD_BYTES + 1];
        big[0] = (byte) 0xFF;
        big[1] = (byte) 0xD8;
        big[2] = (byte) 0xFF;

        assertThatThrownBy(() -> processor.process(big))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("FILE_TOO_LARGE"));
    }

    @Test
    void rejectsCorruptedImage() throws IOException {
        byte[] jpeg = image(50, 50, BufferedImage.TYPE_INT_RGB, "jpg");
        byte[] truncated = java.util.Arrays.copyOf(jpeg, 20);

        assertThatThrownBy(() -> processor.process(truncated)).isInstanceOf(ApiException.class);
    }

    @Test
    void resizesLargeJpegToMax800AndThumbTo200() throws IOException {
        ImageProcessor.Processed result = processor.process(image(1600, 1200, BufferedImage.TYPE_INT_RGB, "jpg"));

        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.width()).isEqualTo(800);
        assertThat(result.height()).isEqualTo(600);
        BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(result.thumbnail()));
        assertThat(Math.max(thumb.getWidth(), thumb.getHeight())).isEqualTo(200);
        assertThat(ImageProcessor.detectFormat(result.main())).isEqualTo(ImageProcessor.SourceFormat.JPEG);
    }

    @Test
    void keepsSmallImagesAtOriginalSizeAndPreservesTransparencyAsPng() throws IOException {
        ImageProcessor.Processed result = processor.process(image(300, 150, BufferedImage.TYPE_INT_ARGB, "png"));

        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.width()).isEqualTo(300);
        assertThat(result.height()).isEqualTo(150);
        assertThat(ImageIO.read(new ByteArrayInputStream(result.main())).getColorModel().hasAlpha()).isTrue();
    }
}
