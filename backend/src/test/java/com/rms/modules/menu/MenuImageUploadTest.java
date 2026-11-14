package com.rms.modules.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rms.exception.ApiException;
import com.rms.modules.category.entity.Category;
import com.rms.modules.category.repository.CategoryRepository;
import com.rms.modules.menu.entity.MenuItem;
import com.rms.modules.menu.repository.MenuItemRepository;
import com.rms.modules.menu.service.MenuService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * REVIEW B11 — upload ảnh món:
 *   - loại file do NỘI DUNG quyết định (chữ ký đầu file), không tin Content-Type client gửi;
 *   - thay ảnh thì xoá file ảnh cũ;
 *   - không ghi được file là lỗi của server (500), không phải lỗi của người dùng (400).
 */
class MenuImageUploadTest {

    private static final long ITEM_ID = 10L;
    private static final byte[] PNG = bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R');
    private static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1);
    private static final byte[] WEBP = bytes('R', 'I', 'F', 'F', 36, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' ');

    @TempDir
    Path uploads;

    private MenuService service;
    private MenuItem item;

    @BeforeEach
    void setUp() {
        MenuItemRepository menuItems = mock(MenuItemRepository.class);
        service = new MenuService(mock(CategoryRepository.class), menuItems);
        service.setMenuDirectory(uploads.toString());

        Category food = new Category("Món chính", null);
        ReflectionTestUtils.setField(food, "id", 1L);
        item = new MenuItem(food, "Phở bò", null, new BigDecimal("65000"), null);
        ReflectionTestUtils.setField(item, "id", ITEM_ID);
        when(menuItems.findById(ITEM_ID)).thenReturn(Optional.of(item));
    }

    @Test
    void aTextFileDeclaredAsPngIsRejectedAndNothingIsStored() throws IOException {
        var fake = new MockMultipartFile(
                "file", "evil.png", "image/png", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.storeImage(ITEM_ID, fake))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getCode()).isEqualTo("UNSUPPORTED_IMAGE");
                });
        assertThat(storedFiles()).isEmpty();
        assertThat(item.getImageUrl()).isNull();
    }

    @Test
    void theExtensionFollowsTheRealContentNotTheDeclaredType() throws IOException {
        var jpegSentAsPng = new MockMultipartFile("file", "photo.png", "image/png", JPEG);

        service.storeImage(ITEM_ID, jpegSentAsPng);

        assertThat(storedFiles()).singleElement().asString().endsWith(".jpg");
        assertThat(item.getImageUrl()).endsWith(".jpg");
    }

    @Test
    void realPngAndWebpFilesAreStored() throws IOException {
        service.storeImage(ITEM_ID, new MockMultipartFile("file", "a.png", "image/png", PNG));
        assertThat(item.getImageUrl()).startsWith("/api/v1/menu-images/").endsWith(".png");

        service.storeImage(ITEM_ID, new MockMultipartFile("file", "a.webp", "image/webp", WEBP));
        assertThat(item.getImageUrl()).endsWith(".webp");
    }

    @Test
    void replacingTheImageDeletesTheOldFile() throws IOException {
        service.storeImage(ITEM_ID, new MockMultipartFile("file", "first.png", "image/png", PNG));
        String firstName = fileNameOf(item.getImageUrl());

        service.storeImage(ITEM_ID, new MockMultipartFile("file", "second.jpg", "image/jpeg", JPEG));
        String secondName = fileNameOf(item.getImageUrl());

        assertThat(storedFiles()).containsExactly(secondName);
        assertThat(firstName).isNotEqualTo(secondName);
    }

    @Test
    void failingToWriteTheFileIsAServerErrorNotABadRequest() throws IOException {
        // Thư mục upload trỏ vào một FILE thường → không tạo/ghi được.
        Path notADirectory = Files.writeString(uploads.resolve("blocked"), "x");
        service.setMenuDirectory(notADirectory.toString());

        assertThatThrownBy(() -> service.storeImage(ITEM_ID, new MockMultipartFile("file", "a.png", "image/png", PNG)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(ex.getCode()).isEqualTo("IMAGE_STORE_FAILED");
                });
        assertThat(item.getImageUrl()).isNull();
    }

    private List<String> storedFiles() throws IOException {
        try (Stream<Path> files = Files.list(uploads)) {
            return files.filter(Files::isRegularFile).map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    private static String fileNameOf(String imageUrl) {
        return imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }
}
