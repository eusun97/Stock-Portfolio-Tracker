# 거래내역 API 설계

## 작업 범위

거래내역(매수/매도) 한 건의 생애주기를 책임지는 REST API. 거래 등록 → 조회 → 수정 → 삭제까지 5개 엔드포인트와, 그 등록·수정·삭제 트랜잭션 안에서 일어나는 보유종목·실현손익 정합성 처리.

## 엔드포인트

| Method | Path | 설명 |
| --- | --- | --- |
| `POST` | `/api/trades` | 거래 등록 (BUY 또는 SELL) |
| `GET` | `/api/trades` | 거래 목록 조회 (페이징) |
| `GET` | `/api/trades/{id}` | 거래 단건 조회 |
| `PATCH` | `/api/trades/{id}` | 거래 수정 |
| `DELETE` | `/api/trades/{id}` | 거래 삭제 (soft delete) |

## 요청/응답 구조

### 공통 응답 envelope

성공/실패에 같은 envelope 적용

```json
{
  "success": true,
  "data": { ... },
  "message": null,
  "timestamp": "2026-06-17T10:00:00"
}
```

`@JsonInclude(NON_NULL)`로 null 필드 자동 제거. 클라이언트 페이로드 절감.

### 페이지 응답

Spring `Page<T>`를 그대로 직렬화하지 않고 `PageResponse<T>`로 감쌈. envelope 와 자연스럽게 합쳐지고, Spring 측의 verbose 한 페이지 메타데이터(`pageable`, `sort`)를 노출하지 않음.

```json
{
  "content": [...],
  "page": 0,
  "size": 10,
  "totalElements": 23,
  "totalPages": 3,
  "first": true,
  "last": false
}
```

## 트랜잭션 설계

### 거래 등록 (단일 트랜잭션)

```
@Transactional
register(request)
  └─ trade INSERT
  ├─ if BUY  → holding upsert (있으면 평균가 갱신, 없으면 신규)
  └─ if SELL → holding 비관적 락 → 부족 검증 → realized_profit INSERT → holding UPDATE/DELETE
```

핵심:

- 매수/매도 모두 **단일 `@Transactional` 메서드** 안에서 처리. 중간 단계 실패 시 trade INSERT 까지 롤백
- 매수도 비관적 락 경유. 동일 종목 동시 매수 시 평균가 Lost Update 차단
- 매도 시점에 **그 시점의 평균 매입가를 `realized_profit` 에 박제**. 이후 거래 수정으로 평균가가 흔들려도 손익 근거는 보존

### 매수 / 매도 로직

```
매수
  new_avg = (old_avg × old_qty + buy_price × buy_qty) / (old_qty + buy_qty)
  new_qty = old_qty + buy_qty

매도
  profit = (sell_price - avg_buy_price) × sell_qty
  new_qty = old_qty - sell_qty
  if new_qty == 0 → holding row DELETE
```

매도 시 평균 매입가는 변하지 않음. 실현손익 한 건이 추가될 뿐.

### 비관적 락 적용 위치

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT h FROM Holding h WHERE h.ticker = :ticker")
Optional<Holding> findByTickerForUpdate(@Param("ticker") String ticker);
```

- Repository 메서드 어노테이션 방식. 메서드명에 `ForUpdate` 명시해 호출부에서 락 의도가 보임
- 신규 종목(holding 없음) 동시 매수 → 락 자체는 안 걸림. 하지만 holding PK 가 ticker 라 두 번째 INSERT 가 PK 충돌 → 트랜잭션 롤백. **DB가 마지막 안전장치**

## 거래 수정 / 삭제 — 종목 단위 리플레이

수정·삭제는 그 종목의 모든 살아있는 거래내역을 시계열로 다시 재생.

```
recalculateHolding(ticker)
  ├─ 해당 종목의 holding row 삭제
  ├─ 해당 종목의 realized_profit 전부 삭제
  ├─ flush()  ← JPA 영속성 컨텍스트의 DELETE 강제 즉시 반영
  ├─ trade WHERE deleted_at IS NULL ORDER BY traded_at, id 조회
  └─ 거래 한 건씩 매수/매도 분기로 다시 적용
       (replayBuy / replaySell)
```

채택 이유

- delta 계산은 매수/매도 순서에 따라 평균가가 어긋날 위험. 시계열 리플레이는 단순하고 안전
- 한 번에 모든 거래를 다시 도는 비용은 종목 단위라 작음. 거래내역이 수천 건 이상이면 그 시점에 별도 최적화 검토

`flush()` 가 필요한 이유: JPA 의 dirty checking 은 트랜잭션 커밋 시점에 모아서 SQL 을 보냄. `delete()` 호출 직후 `save()` 를 부르면 DELETE 가 아직 안 나간 상태에서 INSERT 가 시도되어 PK 충돌. `flush()` 로 강제 동기화.

## DTO 와 Bean Validation

`record` 기반 불변 DTO. 검증은 어노테이션으로 선언적으로

```java
public record TradeRegisterRequest(
    @NotNull @Pattern(regexp = "\\d{6}") String ticker,
    @NotNull TxType txType,
    @NotNull @DecimalMin("0.0001") BigDecimal quantity,
    @NotNull @DecimalMin("0") BigDecimal price,
    @NotNull LocalDate tradedAt
) {}
```

Controller 에서 `@Valid` 한 줄로 검증 트리거. 위반 시 `MethodArgumentNotValidException` → 5단계 GlobalExceptionHandler 에서 400 응답으로 통일 변환.

## 페이징

| 항목 | 값 | 비고 |
| --- | --- | --- |
| 기본 size | 10 | yml `default-page-size` |
| 최대 size | 100 | yml `max-page-size`. 과도한 size 차단 |
| 기본 정렬 | `tradedAt DESC, id DESC` | Controller `@PageableDefault` |
| page 시작 | 0 | yml `one-indexed-parameters: false` |

## API 문서 — Swagger

- `springdoc-openapi-starter-webmvc-ui` 적용
- Controller 에 `@Tag` / `@Operation` 으로 명세 작성
- prod profile 에서는 `springdoc.api-docs.enabled: false` 로 차단

| 경로 | 용도 |
| --- | --- |
| `/swagger-ui.html` | 개발 중 대화형 API 테스트 |
| `/api-docs` | OpenAPI 3.0 JSON 명세 (도구 import 용) |

## 검증 시나리오

| 시나리오 | 기대 |
| --- | --- |
| 신규 종목 매수 | trade 1건, holding 1건 신규 |
| 같은 종목 추가 매수 | trade 1건 추가, holding 평균가 갱신 |
| 보유 종목 일부 매도 | trade 1건, realized_profit 1건, holding 수량 차감 |
| 보유 종목 전량 매도 | trade 1건, realized_profit 1건, holding row 삭제 |
| 보유 없는 종목 매도 | 트랜잭션 롤백 (`IllegalStateException`) |
| 보유 수량 부족 매도 | 트랜잭션 롤백 (`IllegalStateException`) |
| 매수 거래 수정 | 종목 단위 holding/realized_profit 재계산 |
| 매도 거래 삭제 | soft delete + 종목 단위 재계산 |

## 다음 단계로 넘기는 항목

- `IllegalArgumentException`, `IllegalStateException` 의 통일된 응답 변환은 5단계 GlobalExceptionHandler 에서 처리
- 종목 마스터(`stock`) lazy 등록은 5단계에서 외부 시세 API 도입 후 적용
- 평가손익·총수익률·일간 스냅샷은 5단계 포트폴리오 계산 로직에서 구현
