package com.camping.payments.infra;

import com.camping.payments.core.Payment;

import java.util.Optional;

public interface PaymentRepository {
    Optional<Payment> findByKey(String paymentKey);
    Payment save(Payment payment);
}


