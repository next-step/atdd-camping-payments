package com.camping.payments.infra;

import com.camping.payments.core.Payment;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryPaymentRepository implements PaymentRepository {
    private final Map<String, Payment> storage = new ConcurrentHashMap<>();

    @Override
    public Optional<Payment> findByKey(String paymentKey) {
        return Optional.ofNullable(storage.get(paymentKey));
    }

    @Override
    public Payment save(Payment payment) {
        storage.put(payment.getPaymentKey(), payment);
        return payment;
    }
}


