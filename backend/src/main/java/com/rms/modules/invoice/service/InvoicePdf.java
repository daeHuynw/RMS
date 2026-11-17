package com.rms.modules.invoice.service;

import com.rms.modules.invoice.entity.Invoice;
import com.rms.modules.order.entity.Order;
import com.rms.modules.order.entity.OrderItem;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

public final class InvoicePdf {

    private static final String FONT = "/fonts/DejaVuSans.ttf";

    private InvoicePdf() {
    }

    public static byte[] write(Invoice invoice, Order order) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDFont font = font(document);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                float y = 780;
                y = line(stream, font, 16, 50, y, "Hoá đơn " + invoice.getInvoiceNumber());
                String table = order.getTableSession() == null || order.getTableSession().getTable() == null
                        ? ""
                        : " · bàn " + order.getTableSession().getTable().getTableCode();
                y = line(stream, font, 11, 50, y - 8, "Đơn " + order.getOrderNumber() + table);
                y -= 10;
                for (OrderItem item : order.getItems()) {
                    y = line(
                            stream,
                            font,
                            11,
                            50,
                            y,
                            item.getQuantity() + " x " + item.getItemName()
                                    + "    " + item.getUnitPrice());
                }
                y -= 8;
                y = line(stream, font, 11, 50, y, "Tạm tính: " + invoice.getSubtotal());
                y = line(stream, font, 11, 50, y, "Giảm giá: " + invoice.getDiscountAmount());
                y = line(stream, font, 11, 50, y, "Thuế: " + invoice.getTaxAmount());
                line(stream, font, 13, 50, y, "Tổng: " + invoice.getTotalAmount());
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

    /**
     * Font đóng gói trong ứng dụng (resources/fonts, giấy phép Bitstream Vera / DejaVu cho phép phân phối lại),
     * nên PDF giống nhau trên mọi máy. Trước đây font lấy từ /usr/share/fonts và tên món bị thay dấu bằng "?".
     */
    private static PDFont font(PDDocument document) throws IOException {
        try (InputStream in = InvoicePdf.class.getResourceAsStream(FONT)) {
            if (in == null) {
                throw new IOException("Missing bundled font " + FONT);
            }
            return PDType0Font.load(document, in);
        }
    }
}
