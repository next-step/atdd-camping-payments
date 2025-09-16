package com.camping.payments.api;

import com.camping.payments.core.Payment;
import com.camping.payments.core.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/v1/payments")
public class PaymentsController {

    private final PaymentService service;

    public PaymentsController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody @Validated CreateRequest request) {
        Payment p = service.create(request.paymentKey(), request.orderId(), request.amount());
        return ResponseEntity.ok(Map.of(
                "paymentKey", p.getPaymentKey(),
                "orderId", p.getOrderId(),
                "status", p.getStatus().name()
        ));
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(@RequestBody @Validated ConfirmRequest request) {
        Payment p = service.confirm(request.paymentKey(), request.orderId(), request.amount());
        return ResponseEntity.ok(Map.of(
                "paymentKey", p.getPaymentKey(),
                "orderId", p.getOrderId(),
                "method", p.getMethod(),
                "approvedAt", DateTimeFormatter.ISO_INSTANT.format(p.getApprovedAt()),
                "totalAmount", p.getAmount(),
                "status", "APPROVED",
                "receipt", Map.of("url", p.getReceiptUrl())
        ));
    }

    @PostMapping("/{paymentKey}/cancel")
    public ResponseEntity<?> cancel(@PathVariable String paymentKey, @RequestBody @Validated CancelRequest request) {
        Payment p = service.cancel(paymentKey, request.cancelReason(), request.cancelAmount());
        return ResponseEntity.ok(Map.of(
                "status", "CANCELED",
                "canceledAt", DateTimeFormatter.ISO_INSTANT.format(p.getCanceledAt())
        ));
    }

    public record CreateRequest(String paymentKey, String orderId, Integer amount) {}
    public record ConfirmRequest(String paymentKey, String orderId, Integer amount) {}
    public record CancelRequest(String cancelReason, Integer cancelAmount) {}
}


