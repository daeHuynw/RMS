# RMS — Kết quả review & test chạy thật (2026-11-12)

Tài liệu này ghi lại các lỗi và điểm cần cải thiện tìm được khi đọc toàn bộ code (commit `f6aec48`) và chạy thật app trên máy local (BE `:8080`, FE `:5173`, MySQL `rms_db`). Dùng làm danh sách việc cho lần sửa tiếp theo.

Ký hiệu độ chắc chắn:

- **[Đã tái hiện]** — đã chạy thật và thấy lỗi.
- **[Đọc code]** — thấy trong code, chưa chạy kiểm chứng.
- **[Giả thuyết]** — nguyên nhân mình suy đoán, cần xác nhận khi sửa.

## Cách chạy và kiểm tra lại

```bash
cd backend && mvn spring-boot:run      # http://127.0.0.1:8080
cd frontend && npm run dev             # http://127.0.0.1:5173
cd backend && mvn test                 # 5 test MockMvc hiện có
```

Tài khoản test có sẵn trong DB local: `admin@rms.local` (ADMIN), `staff1@example.com` (STAFF), `kitchen@example.com` (KITCHEN). Mật khẩu hỏi chủ dự án; không ghi vào file này.

Ràng buộc khi sửa:

- Không sửa migration đã chạy (`V1`–`V12`). Thay đổi schema đi vào `V13__...sql` trở đi.
- `ddl-auto: validate` — entity phải khớp schema.
- Lỗi nghiệp vụ dùng `ApiException` → `ApiError`.
- FE gọi API qua `api()` trong `frontend/src/api.js`, không `fetch` mới.

---

## A. Hỏng khi chạy thật — sửa trước

### A1. Realtime màn bếp không hoạt động [Đã tái hiện] — ✅ ĐÃ SỬA 2026-11-12

- **Đã sửa:**
  - `KitchenWebSocketConfig`: lấy accessor gốc bằng `MessageHeaderAccessor.getAccessor(...)` thay cho `StompHeaderAccessor.wrap(...)`. Giả thuyết bên dưới đã được xác nhận; cơ chế chính xác: Spring gắn "user change callback" vào accessor gốc của frame `CONNECT` để nhớ user theo phiên, `wrap()` tạo accessor mới không có callback đó.
  - `kitchen.js`: bỏ `connectHeaders` cố định, dùng `beforeConnect` gọi `api('/auth/me')` (tự refresh) rồi đọc token mới trước mỗi lần (re)connect.
  - Test mới `KitchenWebSocketConfigTest` (6 ca; ca "CONNECT lưu user vào phiên" đỏ trước khi sửa, xanh sau khi sửa).
- **Đã kiểm chạy thật:** KITCHEN thấy "Realtime: đã kết nối", `CONNECTED` có `user-name`; staff tạo đơn → ticket hiện không cần tải lại; không token / token sai / CUSTOMER đều bị từ chối (`ERROR`, đóng 1002); giả lập access token hết hạn + restart backend → trang tự refresh token và kết nối lại.
- **Chưa làm (cố ý, ngoài phạm vi A1):** các sự kiện còn thiếu — đổi trạng thái đơn và sửa/xoá món đã thêm ở B6, huỷ đơn đã thêm ở B7; `setAllowedOriginPatterns("*")` → chờ quyết định danh sách origin.
- **Triệu chứng:** `kitchen/index.html` luôn hiện "Realtime mất kết nối — đang thử lại…". Tạo đơn mới thì trang bếp không tự cập nhật (API trả 2 ticket, trang vẫn hiện 1) cho tới khi tải lại.
- **Chi tiết:** STOMP `CONNECT` thành công (nhận `CONNECTED`), nhưng `SUBSCRIBE /topic/kitchen/orders` nhận frame `ERROR` "Failed to send message to ExecutorSubscribableChannel[clientInboundChannel]" rồi socket đóng (code 1002). Frame `CONNECTED` không có header `user-name`.
- **Vị trí:** `modules/kitchen/config/KitchenWebSocketConfig.java`, `configureClientInboundChannel`.
- **[Giả thuyết]:** interceptor dùng `StompHeaderAccessor.wrap(message)` rồi `setUser(...)`. `wrap` tạo bản sao accessor nên user không được lưu vào phiên; tới `SUBSCRIBE` thì `accessor.getUser()` là null và bị ném `AccessDeniedException`. Cách Spring khuyến nghị là lấy accessor gốc bằng `MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class)`.
- **Kiểm tra sau khi sửa:** mở trang bếp bằng tài khoản KITCHEN, thấy "Realtime: đã kết nối"; tạo đơn từ tài khoản khác, ticket hiện ra không cần tải lại. Tài khoản CUSTOMER vẫn phải bị từ chối kết nối.
- **Liên quan:**
  - `frontend/src/pages/kitchen/kitchen.js` đặt `connectHeaders` một lần lúc tạo client. Access token sống 30 phút, nên sau đó mọi lần reconnect đều dùng token hết hạn. Nên lấy token mới mỗi lần kết nối (`beforeConnect`).
  - Chỉ `ORDER_CREATED`, `ITEM_ADDED`, `ITEM_UPDATED` phát sự kiện. Huỷ đơn, đổi trạng thái đơn, sửa/xoá món (`OrderService.cancel`, `transition`, `updateItem`, `removeItem`) không báo cho bếp.
  - `setAllowedOriginPatterns("*")` cho mọi origin.

### A2. Giờ hiển thị lệch +7 tiếng [Đã tái hiện] — ✅ ĐÃ SỬA 2026-11-12

- **Đã sửa (hướng 1 — ép phiên MySQL về UTC):**
  - `application.yml`: datasource URL dùng `connectionTimeZone=%2B00:00&forceConnectionTimeZoneToSession=true` (thay `serverTimezone=UTC`). **Đính chính so với gợi ý bên dưới:** không dùng được tên `UTC` — MySQL máy này chưa nạp bảng tên múi giờ nên `SET time_zone='UTC'` báo lỗi 1298; phải dùng offset `+00:00`.
  - Thêm `rms.time-zone` (mặc định `Asia/Ho_Chi_Minh`, đổi bằng `RMS_TIME_ZONE`).
  - `DashboardService.range()` cắt ngày theo múi giờ nhà hàng; `DashboardRepository.revenueByDay/peakHours` dùng `convert_tz(..., '+00:00', :offset)` (offset số, vì `CONVERT_TZ` với tên vùng cũng trả `NULL` trên máy này).
  - Test mới `DashboardServiceTest` (4 ca; 3 ca đỏ trước khi sửa).
- **Dữ liệu cũ:** đã chạy tay một lần `scripts/fix-a2-shift-timestamps.sql` trên `rms_db` local (trừ 7 tiếng ở các cột DB tự điền; có chốt chặn, chạy lại tự bỏ qua). Không phải migration Flyway — DB tạo mới sau bản sửa **không** chạy script này.
- **Đã kiểm chạy thật:** đơn mới `createdAt` trùng giờ UTC hiện tại; đơn #3 trả `01:47:22Z`; trang cài đặt hiện 18:58 8/11; audit / danh sách đơn staff hiện đúng giờ VN; giờ cao điểm ra 08h, 09h, 19h (giờ VN).
- **Chưa kiểm bằng dữ liệu thật:** ranh giới ngày của doanh thu (chưa có thanh toán nào rơi vào 00:00–07:00 giờ VN); phần này mới được bảo đảm bằng unit test.
- **Triệu chứng:** đơn tạo lúc 08:47 giờ VN hiện 15:47 ở màn bếp, danh sách đơn staff, audit log, hoá đơn; trang cài đặt hiện "cập nhật lần cuối 01:58 9/11" trong khi thực tế là 18:58 8/11.
- **Bằng chứng:** tại thời điểm UTC `01:47:22Z`, API trả `createdAt: "2026-11-12T08:47:22Z"` (sai), nhưng `paidAt: "…T01:48:47Z"` và `openedAt: "…T01:46:49Z"` (đúng).
- **Nguyên nhân:** các cột để MySQL tự điền bằng `DEFAULT CURRENT_TIMESTAMP` (`created_at`, `updated_at`, `issued_at`; entity khai báo `insertable = false`) được ghi theo múi giờ phiên MySQL (giờ máy, +07). App đọc mọi cột thời gian như UTC (`hibernate.jdbc.time_zone: UTC`, URL có `serverTimezone=UTC`). Các cột do code ghi bằng `Instant` thì đúng UTC. Kết quả: DB đang lẫn hai kiểu giờ.
- **Vị trí:** `backend/src/main/resources/application.yml` (datasource URL), các entity có `createdAt`/`updatedAt`, và mọi `DEFAULT CURRENT_TIMESTAMP` trong migration.
- **Hướng xử lý (chọn một, rồi làm nhất quán):**
  1. Ép múi giờ phiên MySQL về UTC trên kết nối (Connector/J có `connectionTimeZone=UTC` + `forceConnectionTimeZoneToSession=true`; kiểm tra lại tài liệu của phiên bản driver đang dùng), hoặc
  2. Để app tự gán `createdAt`/`updatedAt` thay vì dựa vào default của DB.
- **Chú ý quan trọng — sửa cùng lúc với dashboard:** hiện biểu đồ giờ cao điểm ra đúng giờ VN *chỉ vì* `created_at` đang lưu giờ máy. `DashboardRepository` dùng `hour(created_at)`, `date(paid_at)` và `DashboardService.range()` cắt ngày theo UTC. Sau khi đưa `created_at` về UTC thật, các truy vấn này phải đổi sang giờ nhà hàng (Asia/Ho_Chi_Minh), nếu không giờ cao điểm sẽ lệch −7 tiếng và doanh thu ngày cắt lúc 07:00 sáng.
- **Dữ liệu cũ:** các dòng đã có đang lưu giờ máy. Cần quyết định có viết migration dịch −7 tiếng cho các cột đó hay chấp nhận dữ liệu test cũ lệch.
- **Kiểm tra:** tạo đơn, so `createdAt` trong response với giờ UTC hiện tại; xem giờ hiển thị ở bếp, staff, audit; xem biểu đồ giờ cao điểm đúng giờ VN.

### A3. Lỗi validation không có thông báo [Đã tái hiện] — ✅ ĐÃ SỬA 2026-11-12

- **Đã sửa:**
  - `GlobalExceptionHandler`: thêm handler cho `@Valid` trên body, ràng buộc tham số (`HandlerMethodValidationException` và `ConstraintViolationException` — controller có `@Validated` cấp class đi đường thứ hai), JSON hỏng/thiếu body, sai kiểu tham số, file upload quá lớn (413), `AccessDeniedException` (403), và handler chung cho `Exception`.
  - Handler chung giữ đúng mã HTTP của lỗi chuẩn Spring MVC (405, 415, thiếu tham số…) qua `ErrorResponse`; lỗi không lường trước thì ghi log đầy đủ và trả 500 `INTERNAL_ERROR` chung chung.
  - `DataIntegrityViolationException` → 409 `CONFLICT`; `DataAccessException` → 500 `DATA_ACCESS` với thông báo chung, chi tiết SQL chỉ ghi log.
  - `frontend/src/api.js`: nối `errors` từng ô vào `err.message` và gắn `err.errors`, để các trang đang hiện `err.message` thấy được ô nào sai.
  - Test mới `GlobalExceptionHandlerTest` (13 ca, đều đỏ trước khi sửa).
- **Mã lỗi mới:** `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `INVALID_PARAMETER`, `FILE_TOO_LARGE`, `CONFLICT`, `INTERNAL_ERROR`; lỗi chuẩn Spring dùng tên mã HTTP (`BAD_REQUEST`, `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE`…).
- **Đã kiểm chạy thật:** ca register trong mục "Kiểm tra" trả `errors` đủ 3 trường; JSON hỏng, thiếu body, sai kiểu, tham số ngoài khoảng, thiếu tham số, sai method, sai Content-Type đều ra `ApiError`; 401/403/404 và lỗi nghiệp vụ không đổi; trang đăng ký hiện thông báo từng trường.
- **Chưa kiểm chạy thật:** nhánh 409 (trùng khoá), 413 (upload quá lớn) và 500 chung — mới có unit test.
- **Triệu chứng:** gửi body thiếu trường, sai định dạng, hoặc JSON hỏng → 400 với body mặc định của Spring (`timestamp, status, error, path`), không có `code`/`message`. FE chỉ hiện "Request failed". `ApiError.errors` luôn null.
- **Vị trí:** `common/GlobalExceptionHandler.java` — chỉ bắt `ApiException`, `NoResourceFoundException`, `DataAccessException`.
- **Cần bổ sung:** handler cho lỗi `@Valid` trên body (điền danh sách `errors` theo trường), lỗi validate tham số, JSON không đọc được, sai kiểu tham số, và một handler chung cho `Exception` (ghi log, trả 500 chung).
- **Cũng trong file này:** handler `DataAccessException` trả nguyên thông báo lỗi DB cho client (lộ tên bảng/cột) và biến lỗi trùng khoá thành 500 thay vì 409.
- **Kiểm tra:** `POST /api/v1/auth/register` với `{"fullName":"","email":"x","password":""}` phải trả `ApiError` có `errors` từng trường.

---

## B. Lỗi nghiệp vụ và bảo mật

### B1. API QR vẫn mở công khai [Đã tái hiện] — ✅ ĐÃ SỬA 2026-11-12

- **Quyết định:** gỡ hẳn API QR của khách; giữ trang admin "Bàn & QR" và cột token trong DB (không cần migration).
- **Đã gỡ:** `QrController`, `QrOrderController`; dòng `permitAll` cho `/api/v1/qr/**` trong `SecurityConfig`; `TableService.qrMenu/qrSession/resolveOpenSession/ensureOpenSession/validQrTable`; `OrderService.createFromQr/trackByQr` (kèm phụ thuộc `TableService` không còn dùng); `TableDtos.QrMenuResponse`; `DiningTableRepository.findByQrTokenHash`; `QrTokenService.matches`; `frontend/src/pages/orders/qr.js`.
- **Còn giữ:** `GET/POST /admin/tables/{id}/qr[/rotate]`, `GET /staff/tables/{id}/qr`, `TableQrBootstrap`, `QrTokenService.rotate`, `qr.html` (trang chuyển hướng). Token QR giờ không mở được gì; link `orderUrlPath` vẫn trỏ về `qr.html`.
- **Test mới:** `PublicQrApiRemovedTest` — test đầu tiên chạy qua `SecurityFilterChain` thật (`@WebMvcTest` + `SecurityConfig`); 5 ca đều đỏ trước khi sửa.
- **Đã kiểm chạy thật:** với token QR thật của T01, ẩn danh gọi `/qr/menu`, `/qr/session`, `/qr/orders` đều 401; đã đăng nhập thì 404; bàn, phiên, số đơn không đổi.
- **Lưu ý khi chạy lại:** đã xoá file `.java` nên phải `mvn clean` một lần, nếu không `target/classes` còn `.class` cũ của controller QR.

- Luồng đã chốt là "không gọi món bằng QR", nhưng backend vẫn chạy:
  - `GET /api/v1/qr/menu?token=…` và `/qr/session?token=…` — không cần đăng nhập, **tự mở phiên bàn và đổi bàn sang OCCUPIED** (đã thấy T01 đổi trạng thái sau một lệnh GET ẩn danh).
  - `POST /api/v1/qr/orders?token=…` — tạo đơn ẩn danh (`userId = null`).
- Token không hết hạn; token gốc lưu cùng hash trong `dining_tables.qr_token`.
- **Vị trí:** `config/SecurityConfig.java` (`/api/v1/qr/**` permitAll), `table/controller/QrController`, `order/controller/QrOrderController`, `TableService.qrMenu/qrSession/resolveOpenSession/ensureOpenSession`, `OrderService.createFromQr/trackByQr`, và `frontend/src/pages/orders/qr.js` (code chết, `qr.html` không còn nạp).
- **Cần quyết định:** gỡ hẳn, hay giữ nhưng yêu cầu đăng nhập/chỉ staff. Trang admin "Bàn & QR" đang dùng `GET/POST /admin/tables/{id}/qr[/rotate]`.

### B2. Hoàn tất đơn không cần thanh toán [Đã tái hiện] — ✅ ĐÃ SỬA 2026-11-12

- **Đã sửa:** `OrderService.transition` từ chối mọi yêu cầu sang `COMPLETED` với 409 `PAYMENT_REQUIRED`; bỏ nhánh `case COMPLETED`. `PaymentService.record` (`order.settle()`) không đổi và là đường duy nhất tới `COMPLETED`. Frontend không đổi (trang staff vốn không có nút gửi `COMPLETED`).
- **Test mới:** `OrderServiceTransitionTest` (3 ca; ca `SERVED → COMPLETED` đỏ trước khi sửa) — test đầu tiên chạy `OrderService` thật với repository giả.
- **Đã kiểm chạy thật:** đơn đi `CONFIRMED → … → SERVED` bình thường; `{"status":"COMPLETED"}` trả 409 `PAYMENT_REQUIRED`, đơn vẫn `SERVED`, chưa có hoá đơn; thanh toán tiền mặt đúng số tiền → đơn `COMPLETED`, có hoá đơn.
- **Dữ liệu cũ:** đơn id 4 (`COMPLETED` không có thanh toán, tạo trước khi sửa) vẫn còn trong `rms_db`; chưa xử lý.

- `PATCH /api/v1/staff/orders/{id}/status` với `{"status":"COMPLETED"}` thành công từ `SERVED`. Đơn thành COMPLETED, không có payment, `GET /invoices/by-order/{id}` trả 404, không tính vào doanh thu.
- **Vị trí:** `OrderService.transition` (nhánh `case COMPLETED`). Chỉ `PaymentService.record` (gọi `order.settle()`) mới nên đưa đơn sang COMPLETED.

### B3. Khách gọi món vào phiên của nhóm khác [Đã tái hiện] — ✅ ĐÃ SỬA 2026-11-12

- **Đã sửa (theo "Hướng xử lý" bên dưới):**
  - Migration `V13__table_session_reservation.sql`: thêm `table_sessions.reservation_id` (NULL = khách vãng lai / phiên cũ; UNIQUE; FK tới `reservations`).
  - `TableSession.reservationId` (lưu id, như `Payment.orderId`); `TableSessionRepository.findByReservationIdAndStatus`.
  - Check-in (`ReservationService.openSessionFor`): mở phiên riêng gắn `reservation_id`; bàn còn phiên mở → 409 `TABLE_OCCUPIED` (không dùng chung phiên nữa).
  - `OrderService.orderingContext`: tìm phiên theo lượt đặt, không theo bàn.
  - `TableService.closeSession`: lượt đặt gắn với phiên đang `CHECKED_IN` → tự `COMPLETED`.
- **Cố ý không làm:** staff bấm "hoàn tất" lượt đặt **không** tự đóng phiên (sẽ đóng cả phiên còn đơn chưa thanh toán — lỗi B4). Khách mất ngữ cảnh gọi món ngay khi lượt đặt hoàn tất; phiên vẫn mở để staff thu tiền rồi đóng. Nên chốt lại cùng B4.
- **Test mới:** `ReservationSessionOwnershipTest` (7 ca trên ba service thật; 5 ca đỏ trước khi sửa).
- **Đã kiểm chạy thật (kịch bản "Tái hiện" bên dưới):** check-in T03 → đóng phiên (lượt đặt tự `COMPLETED`) → mở phiên vãng lai → khách cũ gọi `/orders/context` và `POST /orders` đều 404, phiên vãng lai không có đơn nào; check-in lượt đặt khác khi bàn còn phiên vãng lai → 409 `TABLE_OCCUPIED`; đóng phiên vãng lai rồi check-in → phiên riêng, khách gọi món vào đúng phiên đó.
- **Chưa kiểm chạy thật:** nhánh 409 `SESSION_NOT_OPEN` của `orderingContext` (lượt đặt `CHECKED_IN` nhưng phiên của nó đã đóng) — sau bản sửa không còn tạo được trạng thái này qua API; mới có unit test.

- **Tái hiện:** khách check-in bàn T03 → staff đóng phiên (lượt đặt vẫn `CHECKED_IN`) → staff mở phiên mới cho khách vãng lai ở T03 → khách cũ gọi `GET /orders/context` nhận phiên mới và `POST /orders` thành công vào phiên đó.
- **Nguyên nhân:** `OrderService.orderingContext` đi từ lượt đặt `CHECKED_IN` → bàn → *phiên đang mở của bàn*, không phải phiên của chính lượt đặt. `TableService.closeSession` không hoàn tất lượt đặt; `ReservationService` hoàn tất lượt đặt không đóng phiên. `openSessionIfNeeded` dùng chung phiên nếu bàn đã có phiên mở.
- **Hướng xử lý:** thêm `reservation_id` vào `table_sessions` (migration mới); context tìm phiên theo lượt đặt; đóng phiên thì hoàn tất lượt đặt gắn với nó; check-in bị từ chối nếu bàn đang có phiên của nhóm khác.

### B4. Đóng phiên bàn khi còn đơn chưa thanh toán [Đã tái hiện] — ✅ ĐÃ SỬA 2026-11-12

- **Đã sửa:** `TableService.closeSession` trả 409 `SESSION_HAS_OPEN_ORDERS` nếu phiên còn đơn ở trạng thái khác `COMPLETED`/`CANCELLED` (kiểm sau khi khoá dòng phiên — tạo đơn cũng khoá dòng này nên không có đơn chen vào). Thêm `OrderRepository.existsByTableSession_IdAndStatusNotIn`. Frontend không đổi: trang sơ đồ bàn đã hiện `err.message` khi đóng phiên thất bại.
- **Quyết định đi kèm (điểm mở từ B3):** nút "Hoàn tất" lượt đặt giữ nguyên — chỉ đổi lượt đặt sang `COMPLETED`, không đóng phiên.
- **Test mới:** `TableServiceCloseSessionTest` (3 ca; 2 ca đỏ trước khi sửa).
- **Đã kiểm chạy thật:** phiên còn đơn `PENDING` → 409, phiên vẫn mở, bàn vẫn `OCCUPIED`; đơn `SERVED` chưa thanh toán → vẫn 409; thanh toán xong → đóng được, bàn `AVAILABLE`; phiên chỉ có đơn đã huỷ → đóng được.

- `POST /staff/sessions/{id}/close` thành công dù phiên còn đơn `PENDING`. **Vị trí:** `TableService.closeSession`.

### B5. Đặt bàn tương lai bị chặn theo trạng thái hiện tại [Đã tái hiện] — ✅ ĐÃ SỬA 2026-11-12

- **Quyết định:** (1) `RESERVED` chỉ còn khi staff đặt tay — đặt bàn không tự đổi trạng thái bàn, trừ check-in; (2) bàn đang có khách vẫn chặn các khung giờ gần: phiên đang mở được coi như giữ bàn từ lúc mở đến hết thời lượng đặt bàn trong cài đặt (180 phút).
- **Đã sửa (`ReservationService`):**
  - `availability` / `create`: bỏ lọc theo `OCCUPIED` và "có phiên mở"; chỉ chặn `OUT_OF_SERVICE`, lượt đặt chồng giờ, và khung giờ bắt đầu trước `openedAt + thời lượng` của phiên đang mở (`heldBySeatedParty`).
  - `transition`: confirm không gán `RESERVED`; cancel / reject / complete / no-show không gán `AVAILABLE`.
- **Lỗi phụ được sửa theo:** trước đây huỷ/hoàn tất lượt đặt ghi đè bàn `OUT_OF_SERVICE` (và giữ chỗ tay) về `AVAILABLE`.
- **Test mới:** `ReservationAvailabilityTest` (10 ca; 6 ca đỏ trước khi sửa).
- **Đã kiểm chạy thật (kịch bản "Tái hiện" bên dưới):** T03 đang có khách → `availability` tuần sau có T03, `POST /reservations` T03 tuần sau → 201; khung giờ 30 phút nữa → không có T03, đặt → 409 `TABLE_UNAVAILABLE`; xác nhận lượt đặt tuần sau (T02, T03) → trạng thái bàn không đổi; huỷ → không đổi; đóng phiên → T03 về `AVAILABLE`.
- **Hệ quả:** sơ đồ bàn staff không tự hiện "Đã giữ chỗ" khi xác nhận lượt đặt; `reservedTables` trên dashboard chỉ đếm bàn staff đặt tay.
- **Giới hạn đã biết:** phiên mở lâu hơn 180 phút thì không còn chặn khung giờ gần (check-in khi đó bị 409 `TABLE_OCCUPIED` theo B3). Staff vẫn mở được phiên vãng lai trên bàn có lượt đặt đã xác nhận sắp tới giờ — chưa xử lý.

- **Tái hiện:** T03 đang có khách ngồi → `GET /tables/availability` cho tuần sau không có T03; `POST /reservations` cho T03 tuần sau trả 409 `TABLE_UNAVAILABLE`.
- Chiều ngược lại: xác nhận một lượt đặt cho tuần sau thì bàn chuyển `RESERVED` ngay hôm nay.
- **Vị trí:** `ReservationService.availability`, `create` (lọc theo `table.getStatus()` và phiên đang mở), `transition` (gán `TableStatus.RESERVED` khi confirm; gán `AVAILABLE` khi huỷ/hoàn tất).
- **Hướng xử lý:** trạng thái bàn chỉ phản ánh hiện tại; khả dụng cho một khung giờ chỉ xét các lượt đặt chồng giờ (và `OUT_OF_SERVICE`).

### B6. Trạng thái đơn và trạng thái món không liên kết [Đã tái hiện] — ✅ ĐÃ SỬA 2026-11-12

- **Quy tắc đã chốt:** (1) đơn tự động theo bếp — bếp bắt đầu món đầu tiên → `IN_PROGRESS`, mọi món (không tính món huỷ) `READY` → `READY`; staff chỉ còn `CONFIRMED` và `SERVED`; (2) bếp chỉ làm món của đơn staff đã xác nhận.
- **Đã sửa:**
  - `KitchenService.updateItemStatus`: từ chối 409 `ORDER_NOT_IN_KITCHEN` nếu đơn không ở `CONFIRMED`/`IN_PROGRESS`; sau khi đổi món thì đẩy trạng thái đơn (`advanceOrder`).
  - `KitchenService.queue`: chỉ hiện đơn `CONFIRMED` / `IN_PROGRESS` / `READY` (đơn `PENDING` không còn hiện ở bếp).
  - `OrderService.transition`: `IN_PROGRESS` / `READY` gửi tay → 409 `KITCHEN_DRIVEN_STATUS`; `SERVED` khi còn món chưa `READY` → 409 `ITEMS_NOT_READY`; mỗi lần đổi trạng thái phát sự kiện bếp `ORDER_STATUS_CHANGED`.
  - `OrderService.updateItem` / `removeItem`: phát sự kiện bếp (`ITEM_CHANGED`, `ITEM_REMOVED`) — phần còn treo từ A1.
  - `frontend/src/pages/orders/staff-orders.js`: bỏ nút `→ IN_PROGRESS`, `→ READY`; hiện dòng "chờ bếp" cho đơn `CONFIRMED` / `IN_PROGRESS`.
- **Test mới:** `KitchenServiceTest` (7 ca) + 5 ca thêm vào `OrderServiceTransitionTest`; 10 ca đỏ trước khi sửa.
- **Đã kiểm chạy thật:** đơn `PENDING` không hiện ở bếp, bếp làm món → 409; staff xác nhận → ticket hiện ở màn bếp không cần tải lại; món 1 `PREPARING` → đơn `IN_PROGRESS`; staff gửi `SERVED`/`READY` sớm → 409; món cuối `READY` → đơn `READY`; staff `SERVED` → ticket biến mất khỏi màn bếp; trang staff chỉ còn nút đúng vai.
- **Chưa làm:** huỷ đơn chưa báo bếp (B7). Trang đơn của staff không có realtime — staff phải tải lại mới thấy đơn sang `READY`.
- **Lỗi phụ REVIEW chưa nêu, đã sửa theo:** bếp từng đổi được trạng thái món của đơn đã `SERVED` / `CANCELLED`.

- Staff đưa đơn tới `SERVED` trong khi một món còn `PENDING`; đơn lập tức biến khỏi hàng đợi bếp (`KitchenService.queue` ẩn đơn `SERVED`).
- Staff phải bấm tay 4 bước (CONFIRMED → IN_PROGRESS → READY → SERVED) bất kể bếp đã làm tới đâu.
- **Cần quyết định quy tắc:** ví dụ đơn tự sang READY khi mọi món READY, và không cho SERVED khi còn món chưa READY.

### B7. Huỷ đơn chưa trọn [Đã tái hiện] — ✅ ĐÃ SỬA 2026-11-12

- **Đã sửa (`OrderService.cancel`):** ngoài đổi trạng thái đơn, giờ còn (1) đặt mọi món sang `CANCELLED`; (2) trả lại lượt dùng mã giảm giá — thêm `Discount.release()` (không xuống dưới 0) và `DiscountService.release(id)` (khoá dòng mã như lúc `consume`); dòng `order_discounts` giữ lại làm lịch sử; (3) ghi audit `ORDER_CANCELLED` kèm lý do; (4) phát sự kiện bếp `ORDER_CANCELLED`. `OrderService` nhận thêm `AuditService`.
- **Test mới:** `OrderServiceCancelTest` (7 ca; 4 ca đỏ trước khi sửa) — `OrderService` + `DiscountService` thật, repository giả.
- **Đã kiểm chạy thật:** đơn có mã, đã `CONFIRMED` → `usedCount` 1; huỷ → `usedCount` 0, cả hai món `CANCELLED`, kênh bếp nhận `ORDER_CANCELLED`, hàng đợi bếp trống, audit có dòng `ORDER_CANCELLED` với `actorId` của staff và lý do.
- **Không đổi:** chỉ huỷ được đơn `PENDING`/`CONFIRMED` (đơn bếp đã bắt đầu thì không). Khách chủ đơn chưa được thông báo khi đơn bị huỷ — REVIEW không yêu cầu.
- **Dữ liệu cũ:** mã `CLAUDETEST10` vẫn `usedCount = 2` (các đơn dùng nó bị huỷ trước bản sửa); chưa chỉnh.

- Huỷ đơn có mã giảm giá: `usedCount` giữ nguyên (2 trước và sau khi huỷ). `Discount` chỉ có `consume()`.
- Món của đơn huỷ vẫn `PENDING`; không phát sự kiện cho bếp; không ghi audit.
- **Vị trí:** `OrderService.cancel`, `Discount`.

### B8. Món thuộc danh mục đã tắt vẫn hiện trên menu [Đã tái hiện] — ✅ ĐÃ SỬA 2026-11-12

- **Đã sửa:** `MenuService.catalog()` và `byCategory()` lọc thêm `item.getCategory().isActive()` — cùng điều kiện với `OrderService.requireAvailableMenuItem`. `GET /admin/menu` không đổi (admin vẫn thấy mọi món).
- **Test mới:** `MenuServiceTest` (4 ca; 2 ca đỏ trước khi sửa).
- **Đã kiểm chạy thật:** tắt "Đồ uống" → `GET /menu` không còn "Trà đào", `GET /menu/categories/3/items` trả rỗng, admin vẫn thấy đủ 5 món; bật lại → "Trà đào" hiện lại. Danh mục đã được bật lại như cũ.
- **Ghi chú:** lọc trong Java sau khi truy vấn (thực đơn nhỏ); nếu thực đơn lớn thì đưa điều kiện vào truy vấn cùng lúc với C1.

- Tắt danh mục "Đồ uống" → `GET /menu` bỏ danh mục khỏi `categories` nhưng "Trà đào" vẫn nằm trong `items`. Bấm đặt thì bị từ chối `MENU_ITEM_UNAVAILABLE` (vì `OrderService.requireAvailableMenuItem` có kiểm tra danh mục).
- **Vị trí:** `MenuService.catalog()` và `byCategory()`.

### B9. Đổi mật khẩu không thu hồi phiên khác [Đã tái hiện] — ✅ ĐÃ SỬA 2026-11-12

- **Quyết định:** đổi mật khẩu thu hồi **mọi** refresh token của tài khoản, kể cả phiên đang dùng — người dùng đăng nhập lại. Hai việc "liên quan" (dọn token cũ, giới hạn đăng nhập sai) **chưa làm**, để quyết sau.
- **Đã sửa:** `AuthService.changePassword` thu hồi mọi token còn hiệu lực (`RefreshTokenRepository.findByUser_IdAndRevokedAtIsNull`); API vẫn trả 204. `frontend/src/pages/auth/profile.js`: báo "vui lòng đăng nhập lại", xoá token và về trang đăng nhập.
- **Test mới:** `AuthServiceChangePasswordTest` (5 ca; 2 ca đỏ trước khi sửa).
- **Đã kiểm chạy thật:** tài khoản tạm đăng nhập trên hai "thiết bị"; đổi mật khẩu ở trang hồ sơ → trang báo và chuyển về đăng nhập, token trong trình duyệt bị xoá; thiết bị kia `POST /auth/refresh` → 401; mật khẩu cũ → 401; mật khẩu mới → 200.
- **Giới hạn còn lại:** access token (JWT, sống 30 phút) của phiên khác không thu hồi được ngay — vẫn dùng được tới khi hết hạn, sau đó không gia hạn được nữa. Muốn cắt ngay cần thêm mốc "đổi mật khẩu lúc" để filter so với thời điểm cấp token (cần migration).

- Sau `PUT /auth/me/password`, refresh token của phiên đăng nhập khác vẫn dùng được.
- **Vị trí:** `AuthService.changePassword`. Liên quan: token đã thu hồi/hết hạn không bao giờ bị xoá khỏi `refresh_tokens`; không giới hạn số lần đăng nhập sai.

### B10. Một khách giữ được nhiều bàn [Đã tái hiện] — ✅ ĐÃ SỬA 2026-11-12

- **Quy tắc đã chốt:** (1) một khách không được có hai lượt đặt chồng giờ, bất kể bàn nào; (2) lượt đặt `PENDING` tới giờ bắt đầu mà chưa được xác nhận thì tự `CANCELLED`; (3) lượt đặt `CONFIRMED` quá giờ bắt đầu 30 phút mà chưa check-in thì tự `NO_SHOW`.
- **Đã sửa:**
  - `ReservationService.create`: khoá dòng user (`UserRepository.lockById`) rồi kiểm `ReservationRepository.existsOverlapForUser` → 409 `RESERVATION_OVERLAP`.
  - `ReservationService.expireStale(now, grace)` + `ReservationRepository.lockDue` (khoá dòng để không ghi đè thao tác của staff).
  - `ReservationExpiryJob` (`@Scheduled`, quét mỗi 60 giây) + `SchedulingConfig` (`@EnableScheduling`) — tác vụ định kỳ đầu tiên của dự án.
  - `application.yml`: `rms.reservation.no-show-grace-minutes` (mặc định 30, đổi bằng `RMS_NO_SHOW_GRACE_MINUTES`), `sweep-interval-ms`.
- **Test mới:** `ReservationHoldLimitsTest` (7 ca; 5 ca đỏ trước khi sửa).
- **Đã kiểm chạy thật:** cùng khách đặt bàn thứ hai trùng giờ → 409; lệch 1 tiếng (vẫn chồng) → 409; khác ngày → 201. Tác vụ quét: lùi giờ ba lượt đặt test trong DB để giả lập thời gian trôi → `PENDING` quá giờ 5 phút thành `CANCELLED`, `CONFIRMED` quá 40 phút thành `NO_SHOW`, `CONFIRMED` quá 10 phút giữ nguyên; log có "Reservation sweep closed 2 stale reservation(s)"; trạng thái bàn không đổi.
- **Không làm:** không thông báo cho khách khi lượt đặt tự huỷ / tự `NO_SHOW`; không ghi audit cho thao tác tự động; chưa giới hạn tổng số lượt đặt của một khách ở các khung giờ khác nhau (bạn chọn chỉ chặn chồng giờ).

- Cùng tài khoản đặt hai bàn cùng khung giờ đều được nhận; lượt đặt `PENDING` chặn chỗ vô thời hạn; không tự chuyển `NO_SHOW`.
- **Vị trí:** `ReservationService.create`.

### B11. Các điểm bảo mật/kiểm tra đầu vào khác [Đọc code] — ✅ ĐÃ SỬA 2026-11-13

Cả năm điểm đã được xác nhận trên code rồi sửa, mỗi điểm có test đỏ trước.

- **Thuế suất:** `SettingsController.Update.taxRate` thêm `@DecimalMax("1")`; ô nhập trang cài đặt thêm `max="1"`. Test `SettingsControllerTest` (4 ca).
- **`POST /discounts/validate`:** bỏ dòng `permitAll` trong `SecurityConfig` → phải đăng nhập. Chưa thêm giới hạn tần suất. Test `DiscountValidationSecurityTest` (2 ca, chạy qua SecurityFilterChain thật).
- **Giới hạn mã theo khách (quyết định: mỗi tài khoản khách dùng mỗi mã một lần, đơn đã huỷ không tính, đơn staff tạo cho khách vãng lai không giới hạn):** `DiscountService.lockApplicable(code, subtotal, customerId)` trả 409 `DISCOUNT_ALREADY_USED`; `OrderDiscountRepository.existsActiveUseByCustomer`. Không cần migration. Test `DiscountPerCustomerTest` (4 ca).
- **Upload ảnh món (`MenuService.storeImage`):** loại file xác định bằng chữ ký đầu file (JPG/PNG/WEBP), không tin `Content-Type`; thay ảnh thì xoá file cũ; không ghi được file → 500 `IMAGE_STORE_FAILED` (trước là 400). Test `MenuImageUploadTest` (5 ca). **Chưa đổi:** thư mục `uploads/menu` vẫn là đường dẫn tương đối, cấu hình qua `RMS_MENU_DIR`.
- **Admin tạo user trùng email cùng lúc:** `AdminUserService.create` dùng `saveAndFlush` và bắt lỗi trùng khoá → 409 `EMAIL_EXISTS` (sau A3 đã là 409 `CONFLICT` chứ không còn 500). Test `AdminUserServiceTest` (3 ca).
- **Đã kiểm chạy thật:** `taxRate=8` → 400, cài đặt không đổi; `/discounts/validate` ẩn danh → 401, đã đăng nhập → vào tới service; file text khai là PNG → 400, PNG thật → lưu, JPEG khai là PNG → lưu thành `.jpg` và file PNG cũ bị xoá; hai yêu cầu tạo cùng email gửi đồng thời → một 201, một 409 `EMAIL_EXISTS`; khách dùng mã lần hai → 409 `DISCOUNT_ALREADY_USED`, huỷ đơn đầu thì dùng lại được, hai đơn vãng lai cùng mã đều được.
- **Chưa kiểm chạy thật:** nhánh 500 khi không ghi được file; và không xác định được yêu cầu trùng email bị chặn ở bước kiểm trước hay ở ràng buộc UNIQUE (cả hai đều ra `EMAIL_EXISTS`).
- **Ghi chú:** `/discounts/validate` không áp luật "mỗi khách một lần" vì nó không biết đơn sẽ thuộc về ai; luật chỉ được áp lúc tạo đơn.

- `SettingsController.Update.taxRate` chỉ `@Min(0)`: nhập 8 thay vì 0.08 thành thuế 800%.
- `POST /api/v1/discounts/validate` mở công khai, không giới hạn tần suất, FE không dùng → dò được mã.
- Mã giảm giá không giới hạn số lần theo từng khách.
- Upload ảnh món (`MenuService.storeImage`): chỉ tin `Content-Type` client gửi; ảnh cũ không xoá; thư mục `uploads/menu` là đường dẫn tương đối (phụ thuộc nơi chạy lệnh); lỗi ghi file trả 400.
- Admin tạo user trùng email cùng lúc → 500 (`AdminUserService.create` không bắt lỗi trùng khoá như `AuthService.register`).

---

## C. Hiệu năng và chất lượng

### C1. Tải toàn bộ bảng mỗi lần [Đọc code] — ✅ ĐÃ SỬA 2026-11-13 (phạm vi: lọc trong truy vấn + giới hạn, không phân trang)

- **Quyết định:** lọc trong truy vấn và giới hạn số dòng; **không** đổi dạng response (chưa có `page`/`size`), nên frontend không đổi.
- **Đã sửa:**
  - `KitchenService.queue()`: `OrderRepository.findWithDetailsByStatusIn(CONFIRMED, IN_PROGRESS, READY)` — CSDL lọc theo trạng thái.
  - `OrderService.listAll()` (`GET /staff/orders`): tối đa 200 đơn mới nhất, hai bước `findRecentIds(Pageable)` → `findWithDetailsByIdIn` (tránh Hibernate phân trang trong bộ nhớ khi LIMIT đi cùng fetch join `items`).
  - `ReservationService.listAll()` (`GET /staff/reservations`): tối đa 200 lượt, giờ bắt đầu muộn nhất trước.
  - Migration `V14__orders_created_at_index.sql`: index `orders(created_at)`.
  - Bỏ hai truy vấn nạp cả bảng không còn ai dùng.
- **Test:** `StaffListLimitsTest` (3 ca) + 2 ca trong `KitchenServiceTest`; 4 ca đỏ trước khi sửa.
- **Đã kiểm chạy thật:** V14 chạy trên `rms_db`; `/staff/orders` và `/staff/reservations` trả đúng số dòng và thứ tự như DB; hàng đợi bếp: đơn `PENDING` không hiện, xác nhận thì hiện, huỷ thì mất; `EXPLAIN` cho truy vấn "200 đơn mới nhất" dùng `idx_orders_created_at` (quét index ngược, không sắp xếp cả bảng); log không có cảnh báo phân trang trong bộ nhớ.
- **Chưa kiểm chạy thật:** ngưỡng 200 (DB mới có 23 đơn, 15 lượt đặt).
- **Chưa làm:** phân trang thật (staff chưa xem được đơn thứ 201 trở đi); danh sách user, món admin, danh mục, mã giảm giá vẫn trả cả bảng (bảng nhỏ theo bản chất); `GET /orders/my` của khách chưa giới hạn.

- `KitchenService.queue()` và `OrderService.listAll()` gọi `findAllByOrderByCreatedAtDesc()` (kèm items) rồi lọc trong bộ nhớ. Màn bếp gọi lại sau mỗi sự kiện realtime.
- `ReservationService.listAll()`, danh sách user, món admin cũng không phân trang.
- Hướng xử lý: lọc theo trạng thái trong truy vấn, thêm phân trang; thêm index `orders(created_at)`.

### C2. Test chưa chạm logic [Đọc code] — ✅ PHẦN TÍNH TIỀN XONG 2026-11-18 (còn test tích hợp MySQL)

- **Tiến độ:** các mục B (trạng thái đơn, huỷ, ngữ cảnh gọi món, đặt bàn, thanh toán trùng key) đã có test service từ lúc sửa từng mục. 2026-11-18 thêm phần tính tiền — 20 test, cả bộ 160/160:
  - `DiscountAmountTest` (8): phần trăm, làm tròn HALF_UP tới xu, mức trần, mã cố định, giảm không vượt tạm tính, mức tối thiểu tính cả bằng, khung hiệu lực tính cả hai đầu, hết lượt / đã tắt.
  - `OrderPricingTest` (9): ca chuẩn mục D (155 000 − 10% → thuế 11 160 → 150 660), không mã, thuế 0%, mã lớn hơn đơn → tổng 0, làm tròn thuế; sửa số lượng / xoá món / thêm món tính lại; tụt dưới mức tối thiểu thì bỏ giảm giá; món đã huỷ không tính; đổi giá menu sau khi gọi không đổi giá món đã gọi.
  - `PaymentAmountTest` (3): hoá đơn chép nguyên số của đơn; `150660` = `150660.00`; lệch một xu cũng bị từ chối và không ghi gì.
- Cả 20 test xanh ngay lần đầu — code tính tiền hiện tại đúng như mục D. Đã thử làm hỏng có chủ ý (tính thuế trên tạm tính thay vì sau giảm giá) → 3 test đỏ, rồi trả code về nguyên.
- **Quy tắc cần chốt (chưa đổi code):**
  1. Tiền lẻ dưới 1 đồng: thuế/giảm giá làm tròn tới 2 chữ số thập phân, nên có thể ra tổng như 10 751,08đ. Màn hình hiện số đã làm tròn (10.751 ₫) nhưng thanh toán phải đúng 10 751,08. Với giá món chẵn nghìn và thuế 8% hiếm gặp; mã giảm phần trăm dễ gây ra.
  2. Sửa đơn làm tạm tính tụt dưới mức tối thiểu: giảm giá về 0 nhưng mã vẫn gắn với đơn và vẫn tính là đã dùng (lượt dùng, luật một khách một lần).
  3. Sửa đơn tính lại thuế theo thuế suất **hiện tại**, không phải lúc tạo đơn.
- **Còn lại:** test tích hợp trên MySQL thật (Testcontainers) cho ràng buộc DB (V13–V15, cột MySQL tự điền).

- 5 test đều `MockMvcBuilders.standaloneSetup(...)` với service giả: không đi qua security, validation hay logic nghiệp vụ.
- Ưu tiên viết test cho: tính tiền/thuế/giảm giá, chuyển trạng thái đơn (B2, B6), huỷ đơn (B7), ngữ cảnh gọi món (B3), đặt bàn (B5, B10), thanh toán.
- Test tích hợp cần MySQL thật (ví dụ Testcontainers) vì schema dùng cột generated kiểu MySQL.

### C3. Schema [Đọc code] — ✅ ĐÃ XỬ LÝ 2026-11-13 (2 ý đã sửa, 1 ý không sửa được)

- **Chứng từ tiền bị xoá dây chuyền — đã sửa:** migration `V15__protect_financial_records.sql` đổi `fk_payments_order` và `fk_invoices_order` từ `ON DELETE CASCADE` sang mặc định (RESTRICT). `order_items` và `order_discounts` vẫn CASCADE. Không đụng code (không có code nào xoá đơn).
  - Kiểm chạy thật: trước V15, `DELETE` đơn #3 (trong transaction, đã rollback) xoá luôn payment và invoice; sau V15, cùng lệnh đó bị từ chối (lỗi 1451), dữ liệu nguyên vẹn; đơn không có chứng từ vẫn xoá được.
- **`idempotency_key` — đã sửa ở tầng code (quyết định: giữ index duy nhất toàn cục):** `OrderService.replay` chỉ trả lại đơn cũ khi **cùng người tạo và cùng phiên**; người khác gửi key đó nhận 409 `IDEMPOTENCY_KEY_REUSED` (trước đây chỉ kiểm cùng phiên, nên người khác trong cùng phiên nhận được đơn của người kia). Áp dụng cho cả nhánh bắt lỗi trùng khoá. Test `OrderIdempotencyTest` (4 ca; 2 ca đỏ trước khi sửa).
  - Kiểm chạy thật: staff1 tạo đơn rồi gửi lại cùng key → nhận lại đúng đơn đó, không tạo đơn mới; admin gửi cùng key trong cùng phiên → 409.
  - Chưa đổi: index vẫn duy nhất toàn cục (hai người dùng trùng key thì người sau nhận 409); `payments.idempotency_key` cũng vậy.
- **Dữ liệu mẫu trong migration có phiên bản (`V6`, `V7`); `V1` rỗng; `V5` và `V6` cùng tên mô tả — KHÔNG sửa được:** sửa nội dung hay tên file đã chạy làm lệch checksum Flyway và vi phạm ràng buộc "không đụng `V1`–`V12`". Quy ước cho migration mới: không chèn dữ liệu demo vào migration có phiên bản; tên mô tả phải khác nhau.
- **Kiểm phần D sau khi đổi khoá ngoại:** một lượt đầy đủ tạo đơn → bếp làm → phục vụ → thanh toán vẫn chạy; gửi lại cùng `Idempotency-Key` thanh toán không tạo payment trùng.

- `payments` và `invoices` có `ON DELETE CASCADE` theo `orders` — chứng từ tiền nên chặn xoá.
- `orders.idempotency_key` unique toàn cục, không theo người dùng.
- Dữ liệu mẫu nằm trong migration có phiên bản (`V6`, `V7`); `V1` rỗng; `V5` và `V6` cùng tên mô tả.

### C4. Khác [Đọc code, trừ khi ghi chú] — ✅ ĐÃ SỬA 2026-11-17 (cả 6 điểm)

- **PDF hoá đơn — REVIEW nói thiếu:** lỗi không chỉ ở font. `InvoicePdf` luôn chạy `asciiSafe()` trên tên món, nên kể cả máy có DejaVu thì "Gỏi cuốn" vẫn in ra "G?i cu?n" (đã tái hiện), và nhãn viết không dấu ("Hoa don", "Tam tinh"). Đã sửa: đóng gói `fonts/DejaVuSans.ttf` (+ `DejaVuSans-LICENSE.txt`, giấy phép Bitstream Vera cho phép phân phối lại) vào `resources`, nạp font từ classpath, bỏ `asciiSafe`, nhãn có dấu. Test `InvoicePdfTest` (3 ca, đỏ trước khi sửa). Chạy thật: PDF ra đúng "1 x Gỏi cuốn", "Hoá đơn", "Tạm tính", "Tổng".
- **`createdAt` null — đã sửa:** `Order.createdAt` / `updatedAt` thêm `@Generated` (Hibernate đọc lại cột do MySQL tự điền sau INSERT/UPDATE). Không có unit test (cần MySQL thật); chạy thật: trước sửa phản hồi tạo đơn có `createdAt = null`, sau sửa `2026-11-17T01:30:03.442Z` khớp giờ UTC lúc tạo.
- **Notification — đã sửa `markAll`:** một câu `UPDATE` cho mọi thông báo chưa đọc (`NotificationRepository.markAllRead`), thay cho đánh dấu 50 dòng đầu. Test `NotificationServiceTest` (2 ca, đỏ trước khi sửa). Chạy thật: staff1 27 chưa đọc → 0 (chưa tái hiện được ca > 50 bằng dữ liệu thật). **Không đổi:** mỗi đơn mới vẫn tạo một dòng cho từng STAFF/KITCHEN/ADMIN (thiết kế, không phải lỗi).
- **`JwtService` — đã sửa:** `DefaultJWTProcessor` dựng một lần trong constructor, dùng chung (như `NimbusJwtDecoder` của Spring Security); bỏ import trùng. Test `JwtServiceTest` (6 ca, gồm 400 token kiểm song song trên 8 luồng) xanh trước và sau khi sửa.
- **Bắt `DataIntegrityViolationException` rồi truy vấn tiếp — REVIEW đúng một phần:** không tái hiện được ở `OrderService` (6 yêu cầu cùng key gửi đồng thời đều nhận lại đúng một đơn — khoá dòng phiên bàn bắt chúng xếp hàng trước khi tới nhánh `catch`). Ở `PaymentService` thì có lỗi thật nhưng khác mô tả: 4 yêu cầu cùng key gửi đồng thời → 1 thành công, 3 nhận 409 `ORDER_NOT_PAYABLE` (không tạo trùng tiền), vì key được tra **trước** khi khoá đơn. Đã sửa: khoá dòng đơn trước rồi mới tra key. Test `PaymentIdempotencyTest` (4 ca; 1 ca đỏ trước khi sửa). Chạy thật sau sửa: cả 4 yêu cầu nhận cùng payment, 1 dòng payment. Nhánh `catch` của hai service giữ nguyên (gần như không tới được).
- **Audit — đã thêm:** `CATEGORY_CREATED/UPDATED/ENABLED/DISABLED`, `MENU_ITEM_CREATED/UPDATED` (ghi rõ giá cũ → giá mới khi đổi giá), `MENU_ITEM_STATUS_CHANGED`, `TABLE_CREATED/UPDATED/STATUS_CHANGED`, `TABLE_QR_ROTATED` (không ghi token), `SESSION_OPENED/CLOSED`. Huỷ đơn đã có từ B7. Test `AdminOperationsAuditTest` (8 ca; 7 đỏ trước khi sửa, ca "thao tác bị từ chối thì không ghi" xanh). Chạy thật: đổi giá Pepsi rồi trả lại, T01 ra/vào `OUT_OF_SERVICE`, mở/đóng phiên — đủ dòng audit với `actorId` đúng. **Chưa ghi:** check-in mở phiên qua lượt đặt, upload ảnh món.

- **PDF hoá đơn:** `InvoicePdf.font()` tìm font ở `/usr/share/fonts/...`; máy không có thì rơi về Helvetica và ký tự tiếng Việt bị thay bằng `?`. Nên đóng gói font vào `resources`. (PDF tạo được trên máy hiện tại — đã tái hiện.)
- **Audit:** chỉ ghi thanh toán, giảm giá, cài đặt, user. Huỷ đơn, đổi giá món, thao tác bàn/phiên chưa ghi.
- **Notification:** mỗi đơn mới tạo một dòng cho từng STAFF/KITCHEN/ADMIN; `markAll` chỉ đánh dấu 50 dòng đầu.
- **`createdAt` null** trong response ngay sau khi tạo đơn [Đã tái hiện] (cột `insertable = false`, entity chưa nạp lại).
- **Bắt `DataIntegrityViolationException` rồi truy vấn tiếp trong cùng transaction** (`OrderService.create`, `PaymentService.record`): transaction đã bị đánh dấu rollback nên nhánh dự phòng idempotency không chạy như ý.
- **`JwtService.parseUserId`** tạo `DefaultJWTProcessor` mới mỗi request; import trùng.

### C5. Frontend [Đọc code, trừ khi ghi chú] — ✅ ĐÃ SỬA 2026-11-17 (cả 10 điểm)

- **Số liệu REVIEW lệch:** `escapeHtml` có 17 bản (không phải 18), 12 bản thiếu escape `"` (không phải 6); `money` 6 bản + `fmt` 5 bản (11, không phải 13) cộng 2 chỗ tự `toLocaleString`. Không có bản thiếu `"` nào được dùng trong thuộc tính với dữ liệu người dùng (chỉ id số / enum) nên chưa thành XSS — lỗi tiềm ẩn. Đã gom vào `src/utils.js` (`escapeHtml` escape cả `"` và `'`, `money`, `formatDateTime`, `formatTime`), xoá mọi bản chép.
- **`crypto.randomUUID` + Idempotency-Key — đã sửa chung:** `sendIdempotent(scope, body, send)` trong `utils.js`: key có dự phòng khi không có `randomUUID` (`getRandomValues`, rồi `Date`+`Math.random`); bấm lại cùng nội dung sau lỗi mạng/5xx dùng lại key cũ, đổi nội dung hoặc đã thành công/4xx thì key mới; key chỉ nằm trong bộ nhớ trang. Áp cho giỏ, "Đặt ngay", staff tạo đơn, staff thu tiền. Lỗi mạng báo "bấm lại để gửi lại (không tạo trùng)". Chạy thật: giả lập mất phản hồi lần đầu → lần bấm lại gửi cùng key, staff tạo đơn ra đúng 1 đơn (id 28), thu tiền ra đúng 1 payment (id 10).
- **`fetchMe` — đã sửa:** chỉ xoá token khi 401; lỗi mạng/5xx giữ token, nav hiện "Mất kết nối" + nút Đăng xuất, trang hiện dòng lỗi, không đẩy về login. Chạy thật: tắt backend → giữ token, ở lại trang; bật lại → bình thường; token hỏng → xoá token, về login.
- **Đăng nhập — đã sửa:** 401 "Sai email hoặc mật khẩu (hoặc tài khoản đã bị khoá)" (backend cố ý trả cùng 401 cho tài khoản khoá), 400 sai định dạng, mất mạng, 5xx — mỗi loại một câu.
- **`requireRoles` — đã sửa:** sau khi chuyển hướng trả về promise không bao giờ xong thay vì `throw` → hết "Uncaught Error: Forbidden" (staff1 mở `admin/users.html`, `cart.html`: về trang chủ, console sạch).
- **`my-orders.html` — đã sửa** hai dòng chữ nhắc QR. (Trang admin Bàn vẫn có link "Mở trang gọi món QR" — ngoài phạm vi mục này, để nguyên.)
- **Nút Giỏ — đã sửa:** giỏ, `cart.html`, nút "Thêm giỏ"/"Đặt ngay" ở thực đơn chỉ cho CUSTOMER + ADMIN (`CART_ROLES` trong `nav.js`); STAFF/KITCHEN thấy "Tài khoản nhân viên chỉ xem thực đơn".
- **Staff tạo đơn / thu tiền — đã sửa:** nhiều dòng món (thêm/xoá dòng, dòng trùng món được gộp), chọn Tiền mặt / Chuyển khoản, chuyển khoản có ô mã giao dịch (tuỳ chọn); nút ghi rõ số tiền. Chạy thật: Cơm gà ×2 + Gỏi cuốn + Cơm gà → 1 đơn "Cơm gà ×3, Gỏi cuốn ×1" 226.800đ; thu chuyển khoản mã `VCB-C5-TEST-001` → `transaction_reference` lưu đúng.
- **`/auth/me` hai lần — đã sửa:** `fetchMe` nhớ kết quả thành công trong trang; nav và `requireRoles` dùng chung → 1 lần/trang (đã đếm trên `staff/orders.html`, `menu.html`).

- `staff-orders.js` gọi `crypto.randomUUID()` không có dự phòng (các file khác có) → lỗi khi mở qua `http://<IP LAN>`.
- `Idempotency-Key` sinh mới mỗi lần bấm, nên bấm lại sau lỗi mạng không idempotent.
- `auth.js` → `fetchMe()` xoá token với mọi lỗi (kể cả mạng/500); chỉ nên xoá khi 401.
- `main.js` báo "Sai email hoặc mật khẩu" cho mọi lỗi đăng nhập.
- `escapeHtml` chép 18 lần, 6 bản thiếu escape `"`; `money`/`fmt` chép 13 lần → gom vào một `utils.js`.
- `requireRoles` ném lỗi sau khi chuyển hướng → "Uncaught Error: Forbidden" trong console [Đã tái hiện].
- `my-orders.html` còn dòng chữ nhắc "gọi món qua QR" [Đã tái hiện].
- Nút "Giỏ" hiện cho cả KITCHEN/STAFF [Đã tái hiện].
- Staff tạo đơn chỉ chọn được 1 món mỗi lần; chỉ thu tiền mặt dù backend nhận `BANK_TRANSFER`.
- `/auth/me` bị gọi hai lần mỗi trang (`mountAppNav` + `requireRoles`).

---

## D. Đang chạy đúng — đừng làm hỏng

- Tính tiền: 155.000 − 10% = 139.500; thuế 8% = 11.160; tổng 150.660 — khớp ở đơn, thanh toán, hoá đơn. Thêm/sửa/xoá món tính lại đúng.
- Thanh toán: sai số tiền, trả hai lần, khách tự thanh toán đều bị chặn; gửi lại cùng `Idempotency-Key` không tạo trùng.
- Phân quyền bốn vai đúng; đơn, lượt đặt, hoá đơn của người khác trả 403.
- Refresh token xoay vòng, token cũ bị từ chối; đăng xuất thu hồi refresh token.
- Chống đặt trùng bàn (khoá dòng bàn trước khi kiểm tra chồng giờ); giới hạn 180 phút, giờ quá khứ, sức chứa.
- Khoá khi tạo đơn, thanh toán, dùng mã giảm giá; sự kiện bếp phát sau commit; một phiên mở mỗi bàn được ràng buộc ở DB.
- 21 trang FE tải đủ, không lỗi JS ngoài mục đã nêu.

## E. Dữ liệu test còn trong `rms_db`

- Tài khoản khách `claude-test-…@example.com`, `claude-test2-…@example.com` (id 8, 9).
- Lượt đặt bàn id 5, 6 (đã huỷ), 7 (hoàn tất); phiên bàn 7–9 đã đóng.
- Đơn id 3 đã thanh toán 150.660đ; đơn id 4 `COMPLETED` không có thanh toán (minh hoạ B2); đơn id 5–9 đã huỷ.
- Mã giảm giá `CLAUDETEST10` đã tắt, `usedCount = 2` (minh hoạ B7).
- Từ lần kiểm A1 (2026-11-12): tài khoản khách `a1-verify-…@example.com` (id 10); phiên bàn 10 (T04, đã đóng); đơn id 10 (đã huỷ).
- Từ lần kiểm C4 (2026-11-17): phiên bàn 25, 26 trên T04 (đã đóng); đơn id 25 (đã huỷ), 26 và 27 đã thanh toán tiền mặt 48.600đ mỗi đơn (cộng vào doanh thu 16–17/11); giá Pepsi đổi 20.000 → 22.000 rồi trả về 20.000 (2 dòng audit); T01 ra/vào `OUT_OF_SERVICE` (2 dòng audit); mọi thông báo của staff1 đã được đánh dấu đã đọc.
- Từ lần kiểm C5 (2026-11-17): phiên bàn 27 trên T04 (đã đóng); đơn id 28 (Cơm gà ×3, Gỏi cuốn ×1) đã thanh toán chuyển khoản 226.800đ, mã `VCB-C5-TEST-001` (payment id 10, cộng vào doanh thu 17/11).
- Từ lần kiểm C3 (2026-11-13): phiên bàn 24 trên T04 (đã đóng); đơn id 24 đã thanh toán tiền mặt 48.600đ (cộng vào doanh thu 13/11). Schema `rms_db` đã lên V15. Hai lệnh `DELETE` thử nghiệm trên đơn #3 và #5 đều chạy trong transaction và đã rollback.
- Từ lần kiểm C1 (2026-11-13): phiên bàn 23 trên T04 (đã đóng); đơn id 23 (đã huỷ). Schema `rms_db` đã lên V14.
- Từ lần kiểm B11 (2026-11-13): tài khoản STAFF `b11-staff-…@example.com` (id 15, **đã khoá**); tài khoản khách `b11-verify-…@example.com`; mã giảm giá `B11ONCE…` (id 3, đã tắt, `usedCount = 0`); lượt đặt id 15 (hoàn tất); phiên bàn 21 (T03), 22 (T04) đã đóng; đơn id 19–22 (đã huỷ). Món Pepsi được gắn ảnh test rồi trả về không có ảnh; file ảnh test đã xoá.
- Từ lần kiểm B10 (2026-11-12): tài khoản khách `b10-verify-…@example.com` (id 14); lượt đặt id 12 (tự `CANCELLED`), 13 và 14 (`NO_SHOW`) — `reservation_start`/`reservation_end` của ba dòng này đã bị sửa tay trong DB để giả lập quá giờ.
- Từ lần kiểm B9 (2026-11-12): tài khoản khách `b9-verify-…@example.com` (id 13), đã đổi mật khẩu một lần; 3 dòng `refresh_tokens` (2 đã thu hồi).
- Từ lần kiểm B7 (2026-11-12): mã giảm giá `B7VERIFY…` (id 2, đã tắt, `usedCount = 0`); phiên bàn 20 trên T04 (đã đóng); đơn id 18 (đã huỷ, có dòng audit `ORDER_CANCELLED`).
- Từ lần kiểm B6 (2026-11-12): phiên bàn 19 trên T04 (đã đóng); đơn id 16 đã thanh toán tiền mặt 108.000đ (cộng vào doanh thu 12/11); đơn id 17 (đã huỷ).
- Từ lần kiểm B5 (2026-11-12): tài khoản khách `b5-verify-…@example.com`; lượt đặt id 10 (T03), 11 (T02) cho tuần sau — đã huỷ; phiên bàn 18 trên T03 (đã đóng).
- Từ lần kiểm B4 (2026-11-12): phiên bàn 16, 17 trên T04 (đã đóng); đơn id 14 đã thanh toán tiền mặt 48.600đ (cộng vào doanh thu 12/11); đơn id 15 (đã huỷ).
- Từ lần kiểm B3 (2026-11-12): tài khoản khách `b3-verify-…@example.com` (id 11); lượt đặt id 8, 9 (hoàn tất); phiên bàn 13–15 trên T03 (đã đóng); đơn id 13 (đã huỷ). Schema `rms_db` đã lên V13.
- Từ lần kiểm B2 (2026-11-12): phiên bàn 12 (T04, đã đóng); đơn id 12 đã thanh toán tiền mặt 48.600đ (có hoá đơn) — cộng vào doanh thu ngày 12/11.
- Từ lần kiểm A2 (2026-11-12): phiên bàn 11 (T04, đã đóng); đơn id 11 (đã huỷ). Mọi cột DB tự điền của dữ liệu cũ đã được dịch −7 tiếng về UTC.

## F. Thứ tự đề xuất

1. A1, A2 (kèm dashboard), A3.
2. B1, B2.
3. B3, B4.
4. B5, B6, B7.
5. B8–B11.
6. C2 làm song song: mỗi mục B sửa kèm test.
7. C1, C3–C5.
