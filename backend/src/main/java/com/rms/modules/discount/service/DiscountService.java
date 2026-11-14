package com.rms.modules.discount.service;

import com.rms.exception.ApiException;
import com.rms.modules.audit.service.AuditService;
import com.rms.modules.discount.dto.DiscountDtos.Quote;
import com.rms.modules.discount.dto.DiscountDtos.Request;
import com.rms.modules.discount.dto.DiscountDtos.Response;
import com.rms.modules.discount.entity.Discount;
import com.rms.modules.discount.repository.DiscountRepository;
import com.rms.modules.discount.repository.OrderDiscountRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiscountService {

    private final DiscountRepository discounts;
    private final OrderDiscountRepository orderDiscounts;
    private final AuditService audit;

    public DiscountService(
            DiscountRepository discounts, OrderDiscountRepository orderDiscounts, AuditService audit) {
        this.discounts = discounts;
        this.orderDiscounts = orderDiscounts;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Response> list() {
        return discounts.findAllByOrderByCreatedAtDesc().stream().map(Response::from).toList();
    }

    @Transactional
    public Response create(Request request) {
        validate(request);
        String code = normalize(request.code());
        if (discounts.findByCode(code).isPresent()) {
            throw ApiException.conflict("DISCOUNT_CODE_EXISTS", "Discount code already exists");
        }
        Discount saved = discounts.saveAndFlush(new Discount(
                code,
                blankToNull(request.description()),
                request.type(),
                request.value(),
                request.minimumOrderAmount(),
                request.maximumDiscountAmount(),
                request.usageLimit(),
                request.startAt(),
                request.endAt()));
        audit.record("DISCOUNT_CREATED", "DISCOUNT", String.valueOf(saved.getId()), code);
        return Response.from(saved);
    }

    @Transactional
    public Response update(Long id, Request request) {
        validate(request);
        Discount discount = discounts.lockById(id).orElseThrow(() -> ApiException.notFound("Discount not found"));
        String code = normalize(request.code());
        if (!discount.getCode().equals(code) && discounts.findByCode(code).isPresent()) {
            throw ApiException.conflict("DISCOUNT_CODE_EXISTS", "Discount code already exists");
        }
        discount.update(
                code,
                blankToNull(request.description()),
                request.type(),
                request.value(),
                request.minimumOrderAmount(),
                request.maximumDiscountAmount(),
                request.usageLimit(),
                request.startAt(),
                request.endAt());
        Discount saved = discounts.saveAndFlush(discount);
        audit.record("DISCOUNT_UPDATED", "DISCOUNT", String.valueOf(saved.getId()), code);
        return Response.from(saved);
    }

    @Transactional
    public Response deactivate(Long id) {
        Discount discount = discounts.findById(id).orElseThrow(() -> ApiException.notFound("Discount not found"));
        if (discount.isActive()) {
            discount.deactivate();
        } else {
            discount.activate();
        }
        audit.record(
                discount.isActive() ? "DISCOUNT_ACTIVATED" : "DISCOUNT_DEACTIVATED",
                "DISCOUNT",
                String.valueOf(discount.getId()),
                discount.getCode());
        return Response.from(discount);
    }

    @Transactional(readOnly = true)
    public Quote validateCode(String code, BigDecimal subtotal) {
        Discount discount = discounts.findByCode(normalize(code))
                .orElseThrow(() -> ApiException.badRequest("INVALID_DISCOUNT", "Discount code is invalid"));
        if (!discount.canApply(Instant.now(), subtotal)) {
            throw ApiException.badRequest(
                    "INVALID_DISCOUNT",
                    "Discount code is inactive, expired, exhausted, or order minimum is not met");
        }
        return new Quote(discount.getCode(), discount.amount(subtotal));
    }

    /** Đơn dùng mã bị huỷ: trả lại lượt. Khoá dòng mã như lúc consume để không mất cập nhật. */
    @Transactional
    public void release(Long discountId) {
        discounts.lockById(discountId).ifPresent(Discount::release);
    }

    @Transactional
    public Discount lockApplicable(String code, BigDecimal subtotal, Long customerId) {
        Discount discount = discounts.lockByCode(normalize(code))
                .orElseThrow(() -> ApiException.badRequest("INVALID_DISCOUNT", "Discount code is invalid"));
        if (!discount.canApply(Instant.now(), subtotal)) {
            throw ApiException.badRequest(
                    "INVALID_DISCOUNT",
                    "Discount code is inactive, expired, exhausted, or order minimum is not met");
        }
        // Mỗi tài khoản khách dùng mỗi mã một lần (đơn đã huỷ không tính). customerId == null là đơn
        // staff tạo cho khách vãng lai. Dòng mã đã bị khoá ở trên nên hai đơn cùng lúc phải chờ nhau.
        if (customerId != null && orderDiscounts.existsActiveUseByCustomer(discount.getId(), customerId)) {
            throw ApiException.conflict("DISCOUNT_ALREADY_USED", "You have already used this discount code");
        }
        return discount;
    }

    private static void validate(Request request) {
        if (!request.endAt().isAfter(request.startAt())) {
            throw ApiException.badRequest("INVALID_DISCOUNT_DATES", "End date must be after start date");
        }
        if (request.type() == Discount.Type.PERCENTAGE && request.value().compareTo(new BigDecimal("100")) > 0) {
            throw ApiException.badRequest("INVALID_DISCOUNT_VALUE", "Percentage cannot exceed 100");
        }
        if (request.usageLimit() != null && request.usageLimit() < 1) {
            throw ApiException.badRequest("INVALID_USAGE_LIMIT", "Usage limit must be at least 1");
        }
    }

    private static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
