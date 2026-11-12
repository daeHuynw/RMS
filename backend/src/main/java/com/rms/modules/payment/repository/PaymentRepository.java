package com.rms.modules.payment.repository;

import com.rms.modules.payment.entity.Payment;
import com.rms.modules.payment.entity.PaymentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);
}
