package com.camping.payments.core;

import java.time.Instant;
import java.util.Objects;

public class Payment {
    private final String paymentKey;
    private final String orderId;
    private final int amount;

    private PaymentStatus status;
    private String method;
    private Instant approvedAt;
    private String receiptUrl;
    private Instant canceledAt;
    private Integer canceledAmount;
    private String cancelReason;
    private final Instant createdAt;
    private Instant updatedAt;

    public Payment(String paymentKey, String orderId, int amount) {
        this.paymentKey = Objects.requireNonNull(paymentKey);
        this.orderId = Objects.requireNonNull(orderId);
        this.amount = amount;
        this.status = PaymentStatus.INITIATED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getPaymentKey() { return paymentKey; }
    public String getOrderId() { return orderId; }
    public int getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public String getMethod() { return method; }
    public Instant getApprovedAt() { return approvedAt; }
    public String getReceiptUrl() { return receiptUrl; }
    public Instant getCanceledAt() { return canceledAt; }
    public Integer getCanceledAmount() { return canceledAmount; }
    public String getCancelReason() { return cancelReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void approve(String method, String receiptUrl, Instant approvedAt) {
        this.status = PaymentStatus.APPROVED;
        this.method = method;
        this.receiptUrl = receiptUrl;
        this.approvedAt = approvedAt;
        this.updatedAt = Instant.now();
    }

    public void cancel(String reason, Integer amount, Instant canceledAt) {
        this.status = PaymentStatus.CANCELED;
        this.cancelReason = reason;
        this.canceledAmount = amount;
        this.canceledAt = canceledAt;
        this.updatedAt = Instant.now();
    }

}


