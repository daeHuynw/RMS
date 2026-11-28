# Hướng dẫn giao diện "nhà hàng sang trọng" cho RMS

Tài liệu này dành cho **một model AI khác (hoặc lập trình viên)** sẽ làm lại giao diện phía khách của RMS, lấy cảm hứng từ website nhà hàng fine dining <https://www.lesamis.com.sg/>.

Đọc hết tài liệu trước khi sửa file nào. Làm từng bước ở §8, mỗi bước kiểm theo §10 rồi mới sang bước sau. Mục §9 liệt kê các lỗi đã thấy trước là sẽ xảy ra; đọc kỹ.

Thông số trong tài liệu được **đo trực tiếp** trên site gốc ngày 28/11/2026 (font, cỡ chữ, màu, keyframe). Chỗ nào là đề xuất riêng cho RMS thì ghi rõ "RMS:".

---

## 1. Bối cảnh: hai trang khác bản chất

| | Les Amis | RMS |
| --- | --- | --- |
| Loại | Website giới thiệu thương hiệu | Ứng dụng vận hành nhà hàng |
| Đặt bàn | Trang chính sách + nút sang SevenRooms (bên ngoài) | Tự xử lý, có kiểm bàn trống theo khung giờ |
| Thực đơn | Link tới file PDF | Danh sách món từ API, lọc danh mục, thêm giỏ, đặt món |
| Gọi món, giỏ, đơn | Không có | Có (sau khi staff check-in) |
| Màn staff/bếp/admin | Không có | Có |

**Hệ quả:** chỉ lấy *ngôn ngữ thiết kế* (màu, chữ, bố cục, hiệu ứng). **Không bỏ hay đổi chức năng** của RMS để giống họ. Ví dụ: không thay danh sách món bằng file PDF, không chuyển đặt bàn ra site ngoài.

## 2. Phạm vi

| Nhóm trang | Làm gì |
| --- | --- |
| Trang công khai, mang tính giới thiệu: `home.html`, trang mới (Giới thiệu, Thư viện ảnh, Phòng riêng, Liên hệ) | Áp **đầy đủ** phong cách: hero ảnh/video, menu ☰ phủ màn hình, hiệu ứng |
| Trang khách thao tác: `menu.html`, `cart.html`, `reservations.html`, `my-orders.html`, `index.html` (đăng nhập), `register.html`, `profile.html` | Áp **màu + font + nút + khung**, bố cục gọn, ưu tiên điện thoại. **Không** hero video, **không** giấu thao tác chính sau ☰ |
| `staff/*`, `kitchen/*`, `admin/*` | **Không đổi bố cục.** Tối đa đổi font chữ thường. Giữ nguyên màu trạng thái (xanh/vàng/đỏ của sơ đồ bàn, badge đơn) vì chúng mang nghĩa nghiệp vụ |

## 3. Ràng buộc bắt buộc (từ `AGENTS.md`, không thương lượng)

1. HTML + CSS + JavaScript thuần, Vite. **Không** React/Vue/jQuery/Tailwind/thư viện animation.
2. Mọi lời gọi backend qua `api()` trong `src/api.js`. **Không viết `fetch` mới.**
3. Chuyển trang bằng `window.location.href`.
4. Chèn dữ liệu vào HTML luôn qua `escapeHtml` (`src/utils.js`); tiền qua `money()`, ngày giờ qua `formatDateTime()`.
5. Ẩn/hiện theo vai chỉ để gọn; backend mới là nơi chặn quyền. Không thêm logic quyền ở FE thay cho backend.
6. **Không sửa backend, API, migration** cho việc đổi giao diện. Cần API mới (form liên hệ, đặt mang về) thì dừng lại và hỏi người dùng.
7. Không commit/push trừ khi người dùng bảo.

### 3.1. ID và `data-*` mà JS đang dùng — KHÔNG được đổi tên hay xoá

Làm lại markup thì **giữ nguyên** các selector này (được thêm class, bọc thêm thẻ):

| File HTML | JS | Phải giữ |
| --- | --- | --- |
| mọi trang | `src/nav.js` | `#app-nav`, `#app-nav-cart`, `#app-nav-logout`, class `.admin-nav` (nav cũ bị ẩn) |
| `index.html` | `src/main.js` | `#login-form`, `#error`, ô `email`, `password` |
| `register.html` | `pages/auth/register.js` | `#register-form`, `#error` |
| `profile.html` | `pages/auth/profile.js` | `#profile-form`, `#password-form`, `#roles`, `#error`, `#ok` |
| `home.html` | `pages/home.js` | `#home-title`, `#home-sub`, `#home-grid` |
| `menu.html` | `pages/menu/menu.js` | `#menu-loading`, `#menu-error`, `#menu-ok`, `#order-hint`, `#menu-sub`, `#menu-filters`, `#menu-list`, `#goto-cart`; nút món có `data-cart`, `data-order`, `data-name`, `data-price`; class `chip`, `chip-active`, `menu-item` do JS tự gán |
| `cart.html` | `pages/cart/cart.js` | `#cart-items`, `#cart-sub`, `#checkout`, `#customerNote`, `#discountCode`, `#place-order`, `#last-order`, `#hint`, `#error`, `#ok`; nút `data-inc`, `data-dec`, `data-rm` |
| `reservations.html` | `pages/reservations/reservations.js` | `#search-form`, `#availability`, `#mine-list`, `#loading`, `#error`, `#ok`; nút `data-book`, `data-cancel` |
| `my-orders.html` | `pages/orders/my-orders.js` | `#order-list`, `#loading`, `#error` |

Trước khi sửa một trang, chạy lệnh dưới đây để xem selector thật, vì bảng trên có thể cũ:

```bash
grep -oE "(querySelector(All)?|getElementById)\(['\"\`][^'\"\`]+" frontend/src/pages/<nhóm>/<file>.js
```

## 4. Bản quyền và thương hiệu — đọc trước khi lấy bất cứ thứ gì từ site gốc

- **Không tải về, không hotlink, không chép:** logo "LA", ảnh, video `home.mp4`, chữ mô tả, tên món, nội dung chính sách của Les Amis.
- **Không dùng huy hiệu Michelin / Forbes / Wine Spectator** hay bất kỳ giải thưởng nào RMS không có. Đó là nhãn hiệu của bên thứ ba và là quảng cáo sai sự thật.
- Tên hiển thị là **RMS**, hoặc tên nhà hàng do người dùng cung cấp. Không dùng chữ "Les Amis".
- Ảnh/video: dùng ảnh của người dùng, hoặc ảnh có giấy phép cho phép dùng lại (ví dụ Unsplash/Pexels) và ghi nguồn trong `frontend/public/media/CREDITS.md`. Chưa có ảnh thì dùng **nền màu + chữ** thay ảnh, không chèn ảnh ngẫu nhiên.
- Font gốc của họ (Baskerville, Euclid Square) là font trả phí. Dùng font Google Fonts ở §5.2.
- Chỉ được lấy cảm hứng ở mức bố cục, tỉ lệ, cách phối màu, nhịp hiệu ứng. Mã CSS/JS của họ cũng không chép nguyên văn.

## 5. Design tokens

Đặt toàn bộ token trong một file mới `frontend/src/styles/theme.css`, nạp **sau** `style.css` ở các trang thuộc phạm vi §2. Không rải mã màu trực tiếp trong component.

### 5.1. Màu (đo từ site gốc)

| Token | Giá trị | Nguồn đo | Dùng cho |
| --- | --- | --- | --- |
| `--wine` | `#5D090F` | nền panel/menu `rgb(93,9,15)` | Màu thương hiệu: mảng menu ☰, panel trên ảnh, nút chính |
| `--wine-90` | `rgba(93, 9, 15, 0.9)` | panel đặt bàn | Panel đỏ trong suốt đặt trên ảnh |
| `--wine-ink` | `#7A1F28` | RMS: đề xuất | Chữ đỏ trên nền sáng (tiêu đề accordion, link). Có tương phản tốt hơn `--wine` với chữ nhỏ |
| `--gold` | `#C79D5F` | `rgb(199,157,95)` | Điểm nhấn: đường kẻ ngắn, hover, logo chữ |
| `--sand` | `#D9C6A7` | `rgb(217,198,167)` | Chữ phụ trên nền tối, viền mảnh |
| `--cream` | `#F9EFEA` | `rgb(249,239,234)` | Nền sáng ấm của các khối thông tin |
| `--ink` | `#222222` | `rgb(34,34,34)` | Chữ chính trên nền sáng |
| `--ink-2` | `#414142` | `rgb(65,65,66)` | Chữ thường (màu body của site gốc) |
| `--muted` | `#808081` | icon mạng xã hội | Chữ mờ, icon |
| `--paper` | `#FFFFFF` | | Nền trang thao tác |
| `--night` | `#0E0E10` | RMS: đề xuất | Nền dưới ảnh/video, footer |

Màu ngữ nghĩa giữ nguyên của RMS: lỗi đỏ `.error`, thành công `.ok`, màu trạng thái bàn/đơn trong `staff.css`, `orders.css`. **Không** đổi chúng sang tông đỏ rượu: lỗi phải khác màu thương hiệu.

### 5.2. Font

| Vai trò | Site gốc | RMS dùng | Lý do |
| --- | --- | --- | --- |
| Tiêu đề, link menu lớn | Baskerville (trả phí) | **Cormorant Garamond** 500/600 | Serif cổ điển, **có bộ ký tự tiếng Việt** |
| Chữ thường, nhãn, nút | Open Sans | **Open Sans** 400/600 | Giống site gốc, có tiếng Việt |

> **Không dùng Libre Baskerville.** Đã kiểm Google Fonts ngày 28/11/2026: Libre Baskerville chỉ có `latin` và `latin-ext`, **không có `vietnamese`**. Chữ "Đặt bàn", "Thực đơn" sẽ lẫn font fallback. Bodoni Moda cũng không có. Các serif có tiếng Việt đã kiểm: Cormorant Garamond, Playfair Display, EB Garamond, Lora, Noto Serif.

```html
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet"
  href="https://fonts.googleapis.com/css2?family=Cormorant+Garamond:wght@500;600&family=Open+Sans:wght@400;600&display=swap">
```

API `css2` tự gửi đủ các bộ ký tự (kể cả `vietnamese`) qua `unicode-range`. Không cần, và cũng không có tác dụng, thêm tham số `subset=`.

Cormorant rất mảnh ở cỡ nhỏ: **chỉ dùng từ 18px trở lên**. Dưới 18px dùng Open Sans.

### 5.3. Cỡ chữ (đo từ site gốc, quy ra rem với gốc 16px)

| Phần tử | Site gốc | RMS token |
| --- | --- | --- |
| Link chính trong menu ☰ | serif 20px, `letter-spacing: 2px`, in hoa, line-height 24px | `--fs-nav: 1.25rem` |
| Link phụ trong menu ☰ | Open Sans 12px, in hoa, có chấm "•" phía trước | `--fs-nav-sub: 0.75rem` |
| Nhãn nút ☰ ("MENU"/"CLOSE") | Open Sans 9px, `letter-spacing: 1px`, in hoa | `--fs-micro: 0.625rem` (RMS: 10px, 9px quá nhỏ) |
| Chữ thường | Open Sans 15px | `--fs-body: 0.9375rem` |
| Tiêu đề trang (vd "RESERVATION") | serif in hoa ~32px | `--fs-h1: clamp(1.75rem, 4vw, 2.5rem)` |
| Nhãn mục (vd "DRESS CODE") | Open Sans đậm, in hoa, ~13px, có giãn chữ | `--fs-label: 0.8125rem` |

### 5.4. File `theme.css` mẫu

```css
/* theme.css — token giao diện "fine dining". Nạp SAU style.css. */
:root {
  --wine: #5D090F;  --wine-90: rgba(93, 9, 15, 0.9);  --wine-ink: #7A1F28;
  --gold: #C79D5F;  --sand: #D9C6A7;  --cream: #F9EFEA;
  --ink: #222222;   --ink-2: #414142;  --muted: #808081;
  --paper: #FFFFFF; --night: #0E0E10;

  --font-serif: "Cormorant Garamond", "Noto Serif", Georgia, serif;
  --font-sans: "Open Sans", "Segoe UI", system-ui, sans-serif;

  --fs-micro: 0.625rem; --fs-nav-sub: 0.75rem; --fs-label: 0.8125rem;
  --fs-body: 0.9375rem; --fs-nav: 1.25rem; --fs-h1: clamp(1.75rem, 4vw, 2.5rem);

  --track-wide: 0.125em;      /* giãn chữ cho chữ in hoa (≈2px ở 16px) */
  --ease: cubic-bezier(.4, 0, .2, 1);
  --dur: 0.5s;                /* site gốc dùng 0.5s cho menu/rail, 0.3s cho hover link */
  --radius: 0;                /* site gốc gần như không bo góc; chỉ nút CTA bo tròn */
  --z-header: 50; --z-overlay: 100; --z-popup: 110;
}

body.theme-fine {
  font-family: var(--font-sans);
  font-size: var(--fs-body);
  color: var(--ink-2);
  background: var(--paper);
}
.theme-fine h1, .theme-fine h2, .theme-fine .serif {
  font-family: var(--font-serif);
  font-weight: 600;
  letter-spacing: var(--track-wide);
  line-height: 1.3;              /* ≥1.3: dấu tiếng Việt in hoa cần chỗ */
  color: var(--ink);
}
```

**Phạm vi:** mọi rule mới đều treo dưới `body.theme-fine` (hoặc class component riêng). Thêm `class="theme-fine"` vào `<body>` của các trang trong phạm vi §2. Nhờ vậy `staff/`, `kitchen/`, `admin/` không bị ảnh hưởng.

## 6. Thành phần và hiệu ứng

Mỗi mục ghi: site gốc làm gì (đã đo), RMS làm thế nào, và phần bắt buộc về khả năng truy cập.

### 6.1. Header trong suốt trên hero

- **Site gốc:** logo chữ ở góc trên trái; nút ☰ ở góc trên phải gồm 3 gạch mảnh và chữ "MENU" 9px in hoa bên dưới. Mở menu thì 3 gạch thành dấu ✕ và chữ đổi thành "CLOSE". Header không có nền, nằm đè lên ảnh/video.
- **RMS:** trên `home.html` và các trang giới thiệu, header `position: absolute` (hoặc `fixed`), nền trong suốt, chữ trắng. Trên trang thao tác, header dính (`sticky`) nền `--paper` hoặc `--night`, luôn hiện nút **Giỏ (n)** và **Đăng nhập/Đăng xuất** ngoài ☰.
- **Bắt buộc:** nút ☰ là `<button>` có `aria-expanded`, `aria-controls="site-menu"`, `aria-label="Mở menu"` (đổi thành "Đóng menu" khi mở). Vùng bấm ≥ 44×44px.

### 6.2. Menu ☰ phủ toàn màn hình

- **Site gốc (đã đo):**
  - lớp phủ `position: fixed`, `opacity: 0; visibility: hidden; transition: 0.5s ease-in-out`, bật lên thì `opacity: 1; visibility: visible`;
  - nửa trái vẫn thấy ảnh nền; **nửa phải là mảng đỏ rượu `#5D090F`, mép trái cắt chéo** (một khối xoay khoảng 15°, `transform: rotate(15deg)`, tràn khỏi khung và bị cắt);
  - link chính serif 20px in hoa, giãn 2px, chia 2 cột ("Reservation", "La Boutique" ở trên; "Home / About / Menu" cột trái, "Gallery / Les Salons / Contact" cột phải);
  - dưới mỗi link chính là link phụ Open Sans 12px in hoa, có chấm "•";
  - các mục hiện dần lần lượt (stagger);
  - chân menu có copyright và link điều khoản; góc trái dưới có địa chỉ kèm icon ghim bản đồ.
- **RMS:** dùng `clip-path` thay khối xoay (gọn hơn, không phải tính tràn):

```css
.site-menu { position: fixed; inset: 0; z-index: var(--z-overlay);
  opacity: 0; visibility: hidden; transition: opacity var(--dur) var(--ease), visibility var(--dur); }
.site-menu.is-open { opacity: 1; visibility: visible; }
.site-menu__panel { position: absolute; inset: 0; background: var(--wine);
  clip-path: polygon(38% 0, 100% 0, 100% 100%, 22% 100%); }
.site-menu__links { position: relative; margin-left: 48%; padding: 15vh 6vw;
  display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 28px 40px; }
.site-menu__main { font: 600 var(--fs-nav)/1.3 var(--font-serif); letter-spacing: var(--track-wide);
  text-transform: uppercase; color: #fff; text-decoration: none; transition: color .3s var(--ease); }
.site-menu__main:hover, .site-menu__main:focus-visible { color: var(--gold); }
.site-menu__sub { font: 400 var(--fs-nav-sub)/1.66 var(--font-sans); text-transform: uppercase; color: var(--sand); }
.site-menu__sub::before { content: "• "; }
.site-menu.is-open .site-menu__links > * { animation: fade-in-up .6s var(--ease) both; }
.site-menu.is-open .site-menu__links > *:nth-child(2) { animation-delay: .06s; }
/* … mỗi mục +0.06s; tối đa ~8 mục */
@media (max-width: 720px) {
  .site-menu__panel { clip-path: none; }                  /* điện thoại: phủ đỏ toàn màn hình */
  .site-menu__links { margin-left: 0; grid-template-columns: 1fr; padding: 96px 24px 40px; }
}
```

- **Danh sách link vẫn lấy từ `LINKS` trong `src/nav.js`**, đã lọc theo vai (`visibleLinks(me)`). Không viết danh sách cứng thứ hai: hai nơi khai báo link sẽ lệch nhau. Có thể nhóm link phụ dưới link chính bằng cách thêm trường `group` vào từng phần tử của `LINKS`.
- **Bắt buộc:**
  - Mở menu thì khoá cuộn trang (`document.body.style.overflow = 'hidden'`), đóng thì trả lại.
  - Phím `Esc` đóng menu. Bấm link trong menu cũng đóng menu.
  - Mở menu thì focus vào link đầu tiên; đóng thì trả focus về nút ☰. Giữ Tab lòng vòng trong menu (focus trap).
  - Lớp phủ ẩn bằng `visibility: hidden` (hoặc `inert`) để link bên trong không nhận Tab khi menu đóng.

### 6.3. Hero ảnh/video toàn màn hình

- **Site gốc (đã đo):** `<video autoplay muted loop playsinline>` với `object-fit: cover`, đặt `position: absolute` phủ khung. Có nút bật/tắt tiếng và nút toàn màn hình ở góc trái dưới, mũi tên ⌄ nhấp nháy ở giữa đáy để gợi ý cuộn, địa chỉ ở góc trái dưới. **Không có `poster`**, nên lúc video chưa tải thì màn hình đen: RMS phải làm tốt hơn chỗ này.
- **RMS:**

```html
<section class="hero">
  <video class="hero__media" autoplay muted loop playsinline preload="metadata"
         poster="/media/hero-poster.jpg" aria-hidden="true">
    <source src="/media/hero.mp4" type="video/mp4">
  </video>
  <div class="hero__shade"></div>
  <div class="hero__content">
    <p class="hero__eyebrow">Nhà hàng</p>
    <h1 class="hero__title">RMS</h1>
    <div class="hero__actions">
      <a class="btn-wine" href="/reservations.html">Đặt bàn</a>
      <a class="btn-ghost" href="/menu.html">Xem thực đơn</a>
    </div>
  </div>
  <a class="hero__scroll" href="#intro" aria-label="Cuộn xuống">⌄</a>
</section>
```

```css
.hero { position: relative; min-height: 100vh; min-height: 100svh; display: grid; place-items: end start;
  color: #fff; background: var(--night); overflow: hidden; }
.hero__media { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.hero__shade { position: absolute; inset: 0;
  background: linear-gradient(180deg, rgba(0,0,0,.45) 0%, rgba(0,0,0,.1) 40%, rgba(0,0,0,.65) 100%); }
.hero__content { position: relative; padding: 0 6vw 14vh; max-width: 40rem; }
.hero__scroll { position: absolute; left: 50%; bottom: 24px; transform: translateX(-50%);
  color: #fff; animation: nudge 2s ease-in-out infinite; }
@media (prefers-reduced-motion: reduce) {
  .hero__media { display: none; }             /* chỉ hiện poster qua nền */
  .hero { background: var(--night) url(/media/hero-poster.jpg) center / cover; }
  .hero__scroll { animation: none; }
}
```

- **Bắt buộc:**
  - Phải có đủ `muted` + `playsinline`, nếu không iOS/Chrome chặn tự phát.
  - Phải có `poster`. Video dưới 4 MB (1080p, H.264, ~20 giây, không tiếng). Nếu chưa có video, dùng ảnh tĩnh: hero vẫn đẹp mà không cần video.
  - Lớp `hero__shade` bắt buộc để chữ trắng đủ tương phản trên mọi khung hình.
  - Video chỉ trang trí nên đặt `aria-hidden="true"`. Nếu có nút bật tiếng thì dùng `<button>`, kèm `aria-pressed`.

### 6.4. Rail biểu tượng bên trái (tuỳ chọn)

- **Site gốc:** cột icon cố định giữa mép trái (lịch đặt bàn, Instagram, thư), mỗi ô 45×45px, `position: fixed; left: 35px`. Icon `fill: #808081`, hiện dần với `transition: 0.5s ease-in-out`.
- **RMS:** chỉ dùng trên trang giới thiệu, với lối tắt *Đặt bàn* và *Thực đơn*. **Ẩn ở dưới 900px** vì trên điện thoại nó che nội dung. Icon vẽ bằng SVG inline, có `aria-label`.

### 6.5. Panel đỏ trong suốt trên ảnh (trang đặt bàn)

- **Site gốc:** ảnh phòng ăn phủ toàn trang; trên đó là một khối `rgba(93,9,15,0.9)` rộng khoảng 90%, chữ trắng.
  - Tiêu đề serif in hoa ("RESERVATION").
  - Các nhóm "DRESS CODE", "CANCELLATION POLICY"… là nhãn Open Sans đậm, in hoa, màu `--sand`, kèm danh sách gạch đầu dòng.
- **RMS `reservations.html`:** đặt **khối chính sách** (trang phục, huỷ bàn, trẻ em, giờ giữ bàn) bằng panel này ở đầu trang. Bên dưới là **form tìm bàn hiện có**, nằm trên nền `--cream` cho dễ đọc. Không đặt form vào nền đỏ: chữ nhỏ và ô nhập trên nền đỏ khó đọc.
- Nội dung chính sách phải là **chính sách thật của nhà hàng do người dùng cung cấp**. Chưa có thì để khối giữ chỗ ghi rõ "Nội dung chính sách – cần bổ sung", không bịa phí phạt.
- Luật thật do backend quyết định: thời lượng đặt bàn tối đa lấy từ cài đặt (mặc định 180 phút), NO_SHOW sau 30 phút. Chữ trên trang phải khớp các luật này.

### 6.6. Accordion danh mục (từ trang La Boutique)

- **Site gốc:** mỗi danh mục là một dòng chữ in hoa màu đỏ rượu, dấu "+" ở bên phải, đường kẻ dưới mảnh; bấm vào thì mở danh sách.
- **RMS `menu.html`:** có thể trình bày mỗi danh mục thực đơn như một accordion. Dùng `<details><summary>` (sẵn bàn phím và trình đọc màn hình). **Nhưng:**
  - `#menu-filters` và `#menu-list` vẫn phải tồn tại, vì `menu.js` vẽ vào hai phần tử đó.
  - Cách an toàn nhất là để `menu.js` tự vẽ accordion, tức là sửa hàm `renderList()`/`renderFilters()`. Đừng dựng HTML tĩnh rồi bắt JS tìm phần tử mới.
  - Giữ nguyên nút `data-cart` / `data-order` của từng món.

```css
.theme-fine details.menu-cat { border-bottom: 1px solid var(--sand); }
.theme-fine details.menu-cat > summary { list-style: none; cursor: pointer; display: flex; justify-content: space-between;
  padding: 18px 0; font: 600 1.25rem/1.3 var(--font-serif); letter-spacing: var(--track-wide);
  text-transform: uppercase; color: var(--wine-ink); }
.theme-fine details.menu-cat > summary::-webkit-details-marker { display: none; }
.theme-fine details.menu-cat > summary::after { content: "+"; font-family: var(--font-sans); transition: transform .3s var(--ease); }
.theme-fine details.menu-cat[open] > summary::after { transform: rotate(45deg); }
```

### 6.7. Nút

- **Site gốc:** nút "ORDER NOW" trong popup là nút bo tròn (pill), nền đỏ rượu đậm, chữ trắng in hoa nhỏ, giãn chữ.
- **RMS:**

```css
.btn-wine { display: inline-flex; align-items: center; justify-content: center; gap: 8px;
  min-height: 44px; padding: 0 24px; border-radius: 999px; border: 1px solid var(--wine);
  background: var(--wine); color: #fff; font: 600 var(--fs-label)/1 var(--font-sans);
  letter-spacing: var(--track-wide); text-transform: uppercase; text-decoration: none; cursor: pointer;
  transition: background .3s var(--ease), color .3s var(--ease), border-color .3s var(--ease); }
.btn-wine:hover { background: var(--gold); border-color: var(--gold); color: var(--night); }
.btn-ghost { /* như trên nhưng nền trong suốt, viền + chữ trắng (trên ảnh) hoặc --wine (trên nền sáng) */ }
.btn-wine:focus-visible, .btn-ghost:focus-visible { outline: 2px solid var(--gold); outline-offset: 3px; }
```

> **Cẩn thận:** `style.css` hiện có rule toàn cục `button { width: 100%; margin-top: 24px; … }`. Nút mới phải tự đặt `width: auto; margin-top: 0`, hoặc dùng `<a>`. **Không sửa rule toàn cục đó**, vì mọi form của staff/admin đang dựa vào nó.

### 6.8. Hiệu ứng xuất hiện (keyframe đo từ site gốc)

| Tên gốc | Nội dung | Thời lượng |
| --- | --- | --- |
| `fadeInTypeOne` | `opacity 0 → 1` | 1.5s ease-in-out |
| `slideDownOpacity` | rơi từ `translateY(-100%)`, nảy +8% / −4% / +4% / −2% rồi về 0, đồng thời hiện dần | 1s ease |
| `slideUpNormal` | `translateY(100%) → 0` | 1s |
| `slideRightHome` | trượt vào từ phải, đứng yên, rồi trượt ra (dùng cho chữ chạy trên hero) | 1s |

RMS chỉ cần hai hiệu ứng: hiện dần, và trượt lên nhẹ. Không dùng kiểu nảy (`slideDownOpacity`) cho nội dung thao tác.

```css
@keyframes fade-in-up { from { opacity: 0; transform: translateY(16px); } to { opacity: 1; transform: none; } }
@keyframes nudge { 0%, 100% { transform: translate(-50%, 0); } 50% { transform: translate(-50%, 6px); } }

/* Hiện khi cuộn tới: MẶC ĐỊNH NỘI DUNG VẪN HIỆN. JS chỉ thêm hiệu ứng nếu chạy được. */
.js-reveal .reveal { opacity: 0; transform: translateY(16px); transition: opacity .8s var(--ease), transform .8s var(--ease); }
.js-reveal .reveal.is-in { opacity: 1; transform: none; }
@media (prefers-reduced-motion: reduce) {
  .js-reveal .reveal { opacity: 1; transform: none; transition: none; }
  * { animation: none !important; }
}
```

```js
// src/reveal.js — gọi một lần ở trang giới thiệu
export function setupReveal() {
  if (!('IntersectionObserver' in window)) return            // không hỗ trợ → nội dung vẫn hiện
  if (matchMedia('(prefers-reduced-motion: reduce)').matches) return
  document.documentElement.classList.add('js-reveal')        // chỉ ẩn khi chắc chắn sẽ hiện lại
  const io = new IntersectionObserver((entries) => {
    for (const e of entries) if (e.isIntersecting) { e.target.classList.add('is-in'); io.unobserve(e.target) }
  }, { rootMargin: '0px 0px -10% 0px' })
  document.querySelectorAll('.reveal').forEach((el) => io.observe(el))
}
```

### 6.9. Popup quảng cáo (tuỳ chọn, không khuyến khích)

- **Site gốc:** khi vào trang chủ hiện ngay một popup (bánh Noel, giá, nút "ORDER NOW"), nút ✕ ở góc, nền tối phía sau.
- **RMS:** chỉ làm nếu người dùng yêu cầu, và chỉ trên `home.html`; mỗi phiên trình duyệt hiện một lần (ghi nhớ bằng `sessionStorage`, bọc `try/catch`).
  - Dùng `<dialog>`: có sẵn phím `Esc` và lớp nền.
  - **Không bao giờ** hiện trên `menu.html`, `cart.html`, `reservations.html`, vì popup chặn khách đang gọi món.

### 6.10. Chân trang

- **Site gốc:** địa chỉ kèm icon ghim bản đồ, link điều khoản và bảo mật, copyright, form đăng ký nhận tin (tên + email).
- **RMS:** địa chỉ, giờ mở cửa, số điện thoại **hiển thị dạng chữ có thể chọn được**, kèm link `tel:` cho tiện.
  - **Không làm form "đăng ký nhận tin"** khi chưa có API: form gửi mà không đi đâu là lỗi.

## 7. Áp dụng vào từng trang RMS

| Trang | Thay đổi | Ghi chú |
| --- | --- | --- |
| `home.html` | Hero (§6.3), header trong suốt + ☰ (§6.1–6.2), khối giới thiệu ngắn, 2 CTA "Đặt bàn" / "Xem thực đơn". **Giữ `#home-grid`** (thẻ lối vào theo vai) ở khối thứ hai, cho staff/kitchen/admin vào việc nhanh | `home.js` vẫn chạy như cũ |
| `menu.html` | Tiêu đề serif, danh mục thành accordion hoặc chip (§6.6), thẻ món: ảnh vuông, tên serif, giá Open Sans, nút `.btn-wine` nhỏ | Sửa trong `menu.js` (`renderList`, `itemActions`), giữ `data-*` |
| `reservations.html` | Panel chính sách (§6.5) trên cùng, form tìm bàn trên nền `--cream` | Giữ `#search-form`, `#availability`, `#mine-list` |
| `cart.html`, `my-orders.html` | Chỉ đổi token, nút, khung; bố cục một cột | Không hiệu ứng nảy, không video |
| `index.html`, `register.html`, `profile.html` | Có thể đặt form trên nửa phải, ảnh nhà hàng nửa trái (từ 900px trở lên); điện thoại thì chỉ form | Giữ id form |
| Trang mới: `about.html`, `gallery.html`, `private-rooms.html`, `contact.html` | Nội dung tĩnh, hero ảnh, `.reveal` | **Phải thêm vào `vite.config.js` → `build.rollupOptions.input`**, nếu không `npm run build` bỏ sót trang |
| `staff/*`, `kitchen/*`, `admin/*` | **Không đổi** | Nếu đổi font thì kiểm lại sơ đồ bàn, màn bếp, biểu đồ dashboard |

Ảnh/video đặt trong `frontend/public/media/` (Vite phục vụ tại `/media/...`). Không đặt trong `src/`.

## 8. Thứ tự làm (mỗi bước nhỏ, kiểm xong mới sang bước sau)

1. Tạo `src/styles/theme.css` (token §5.4) + link font. Thêm `class="theme-fine"` vào `<body>` của **một** trang (`home.html`). Kiểm §10.
2. Header + menu ☰ (§6.1–6.2): sửa `nav.js` để có thêm kiểu hiển thị overlay, **giữ nguyên** `LINKS`, `visibleLinks`, nút Giỏ, Đăng xuất và các id ở §3.1.
3. Hero trang chủ (§6.3), ban đầu dùng ảnh tĩnh. Video chỉ thêm khi người dùng có file.
4. `menu.html` (§6.6), sửa qua `menu.js`.
5. `reservations.html` (§6.5).
6. Áp token cho `cart.html`, `my-orders.html`, đăng nhập, đăng ký, hồ sơ.
7. Trang tĩnh mới (§7): chỉ làm khi người dùng đã đưa nội dung và ảnh.

Sau mỗi bước, theo `AGENTS.md`: cập nhật `checklist.md` và `flow.md` (không commit hai file này).

## 9. Lỗi model khác hay mắc — kiểm từng dòng

### Phá chức năng

1. **Đổi hoặc xoá `id` / `data-*`** khi "làm sạch markup", khiến JS tìm không thấy phần tử và trang trắng hoặc nút không phản hồi. Xem §3.1.
2. **Viết lại trang bằng React/Vue/thư viện**, hoặc thêm `fetch()` trực tiếp thay vì `api()`. Làm vậy là mất tự refresh token, mất `Idempotency-Key`, mất dạng lỗi `ApiError`.
3. **Sửa rule toàn cục trong `style.css`** (`button`, `label`, `input`, `.page`, `.card`, `h1`) làm vỡ toàn bộ màn staff/admin. Thêm rule dưới `.theme-fine` thay vì sửa rule chung.
4. **Thay danh sách món bằng PDF hoặc ảnh tĩnh** "cho giống site gốc", khiến khách không gọi món được nữa.
5. **Giấu nút Giỏ / Đặt món / Đăng xuất vào sau ☰** trên trang thao tác. Khách phải bấm thêm hai lần, số món trong giỏ không còn thấy.
6. **Viết danh sách link thứ hai** cho menu ☰ thay vì dùng `LINKS` trong `nav.js`. Hai danh sách sẽ lệch nhau, link admin lộ ra cho khách thấy.
7. **Làm mất cập nhật số món trong giỏ**: `nav.js` dùng `onCartChange` để sửa chữ "Giỏ (n)". Vẽ lại nav mà không đăng ký lại sự kiện này thì số không đổi.
8. **Quên thêm trang mới vào `vite.config.js`**: chạy dev thì vẫn thấy, nhưng `npm run build` không xuất trang đó.
9. **Bịa API**: form liên hệ, đăng ký nhận tin, đặt mang về gọi tới endpoint không tồn tại. Chưa có API thì không làm form gửi dữ liệu; hỏi người dùng.

### Bản quyền và nội dung

10. **Chép logo, ảnh, video, chữ của Les Amis**, hoặc hotlink ảnh từ `lesamis.com.sg`.
11. **Gắn huy hiệu Michelin / Forbes / giải thưởng** mà nhà hàng không có.
12. **Bịa chính sách** (phí huỷ, phí mở rượu, tuổi trẻ em) hoặc giá món. Dùng nội dung người dùng đưa; chưa có thì để chỗ giữ chỗ ghi rõ "cần bổ sung".

### Tiếng Việt và chữ

13. **Dùng Libre Baskerville hoặc Bodoni Moda**: không có tiếng Việt, chữ có dấu bị lẫn font. Dùng Cormorant Garamond (§5.2).
14. **`line-height` quá thấp với chữ in hoa có dấu** ("ĐẶT BÀN", "THỰC ĐƠN"), làm dấu chạm hoặc bị cắt ở dòng trên. Giữ `line-height ≥ 1.3` và không đặt `overflow: hidden` sát chữ.
15. **In hoa cả câu dài tiếng Việt** thì rất khó đọc. Chỉ in hoa nhãn ngắn (≤ 4 từ).
16. **Serif mảnh ở cỡ nhỏ**: Cormorant dưới 18px gần như không đọc được trên điện thoại. Chữ nhỏ dùng Open Sans.

### Hiệu ứng và hiệu năng

17. **Nội dung để `opacity: 0` chờ JS bật lên**: JS lỗi hoặc chạy chậm thì trang trống. Chỉ ẩn khi đã thêm class `js-reveal` (§6.8).
18. **Bỏ qua `prefers-reduced-motion`**: người bật giảm chuyển động vẫn thấy video và chữ nảy.
19. **Video thiếu `muted`/`playsinline`/`poster`**, hoặc file nặng hàng chục MB. Hậu quả là màn hình đen, tốn dữ liệu di động, iOS không tự phát.
20. **Hero `height: 100vh` trên điện thoại** bị thanh địa chỉ che đáy. Dùng `min-height: 100svh`, kèm `100vh` làm fallback như §6.3.
21. **Animation chạy mãi** trên nhiều phần tử, hoặc dùng `transition: all`. Chỉ animate `opacity` và `transform`.

### Truy cập và tương tác

22. **Menu ☰ không khoá cuộn, không đóng bằng `Esc`, không trả focus**, link ẩn vẫn nhận phím Tab. Xem phần "Bắt buộc" ở §6.2.
23. **Chữ trắng trên ảnh sáng không có lớp phủ tối**, độ tương phản không đạt. Luôn có `hero__shade`.
24. **Chỉ có hiệu ứng hover**: điện thoại không có hover, nên thông tin chỉ hiện khi rê chuột sẽ mất hẳn.
25. **Nút nhỏ hơn 44px** hoặc nhãn chỉ là icon không có `aria-label`.
26. **`z-index`**: `.app-nav` đang `position: sticky; z-index: 20` và có `backdrop-filter` (tạo stacking context riêng). Lớp phủ menu phải đặt ngoài `.app-nav` và có `z-index` ≥ 100. Đừng nhét overlay vào bên trong nav.
27. **Rail icon cố định che nội dung trên điện thoại**: ẩn rail dưới 900px.

### Phạm vi

28. **Đổi màu trạng thái** của sơ đồ bàn, badge đơn, màn bếp sang tông đỏ/vàng thương hiệu. Các màu đó mang nghĩa nghiệp vụ; staff sẽ đọc nhầm.
29. **Sửa backend, migration** để "phục vụ giao diện". Việc này ngoài phạm vi: dừng lại và hỏi người dùng.
30. **Làm cả loạt trang trong một lần**. Theo `AGENTS.md`, mỗi lần một bước nhỏ, kiểm xong mới làm tiếp.

## 10. Kiểm tra sau mỗi bước

```bash
cd frontend && npm run build          # phải build không lỗi, đủ mọi trang trong vite.config.js
```

Chạy backend + `npm run dev`, rồi kiểm bằng tay:

- [ ] Khổ 375px (điện thoại) và 1280px: không thanh cuộn ngang, chữ không tràn.
- [ ] Đăng nhập CUSTOMER: thêm giỏ → giỏ hiện số → đặt món chạy (cần lượt đặt đã check-in).
- [ ] Đăng nhập STAFF và ADMIN: `staff/tables.html`, `staff/orders.html`, `kitchen/`, `admin/dashboard.html` **trông y như trước**.
- [ ] Menu ☰: mở/đóng bằng chuột, bằng `Esc`; Tab không thoát ra ngoài khi đang mở; link chỉ hiện đúng theo vai.
- [ ] Bật "giảm chuyển động" của hệ điều hành: không video, không hiệu ứng trượt, nội dung vẫn hiện đủ.
- [ ] Tắt JS: trang giới thiệu vẫn đọc được (không bị ẩn bởi `opacity: 0`).
- [ ] Chữ tiếng Việt có dấu ở tiêu đề serif hiển thị đúng font (mở DevTools → Rendered Fonts).
- [ ] Console không có lỗi.

## 11. Phụ lục: số đo gốc

| Thuộc tính | Giá trị đo trên lesamis.com.sg |
| --- | --- |
| Font | Open Sans (chữ thường, ~600 phần tử), Baskerville-Regular (tiêu đề, ~130), EuclidSquare (một số nhãn) |
| Màu chữ chính | `rgb(65,65,66)`; tiêu đề `rgb(34,34,34)` |
| Màu thương hiệu | `rgb(93,9,15)`; dạng trong suốt `rgba(93,9,15,0.9)` |
| Màu phụ | vàng đồng `rgb(199,157,95)`, cát `rgb(217,198,167)`, kem `rgb(249,239,234)` |
| Video hero | `autoplay muted loop playsinline`, `object-fit: cover`, không poster |
| Lớp phủ menu | `position: fixed; opacity: 0; visibility: hidden; transition: 0.5s ease-in-out` |
| Link menu chính | serif 20px / 24px, `letter-spacing: 2px`, uppercase, `transition: 0.3s ease-in-out` |
| Link menu phụ | Open Sans 12px / ~20px, uppercase |
| Nhãn nút ☰ | Open Sans 9px, `letter-spacing: 1px`, uppercase, trắng |
| Rail icon | `position: fixed; top: 50%; left: 35px`, ô 45×45px, icon `fill: rgb(128,128,129)`, `transition: 0.5s ease-in-out` |
| Khối nền nghiêng | `transform: rotate(15deg)` (ma trận `0.9659, 0.2588`) |
| Keyframe | `fadeInTypeOne` 1.5s; `slideDownOpacity` 1s (nảy); `slideUpNormal`; `slideRightHome` 1s |
