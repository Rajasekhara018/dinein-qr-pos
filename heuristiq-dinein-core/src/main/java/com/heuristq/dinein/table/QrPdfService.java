package com.heuristq.dinein.table;

import com.heuristq.dinein.table.domain.DiningTableEntity;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** A4 sheets with 6 cut-out QR cards (2 x 3), each showing the restaurant name, table label and QR code. */
@Component
public class QrPdfService {

    private static final int COLS = 2;
    private static final int ROWS = 3;
    private static final float MARGIN = 28f;
    private static final float GAP = 14f;

    private final QrCodeService qrCodeService;
    private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    public QrPdfService(QrCodeService qrCodeService) {
        this.qrCodeService = qrCodeService;
    }

    public byte[] render(List<DiningTableEntity> tables, String restaurantName, java.util.function.Function<DiningTableEntity, String> urlFor) {
        try (PDDocument doc = new PDDocument()) {
            PDRectangle a4 = PDRectangle.A4;
            float cardW = (a4.getWidth() - 2 * MARGIN - (COLS - 1) * GAP) / COLS;
            float cardH = (a4.getHeight() - 2 * MARGIN - (ROWS - 1) * GAP) / ROWS;
            int perPage = COLS * ROWS;
            for (int start = 0; start < Math.max(tables.size(), 1); start += perPage) {
                PDPage page = new PDPage(a4);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    for (int i = 0; i < perPage && start + i < tables.size(); i++) {
                        int col = i % COLS;
                        int row = i / COLS;
                        float x = MARGIN + col * (cardW + GAP);
                        float y = a4.getHeight() - MARGIN - (row + 1) * cardH - row * GAP;
                        DiningTableEntity table = tables.get(start + i);
                        drawCard(doc, cs, x, y, cardW, cardH, restaurantName, table.getLabel(), urlFor.apply(table));
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void drawCard(PDDocument doc, PDPageContentStream cs, float x, float y, float w, float h,
                          String restaurant, String label, String url) throws IOException {
        cs.setStrokingColor(new Color(0xD6, 0xD3, 0xD1));
        cs.setLineWidth(0.8f);
        cs.setLineDashPattern(new float[]{4, 3}, 0);
        cs.addRect(x, y, w, h);
        cs.stroke();
        cs.setLineDashPattern(new float[]{}, 0);

        float top = y + h - 30;
        centered(cs, bold, 13, pdfSafe(bold, restaurant), x, w, top, new Color(0x44, 0x40, 0x3C));
        centered(cs, bold, 30, pdfSafe(bold, "Table " + label), x, w, top - 36, new Color(0x1C, 0x19, 0x17));

        float qrSize = Math.min(w - 60, h - 120);
        PDImageXObject qr = LosslessFactory.createFromImage(doc, qrCodeService.render(url, 600));
        cs.drawImage(qr, x + (w - qrSize) / 2, y + 30, qrSize, qrSize);

        centered(cs, regular, 10, "Scan to view the menu, order & pay", x, w, y + 16, new Color(0x57, 0x53, 0x4E));
    }

    private void centered(PDPageContentStream cs, PDType1Font font, float size, String text, float x, float w,
                          float baseline, Color color) throws IOException {
        float textWidth = font.getStringWidth(text) / 1000 * size;
        while (textWidth > w - 16 && size > 6) {
            size -= 1;
            textWidth = font.getStringWidth(text) / 1000 * size;
        }
        cs.beginText();
        cs.setNonStrokingColor(color);
        cs.setFont(font, size);
        cs.newLineAtOffset(x + (w - textWidth) / 2, baseline);
        cs.showText(text);
        cs.endText();
    }

    /** Standard-14 fonts only cover WinAnsi; replace anything else so non-Latin names never break printing. */
    static String pdfSafe(PDType1Font font, String text) {
        StringBuilder sb = new StringBuilder();
        text.codePoints().forEach(cp -> {
            String ch = new String(Character.toChars(cp));
            try {
                font.encode(ch);
                sb.append(ch);
            } catch (IOException | IllegalArgumentException e) {
                sb.append('?');
            }
        });
        return sb.toString();
    }
}
