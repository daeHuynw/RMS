package com.rms.modules.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.rms.modules.invoice.entity.Invoice;
import com.rms.modules.invoice.service.InvoicePdf;
import com.rms.modules.menu.entity.MenuItem;
import com.rms.modules.order.entity.Order;
import com.rms.modules.order.entity.OrderItem;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * REVIEW C4 — hoá đơn PDF phải giữ nguyên tiếng Việt (tên món, nhãn), và không phụ thuộc
 * vào việc máy chạy server có cài font nào: font được đóng gói trong ứng dụng.
 */
class InvoicePdfTest {

    @Test
    void vietnameseItemNamesSurviveInTheInvoice() throws IOException {
        String text = textOf(InvoicePdf.write(invoice(), order("Gỏi cuốn tôm thịt")));

        assertThat(text).contains("Gỏi cuốn tôm thịt");
        assertThat(text).doesNotContain("?");
    }

    @Test
    void labelsAreWrittenInVietnamese() throws IOException {
        String text = textOf(InvoicePdf.write(invoice(), order("Phở bò")));

        assertThat(text).contains("Hoá đơn INV-TEST", "Tạm tính", "Giảm giá", "Thuế", "Tổng");
    }

    @Test
    void theFontShipsWithTheApplication() throws IOException {
        try (InputStream font = InvoicePdf.class.getResourceAsStream("/fonts/DejaVuSans.ttf")) {
            assertThat(font).as("font phải nằm trong resources, không lấy từ /usr/share/fonts").isNotNull();
        }
    }

    private static Invoice invoice() {
        return new Invoice(
                1L, "INV-TEST", new BigDecimal("45000.00"), BigDecimal.ZERO,
                new BigDecimal("3600.00"), new BigDecimal("48600.00"));
    }

    private static Order order(String itemName) {
        MenuItem menuItem = Mockito.mock(MenuItem.class);
        Mockito.when(menuItem.getName()).thenReturn(itemName);
        Mockito.when(menuItem.getPrice()).thenReturn(new BigDecimal("45000.00"));
        Order order = new Order("ORD-TEST", null, null, null, "key-1", null);
        order.addItem(new OrderItem(menuItem, 1, null));
        return order;
    }

    private static String textOf(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }
}
