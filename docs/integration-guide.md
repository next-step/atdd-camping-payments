## 결제 연동 가이드

본 문서는 가맹점(키오스크 백엔드)이 본 결제 모듈(atdd-payments)과 연동하는 방법을 설명합니다. 

### 전체 흐름 한눈에 보기

1) 결제 생성: 가맹점 서버가 결제건을 생성합니다.
2) 결제 승인: 결제 성공 후(또는 모의 성공) 가맹점 서버가 승인(confirm)을 요청합니다.
3) 환불(취소): 필요 시 결제를 전액 취소합니다.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client (Kiosk UI)
    participant M as Merchant (Kiosk Backend)
    participant P as Payments (this service)

    C->>M: Checkout 시작 (장바구니 등)
    M->>P: POST /v1/payments (paymentKey, orderId, amount)
    P-->>M: 200 INITIATED
    Note over C,M: (실서비스: PG 결제창 진행 / 본 예제: 모의)
    M->>P: POST /v1/payments/confirm (paymentKey, orderId, amount)
    P-->>M: 200 APPROVED + 영수증 URL
    M->>M: 판매/재고 확정 및 영수증 저장
    M->>P: (필요 시) POST /v1/payments/{paymentKey}/cancel
```

---

### 인증
- 방식: Basic 인증
- 헤더: `Authorization: Basic base64("<secret-key>:")`
- 구성 값: `payments.secret-key` (결제 모듈)와 가맹점에서 사용하는 값이 일치해야 합니다.

예시: `test_sk_dummy:` → Base64 → `dGVzdF9za19kdW1teTo=`

---

### 1) 결제 생성 (Create)
- 엔드포인트: `POST /v1/payments`
- 목적: 결제건 사전 생성 (멱등)
- 요청 본문(JSON):

```json
{
  "paymentKey": "pay_123",
  "orderId": "order_123",
  "amount": 10000
}
```

- 필드 설명:
  - paymentKey: 결제 고유 키(가맹점 생성)
  - orderId: 가맹점 주문번호
  - amount: 결제 총 금액(정수, KRW)

- 응답 200(JSON):

```json
{ "paymentKey": "pay_123", "orderId": "order_123", "status": "INITIATED" }
```

- 특이사항:
  - 동일 `paymentKey` 존재 시 기존 건을 반환(멱등)

- cURL 예시:

```bash
curl -X POST "http://localhost:9090/v1/payments" \
  -H "Authorization: Basic dGVzdF9za19kdW1teTo=" \
  -H "Content-Type: application/json" \
  -d '{
    "paymentKey":"pay_123",
    "orderId":"order_123",
    "amount":10000
  }'
```

---

### 2) 가맹점 결제 승인 (Confirm)
- 엔드포인트: `POST /v1/payments/confirm`
- 목적: 결제 승인 처리
- 요청 본문(JSON):

```json
{ "paymentKey": "pay_123", "orderId": "order_123", "amount": 10000 }
```

- 응답 200(JSON):

```json
{
  "paymentKey": "pay_123",
  "orderId": "order_123",
  "method": "CARD",
  "approvedAt": "2024-01-01T12:34:56Z",
  "totalAmount": 10000,
  "status": "APPROVED",
  "receipt": { "url": "https://pay.local/receipts/pay_123" }
}
```

- 에러(대표):
  - 409 AMOUNT_MISMATCH: 동일 paymentKey로 다른 amount/orderId
  - 422 ALREADY_CANCELED: 이미 취소된 거래 재승인 시도
  - 400 INVALID_REQUEST: 필수 필드 누락/검증 실패

---

### 3) 결제 결과 콜백 (제외)
본 난이도 단순화 버전에서는 콜백을 사용하지 않습니다. 승인 API 응답으로 승인이 즉시 동기 반환됩니다.

---

### 4) 결제 환불(취소)
- 엔드포인트: `POST /v1/payments/{paymentKey}/cancel`
- 목적: 전액 취소 (부분취소 미지원)
- 요청 본문(JSON):

```json
{ "cancelReason": "USER_REQUEST", "cancelAmount": 10000 }
```

- 응답 200(JSON):

```json
{ "status": "CANCELED", "canceledAt": "2024-01-01T12:40:00Z" }
```

- 에러(대표):
  - 404 NOT_FOUND: 존재하지 않는 paymentKey
  - 422 NOT_APPROVED: 승인 전 취소 요청
  - 409 PARTIAL_MISMATCH: 부분취소 금액 불일치(미지원)

---

### 상태/멱등성
- 상태: INITIATED → APPROVED → (취소 시) CANCELED
- 멱등:
  - Create: 동일 paymentKey 재요청 시 기존 건 반환
  - Confirm: 동일 파라미터 재호출 시 승인 정보 재응답

---

### 연동 체크리스트
- [ ] payments.secret-key 일치(Basic 인증)
- [ ] paymentKey/orderId/amount 생성 로직(유일성 보장)
- [ ] callbackUrl 준비 및 방화벽 설정
- [ ] 승인 성공 이후 비즈니스 확정(주문/재고/영수증)
- [ ] 취소 플로우(전액) 준비


