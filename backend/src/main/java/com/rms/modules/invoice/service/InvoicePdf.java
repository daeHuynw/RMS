package com.rms.modules.invoice.service;

import com.rms.modules.invoice.entity.Invoice;
import com.rms.modules.order.entity.Order;
import com.rms.modules.order.entity.OrderItem;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

public final class InvoicePdf {

    private InvoicePdf() {
    }

    public static byte[] write(Invoice invoice, Order order) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDFont font = font(document);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                float y = 780;
                y = line(stream, font, 16, 50, y, "Hoa don " + invoice.getInvoiceNumber());
                String table = order.getTableSession() == null || order.getTableSession().getTable() == null
                        ? ""
                        : " · ban " + order.getTableSession().getTable().getTableCode();
                y = line(stream, font, 11, 50, y - 8, "Don " + order.getOrderNumber() + table);
                y -= 10;
                for (OrderItem item : order.getItems()) {
                    y = line(
                            stream,
                            font,
                            11,
                            50,
                            y,
                            item.getQuantity() + " x " + asciiSafe(item.getItemName())
                                    + "    " + item.getUnitPrice());
                }
                y -= 8;
                y = line(stream, font, 11, 50, y, "Tam tinh: " + invoice.getSubtotal());
                y = line(stream, font, 11, 50, y, "Giam gia: " + invoice.getDiscountAmount());
                y = line(stream, font, 11, 50, y, "Thue: " + invoice.getTaxAmount());
                line(stream, font, 13, 50, y, "Tong: " + invoice.getTotalAmount());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not render invoice PDF", ex);
        }
    }

    private static float line(PDPageContentStream stream, PDFont font, float size, float x, float y, String text)
            throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(text == null ? "" : text);
        stream.endText();
        return y - (size + 6);
    }

    private static PDFont font(PDDocument document) throws IOException {
        for (String candidate : List.of(
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf")) {
            if (Files.isRegularFile(Path.of(candidate))) {
                return PDType0Font.load(document, Path.of(candidate).toFile());
            }
        }
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    /** Avoid WinAnsi encoding issues if fallback Helvetica is used. */
    private static String asciiSafe(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^\\x20-\\x7E]", "?");
    }
}
