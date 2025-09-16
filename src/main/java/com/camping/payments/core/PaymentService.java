package com.camping.payments.core;

import com.camping.payments.infra.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

@Service
public class PaymentService {

    private final PaymentRepository repository;
    private final String receiptBaseUrl;

    public PaymentService(PaymentRepository repository,
                          @Value("${payments.receipt.base-url:https://pay.local/receipts}") String receiptBaseUrl) {
        this.repository = repository;
        this.receiptBaseUrl = receiptBaseUrl;
    }

    public Payment create(String paymentKey, String orderId, int amount) {
        return repository.findByKey(paymentKey).orElseGet(() -> {
            Payment p = new Payment(paymentKey, orderId, amount);
            repository.save(p);
            return p;
        });
    }

    public Payment confirm(String paymentKey, String orderId, int amount) {
        return repository.findByKey(paymentKey).map(existing -> {
            // 멱등 처리: 동일 파라미터면 기존 승인 응답, 다르면 충돌
            if (existing.getAmount() != amount || !existing.getOrderId().equals(orderId)) {
                throw new PaymentException(409, "AMOUNT_MISMATCH", "Different amount or orderId for same paymentKey");
            }
            if (existing.getStatus() == PaymentStatus.CANCELED) {
                throw new PaymentException(422, "ALREADY_CANCELED", "Payment already canceled");
            }
            if (existing.getStatus() == PaymentStatus.APPROVED) {
                return existing; // 멱등 응답
            }
            // INITIATED/FAILED 등은 승인 처리 재시도 허용
            approve(existing);
            return existing;
        }).orElseGet(() -> {
            // 신규 생성 후 승인
            Payment p = new Payment(paymentKey, orderId, amount);
            approve(p);
            repository.save(p);
            return p;
        });
    }

    public Payment cancel(String paymentKey, String reason, Integer cancelAmount) {
        Payment p = repository.findByKey(paymentKey)
                .orElseThrow(() -> new PaymentException(404, "NOT_FOUND", "Payment not found"));
        if (p.getStatus() == PaymentStatus.CANCELED) {
            return p; // 멱등 취소 응답
        }
        if (p.getStatus() != PaymentStatus.APPROVED) {
            throw new PaymentException(422, "NOT_APPROVED", "Payment not approved");
        }
        if (cancelAmount != null && cancelAmount != p.getAmount()) {
            throw new PaymentException(409, "PARTIAL_MISMATCH", "Partial cancel not supported");
        }
        p.cancel(reason, p.getAmount(), Instant.now());
        repository.save(p);
        return p;
    }

    private void approve(Payment p) {
        Instant now = Instant.now();
        String receipt = receiptBaseUrl + "/" + p.getPaymentKey();
        p.approve("CARD", receipt, now);
        repository.save(p);
    }

    public static class PaymentException extends RuntimeException {
        private final int httpStatus;
        private final String code;
        public PaymentException(int httpStatus, String code, String message) {
            super(message);
            this.httpStatus = httpStatus;
            this.code = code;
        }
        public int getHttpStatus() { return httpStatus; }
        public String getCode() { return code; }
    }
}


