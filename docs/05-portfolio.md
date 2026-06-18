# 5단계 — 포트폴리오 계산 로직

## 작업 범위

- 외부 시세 API 통합 (공공데이터포털)
- 종목 마스터 lazy 등록
- 평가손익 / 수익률 계산
- 표준 예외 처리 (`GlobalExceptionHandler` + 도메인 예외 계층)
- 포트폴리오 조회 API (`GET /api/portfolio`)

## 외부 API 통합 — Port–Adapter + Resilience4j

도메인은 외부 API 의 형태를 모른다. 인터페이스만 의존.

```
service/PortfolioService
        ↓
infrastructure/client/StockPriceClient    (Port — 도메인 어휘)
        ↑
infrastructure/client/publicdata/
   ├─ PublicDataStockPriceClient           (Adapter)
   ├─ PublicDataResponse                   (외부 응답 DTO)
   └─ PublicDataProperties                 (base-url / serviceKey / timeout)
```

추후 KIS 등으로 교체 시 Adapter 클래스 하나만 추가, 도메인 코드는 변경 없음.

### Resilience4j 적용

| 구성 | 값 | 의도 |
| --- | --- | --- |
| Circuit Breaker `publicData` | sliding-window=20, failure-rate=50%, open=30s | 일시 장애 시 빠르게 차단, 30초 후 half-open 으로 점진 복구 |
| Retry `publicData` | max=3, base=500ms, multiplier=2 | 일시적 네트워크/서버 흔들림 흡수, 지수 백오프로 폭주 방지 |
| Timeout | connect=2s, read=3s | 슬로우 응답이 호출 스레드를 잡고 있는 시간 제한 |
| Ignore | `StockNotFoundException` | 데이터 부재는 외부 장애가 아님. 재시도/회로 차단 대상에서 제외 |

`@CircuitBreaker(fallbackMethod=...)` + `@Retry` 애너테이션으로 Adapter 메서드 1개에 정책 모두 적용.

### Fallback 정책

폴백은 실패를 숨기지 않음. `ExternalApiException` 으로 변환만 수행, 호출자는 명확한 503 응답을 받음. 캐시된 마지막 시세 사용은 6단계 Redis 적용 후 도입.

### 응답 DTO ↔ 도메인 분리

공공데이터포털 응답 구조 `response > body > items > item[]` 는 `PublicDataResponse` record 에 캡슐화. Adapter 만 이 형식을 알고, `StockPriceClient` 는 도메인 어휘 (`BigDecimal price`, `StockInfo`) 만 노출.

## 종목 마스터 lazy 등록

거래 등록 시점에 `stock` row 가 없으면 외부 API 로 종목명을 조회해 INSERT.

```
TradeService.register()
  └─ StockResolver.resolveOrFetch(ticker)
        ├─ stockRepository.findByTicker(ticker)
        │        ↓ 없음
        ├─ stockPriceClient.fetchStockInfo(ticker)
        └─ stockRepository.save(new Stock(...))
              ↓ unique 제약 위반? → DataIntegrityViolationException → 재조회
```

같은 ticker 가 동시에 들어올 때 두 트랜잭션 모두 INSERT 를 시도할 수 있음. PK 가 ticker 라 한쪽이 unique 제약으로 떨어지고, 그 예외를 잡아 재조회로 멱등 보장.

`StockResolver` 는 `Propagation.MANDATORY` 로 호출자가 트랜잭션을 갖고 있어야만 동작. stock + trade + holding 3 테이블이 한 트랜잭션 안에서 일관성을 가지도록 강제.

### 외부 API 실패 시

거래 등록도 함께 503 으로 실패. 종목명 미상의 stock row 가 만들어져 데이터가 오염되는 것보다, 사용자가 잠시 후 재시도하는 편이 운영 안전성 면에서 단순.

## 표준 예외 처리

### 도메인 예외 계층

```
RuntimeException
└─ BusinessException(errorCode)
   ├─ TradeNotFoundException        (404)
   ├─ HoldingNotFoundException      (400)
   ├─ InsufficientHoldingException  (400)
   ├─ StockNotFoundException        (404)
   └─ ExternalApiException          (503)
```

`ErrorCode` enum 한 곳에 HTTP 상태 + 코드 + 메시지 매핑. `GlobalExceptionHandler` 는 `BusinessException` 만 보고 envelope 으로 변환.

### 핸들러 매핑

| 예외 | HTTP 상태 | 코드 |
| --- | --- | --- |
| `BusinessException` (각 하위) | 도메인 예외별 | T001~T004 / S001 / E001 |
| `MethodArgumentNotValidException` | 400 | C001 |
| `ConstraintViolationException` | 400 | C001 |
| `HttpMessageNotReadableException` | 400 | C001 |
| `MethodArgumentTypeMismatchException` | 400 | C001 |
| `IllegalArgumentException` | 400 | C001 |
| `Exception` (catch-all) | 500 | G001 |

응답 envelope 에 `code` 필드 추가. 클라이언트가 i18n / 분기 처리 시 활용.

```json
{
  "success": false,
  "code": "T003",
  "message": "보유 수량이 부족합니다. ticker=005930, 보유=10, 매도요청=15",
  "timestamp": "2026-06-18T10:30:00"
}
```

### 4단계 예외 정리

4단계의 `IllegalStateException`/`IllegalArgumentException` 호출부를 도메인 예외로 일괄 교체. `replaySell` 의 정합성 위반 (재계산 중 보유 부족) 은 시스템 오류 성격이라 `IllegalStateException` 유지 → catch-all 핸들러로 500 처리.

## 포트폴리오 조회 API

### 응답 구조

```json
{
  "success": true,
  "data": {
    "summary": {
      "totalAsset":           14_500_000.0000,
      "totalEvalProfit":         500_000.0000,
      "totalRealizedProfit":     120_000.0000,
      "totalReturnRate":              0.0357
    },
    "holdings": [
      {
        "ticker":        "005930",
        "stockName":     "삼성전자",
        "quantity":              50.0000,
        "avgPrice":          70_000.0000,
        "currentPrice":      80_000.0000,
        "evalAmount":     4_000_000.0000,
        "evalProfit":       500_000.0000,
        "returnRate":            0.1428,
        "weight":                0.2758
      }
    ]
  },
  "timestamp": "2026-06-18T10:30:00"
}
```

요약 + 종목 리스트 2계층. 클라이언트가 한 번의 호출로 차트와 표를 동시에 그릴 수 있도록 round-trip 1회로 통합.

### 계산 수식

```
evalAmount  = currentPrice × quantity
cost        = avgPrice × quantity
evalProfit  = evalAmount - cost
returnRate  = evalProfit / cost            (cost == 0 → 0)
weight      = evalAmount / totalAsset      (totalAsset == 0 → 0)

totalAsset           = Σ evalAmount
totalCost            = Σ cost
totalEvalProfit      = totalAsset - totalCost
totalRealizedProfit  = Σ realized_profit.profit  (DB 집계)
totalReturnRate      = totalEvalProfit / totalCost
```

수익률 / 비중은 소수 단위 (0.1428 = 14.28%). % 변환은 클라이언트 책임.

### 정렬

`holdings` 는 `evalAmount` DESC. 평가금액이 큰 종목부터 노출. 페이징은 미적용 (보유 종목 수가 많아질 가능성이 낮음).

### 외부 API 호출 횟수

현재 구조는 보유 종목 수 N 회 호출. 6단계에서 시세 캐시 (`stock:price:{ticker}`, TTL 5분) 도입 시 캐시 히트율 만큼 호출 회수 감소.

## 패키지 구조 (5단계 추가)

```
com.eusun97.stocktracker
├── controller/
│   ├── TradeController         (4단계)
│   └── PortfolioController     (5단계)
├── service/
│   ├── TradeService            (4단계, StockResolver 연결)
│   ├── PortfolioService        (5단계)
│   └── StockResolver           (5단계)
├── repository/
│   ├── TradeRepository
│   ├── HoldingRepository
│   ├── RealizedProfitRepository (sumAllProfit 추가)
│   └── StockRepository         (5단계)
├── entity/
│   ├── Trade / Holding / RealizedProfit / TxType
│   └── Stock                   (5단계)
├── dto/
│   ├── Trade* DTO              (4단계)
│   └── PortfolioResponse       (5단계)
├── exception/                  (5단계 신설)
│   ├── ErrorCode
│   ├── BusinessException
│   ├── 도메인 예외 4종
│   └── GlobalExceptionHandler
├── infrastructure/             (5단계 신설)
│   ├── client/
│   │   ├── StockPriceClient    (Port)
│   │   └── publicdata/
│   │       ├── PublicDataStockPriceClient (Adapter)
│   │       ├── PublicDataResponse
│   │       └── PublicDataProperties
│   └── config/
│       └── RestClientConfig
├── config/
│   └── OpenApiConfig
└── common/
    ├── ApiResponse             (code 필드 추가)
    └── PageResponse
```

## 환경 변수

local 기동 시 외부 API 키는 환경변수로 주입. 미설정 시 `dummy` 로 떨어져 호출 자체가 실패하므로 통합 테스트 전 반드시 설정.

```
PUBLICDATA_BASE_URL   (기본값 있음)
PUBLICDATA_SERVICE_KEY (필수)
```

## 검증 시나리오

| # | 시나리오 | 기대 |
| --- | --- | --- |
| 1 | `POST /api/trades` 미존재 ticker BUY | stock INSERT + trade INSERT + holding INSERT |
| 2 | `POST /api/trades` 같은 ticker 동시 BUY 2건 | stock 1행만 존재, holding 정확 합산 |
| 3 | `POST /api/trades` 보유 부족 SELL | 400 + `T003` |
| 4 | `GET /api/portfolio` 빈 보유 | summary 0 / holdings [] |
| 5 | `GET /api/portfolio` 정상 | summary + holdings (evalAmount DESC) |
| 6 | 외부 API 다운 | Circuit Breaker OPEN, 503 + `E001` |
| 7 | 외부 API 5xx 일시 | Retry 3회 후 폴백, 503 + `E001` |
| 8 | 외부 API 4xx | 재시도 없이 즉시 실패 |

## 한계 / 후속

- 시세 호출이 보유 종목 수만큼 발생 — 6단계 Redis Cache-Aside 로 해소
- 실현손익은 전체 합계만 노출 — 종목별/기간별 분해는 7단계 스냅샷·9단계 통계 단계에서 추가
- 동시 거래 등록 부하 테스트는 8단계 Docker Compose 환경 구성 후 진행
