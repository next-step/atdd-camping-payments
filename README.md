## 결제 모듈 가이드 (atdd-payments)

본 문서는 결제 프로세스 개요, API 명세, 설정 방법, 오류 코드, 연동 예시(HTTP/Java)를 포함합니다. 키오스크(`atdd-camping-kiosk`) 등 외부 클라이언트가 본 모듈을 결제 제공자(에뮬레이터)로 사용하도록 돕습니다.

### 개요
- 핵심 엔드포인트:
  - 생성(create): `POST /v1/payments`
  - 승인(confirm): `POST /v1/payments/confirm`
  - 취소(cancel): `POST /v1/payments/{paymentKey}/cancel`
- 인증: Basic 인증. 헤더 `Authorization: Basic base64("<secret-key>:")`
- 멱등성: 같은 `paymentKey`에 대해 동일 파라미터면 승인 응답을 재사용합니다.

### 실행 및 설정
- 기본 포트: `9090`
- 주요 설정(`src/main/resources/application.yml`):

```yaml
payments:
  secret-key: test_sk_dummy
  receipt:
    base-url: https://pay.local/receipts
server:
  port: 9090
```

- 비밀키 주입 예시
  - zsh: `export PAYMENTS_SECRET_KEY=your_secret` 후, `-Dpayments.secret-key=your_secret` 또는 yml 값 변경
  - JVM: `-Dpayments.secret-key=your_secret`

### 인증 규칙(BasicAuth)
- 요청 헤더 예: `Authorization: Basic dGVzdF9za19kdW1teTo=` (`test_sk_dummy:` base64)
- 키 불일치/누락 시 401 응답: `{ "code": "UNAUTHORIZED", "message": "Invalid secret key" }`

### 결제 생성 API: POST /v1/payments
- 목적: 결제 생성 및 콜백 URL 등록[`docs`](https://docs-pay.toss.im/reference/normal/create)
- 요청 Body

```json
{ "paymentKey": "pay_123", "orderId": "order_123", "amount": 10000, "callbackUrl": "https://merchant.local/callback" }
```

- 응답 200

```json
{ "paymentKey": "pay_123", "orderId": "order_123", "status": "INITIATED" }
```

- 비고: 동일 `paymentKey` 존재 시 기존 건을 반환합니다(멱등 생성)

### 승인 API: POST /v1/payments/confirm
- 요청 Body

```json
{ "paymentKey": "pay_123", "orderId": "order_123", "amount": 10000 }
```

- 응답 200

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

- 오류
  - 409 AMOUNT_MISMATCH: 동일 `paymentKey`로 다른 `amount`/`orderId`
  - 422 ALREADY_CANCELED: 이미 취소된 결제 승인 시도
  - 400 INVALID_REQUEST: 필드 누락/검증 실패
  - 500 PROVIDER_ERROR: 기타 내부 오류

### 취소 API: POST /v1/payments/{paymentKey}/cancel
- 요청 Body

```json
{ "cancelReason": "USER_REQUEST", "cancelAmount": 10000 }
```

- 응답 200

```json
{ "status": "CANCELED", "canceledAt": "2024-01-01T12:40:00Z" }
```

- 오류
  - 404 NOT_FOUND: 존재하지 않는 `paymentKey`
  - 422 NOT_APPROVED: 승인 전 취소 요청
  - 409 PARTIAL_MISMATCH: 부분취소 미지원(요청 금액이 승인 금액과 다름)

### 오류 응답 포맷

```json
{ "code": "ERROR_CODE", "message": "사유" }
```

### 결제 결과 콜백(resultCallback)
본 단순화 모드에서는 콜백을 사용하지 않습니다. 승인 API가 동기 응답으로 결과를 반환합니다.

### 연동 시퀀스
1) 서버가 `POST /v1/payments`로 결제건 생성(선택) 및 callbackUrl 등록
2) 클라이언트/PG 통해 `paymentKey`, `orderId`, `amount` 확보 후 서버가 `POST /v1/payments/confirm` 호출(승인)
3) 승인 성공 시 비즈니스 확정(판매/재고 반영)
4) 실패 시 사용자 안내 및 재시도
5) 환불/롤백은 `POST /v1/payments/{paymentKey}/cancel`

### HTTP 예시

```http
POST http://localhost:9090/v1/payments/confirm
Authorization: Basic dGVzdF9za19kdW1teTo=
Content-Type: application/json

{ "paymentKey": "pay_123", "orderId": "order_123", "amount": 10000 }
```

```http
POST http://localhost:9090/v1/payments/pay_123/cancel
Authorization: Basic dGVzdF9za19kdW1teTo=
Content-Type: application/json

{ "cancelReason": "USER_REQUEST", "cancelAmount": 10000 }
```

### Java 예시(RestTemplate)

```java
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_JSON);
headers.set("Authorization", "Basic " + Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8)));

var confirmBody = Map.of("paymentKey","pay_123","orderId","order_123","amount",10000);
var confirmRes = restTemplate.exchange(baseUrl+"/v1/payments/confirm", HttpMethod.POST, new HttpEntity<>(confirmBody, headers), Map.class);

var cancelBody = Map.of("cancelReason","USER_REQUEST","cancelAmount",10000);
var cancelRes = restTemplate.exchange(baseUrl+"/v1/payments/pay_123/cancel", HttpMethod.POST, new HttpEntity<>(cancelBody, headers), Map.class);
```

### 키오스크 연동 팁
- 키오스크 설정 예시(`atdd-camping-kiosk/src/main/resources/application.yml`)

```yaml
kiosk:
  payment:
    base-url: http://localhost:9090
    secret-key: ${PAYMENTS_SECRET_KEY:test_sk_dummy}
```

- 승인 호출 바디는 `{ paymentKey, orderId, amount }` 형식 준수
- 승인 성공 후에만 관리자 서비스에 판매 확정 호출

### 로컬 구동

```bash
cd atdd-payments
./gradlew bootRun
```

### 제한 사항
- 부분취소 미지원(전액취소만)
- 결제수단 고정(CARD)
- 영수증 URL은 설정 기반 가짜 URL

### 변경 이력
- v0.1: 초기 버전 공개


