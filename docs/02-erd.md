# ERD 설계

## 엔티티 식별

요구사항을 도메인 단위로 묶어 5개 엔티티로 정리

| 엔티티 | 한국어 | 역할 |
| --- | --- | --- |
| `stock` | 종목 마스터 | 종목코드, 종목명 등 정적 정보 |
| `transaction` | 거래내역 | 매수/매도 한 건 한 건의 사실 기록 |
| `holding` | 보유 종목 | 종목별 현재 보유수량과 평균 매입가 (집계 결과) |
| `realized_profit` | 실현손익 | 매도 시점에 확정된 손익 |
| `portfolio_snapshot` | 일간 스냅샷 | 매일 자정 포트폴리오 전체 평가 결과 |

현재가 캐시는 RDB가 아닌 Redis에 저장하므로 엔티티에 포함하지 않음

## 설계 결정

### 거래내역과 보유종목을 분리한 이유
거래내역은 append-only로 쌓아두는 사실 기록이고, 보유종목은 그 사실들을 집계한 현재 상태
거래가 누적될수록 매번 SUM 재계산하는 비용이 커지므로, 현재 상태를 별도 테이블에 두고 거래 발생 시 트랜잭션 안에서 같이 갱신

### 실현손익을 별도 테이블로 둔 이유
매도 거래에 손익 컬럼을 직접 박으면 매수 거래에는 의미 없는 NULL이 생기고, 기간별 실현손익 집계 시 매도 행만 필터링하는 비용 발생
매도 한 건과 손익 한 건의 1:1 관계가 명확하므로 분리

### 종목 마스터를 분리한 이유
거래마다 종목명을 문자열로 박으면 정규화 위반이며, 회사명 변경 시 일괄 갱신이 어려움
ticker를 PK로 한 마스터 테이블에서 관리하고 다른 테이블은 FK로 참조

### 종목 마스터는 lazy 등록
사전에 KRX 전 종목을 일괄 적재하지 않고, 거래 등록 시 처음 보는 ticker가 들어오면
외부 API로 종목 정보를 조회해 `stock`에 INSERT한 뒤 거래를 진행
사용한 종목만 관리 (데이터 깔끔)

### 금액/수량 타입은 NUMERIC
부동소수점 오차로 인한 손익 어긋남을 방지하기 위해 `DOUBLE`/`FLOAT`을 사용하지 않고 `NUMERIC(19,4)`로 통일
한국 주식은 정수 수량이지만 분할/병합/소수점 거래 대비 여유를 둠

### 거래 수정/삭제 정책
- 거래내역은 수정/삭제가 가능 (FR-02)
- 삭제는 Soft Delete (`deleted_at` 컬럼)로 처리하여 이력 보존
- 거래가 수정/삭제되면 해당 종목의 `holding`, `realized_profit`은 처음부터 재계산하여 덮어씀
- `portfolio_snapshot`은 시점의 사실 기록이므로 재계산하지 않음, 거래 변경 이후 자정에 찍히는 새 스냅샷부터 변경 내용이 반영

### 전량 매도 시 보유종목 처리
보유수량이 0이 되면 `holding` row를 삭제
>> 보유 종목 목록 조회 시 0주 종목을 필터링하는 로직을 둘 필요가 없음

## ERD

```mermaid
erDiagram
    stock ||--o{ transaction : "거래 발생"
    stock ||--o| holding : "보유 상태"
    stock ||--o{ realized_profit : "매도 시 발생"
    transaction ||--o| realized_profit : "매도 1건당 손익 1건"

    stock {
        VARCHAR ticker PK "종목코드"
        VARCHAR name "종목명"
        VARCHAR market "KOSPI/KOSDAQ"
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    transaction {
        BIGINT id PK
        VARCHAR ticker FK
        VARCHAR tx_type "BUY/SELL"
        NUMERIC quantity
        NUMERIC price
        DATE traded_at
        TIMESTAMP created_at
        TIMESTAMP updated_at
        TIMESTAMP deleted_at "soft delete"
    }

    holding {
        VARCHAR ticker PK_FK
        NUMERIC quantity
        NUMERIC avg_buy_price
        TIMESTAMP updated_at
    }

    realized_profit {
        BIGINT id PK
        BIGINT transaction_id FK
        VARCHAR ticker FK
        NUMERIC sell_price
        NUMERIC avg_buy_price
        NUMERIC quantity
        NUMERIC profit
        DATE realized_at
        TIMESTAMP created_at
    }

    portfolio_snapshot {
        BIGINT id PK
        DATE snapshot_date UK
        NUMERIC total_asset
        NUMERIC total_evaluation_profit
        NUMERIC total_realized_profit
        NUMERIC total_return_rate
        TIMESTAMP created_at
    }
```

## 테이블 상세

### stock
| 컬럼 | 타입 | 제약 |
| --- | --- | --- |
| ticker | VARCHAR(10) | PK |
| name | VARCHAR(100) | NOT NULL |
| market | VARCHAR(20) | NOT NULL |
| created_at | TIMESTAMP | NOT NULL DEFAULT now() |
| updated_at | TIMESTAMP | NOT NULL DEFAULT now() |

### transaction
| 컬럼 | 타입 | 제약 |
| --- | --- | --- |
| id | BIGINT | PK, IDENTITY |
| ticker | VARCHAR(10) | FK→stock, NOT NULL |
| tx_type | VARCHAR(10) | NOT NULL, CHECK IN ('BUY','SELL') |
| quantity | NUMERIC(19,4) | NOT NULL, CHECK > 0 |
| price | NUMERIC(19,4) | NOT NULL, CHECK >= 0 |
| traded_at | DATE | NOT NULL |
| created_at | TIMESTAMP | NOT NULL DEFAULT now() |
| updated_at | TIMESTAMP | NOT NULL DEFAULT now() |
| deleted_at | TIMESTAMP | NULL |

인덱스: `(ticker, traded_at)` — 종목별 시계열 조회

### holding
| 컬럼 | 타입 | 제약 |
| --- | --- | --- |
| ticker | VARCHAR(10) | PK, FK→stock |
| quantity | NUMERIC(19,4) | NOT NULL, CHECK > 0 |
| avg_buy_price | NUMERIC(19,4) | NOT NULL, CHECK >= 0 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT now() |

전량 매도 시 row 삭제.

### realized_profit
| 컬럼 | 타입 | 제약 |
| --- | --- | --- |
| id | BIGINT | PK |
| transaction_id | BIGINT | FK→transaction, UNIQUE NOT NULL |
| ticker | VARCHAR(10) | FK→stock, NOT NULL |
| sell_price | NUMERIC(19,4) | NOT NULL |
| avg_buy_price | NUMERIC(19,4) | NOT NULL |
| quantity | NUMERIC(19,4) | NOT NULL |
| profit | NUMERIC(19,4) | NOT NULL |
| realized_at | DATE | NOT NULL |
| created_at | TIMESTAMP | NOT NULL DEFAULT now() |

인덱스: `(realized_at)` — 기간별 실현손익 집계

### portfolio_snapshot
| 컬럼 | 타입 | 제약 |
| --- | --- | --- |
| id | BIGINT | PK |
| snapshot_date | DATE | NOT NULL UNIQUE |
| total_asset | NUMERIC(19,4) | NOT NULL |
| total_evaluation_profit | NUMERIC(19,4) | NOT NULL |
| total_realized_profit | NUMERIC(19,4) | NOT NULL |
| total_return_rate | NUMERIC(10,4) | NOT NULL |
| created_at | TIMESTAMP | NOT NULL DEFAULT now() |

`snapshot_date` UNIQUE — 추이 조회 인덱스 + 중복 방지를 동시에 해결

## 거래 시나리오 검증

### A. 매수
삼성전자 10주 @ 70,000 매수.
1. `transaction` INSERT (BUY, qty=10, price=70000)
2. `holding` UPSERT
   - 신규: qty=10, avg=70,000
   - 기존: avg = (old_avg × old_qty + 70,000 × 10) / (old_qty + 10)

### B. 매도
삼성전자 5주 @ 80,000 매도. 직전 holding은 (qty=10, avg=70,000).
1. `transaction` INSERT (SELL, qty=5, price=80,000)
2. `realized_profit` INSERT
   - sell=80,000, avg=70,000, qty=5, profit = (80,000 − 70,000) × 5 = 50,000
3. `holding` UPDATE
   - qty = 10 − 5 = 5, avg = 70,000 (변동 없음)

A와 B 모두 단일 트랜잭션으로 묶음

### C. 거래 수정
6/1 매수 거래 (10주 → 8주) 수정
1. `transaction` UPDATE
2. 해당 종목의 `realized_profit` 전부 DELETE
3. 해당 종목의 살아있는 거래내역을 traded_at 순으로 다시 재생하며 `holding`을
   처음부터 재계산. 매도 거래마다 `realized_profit` 새로 INSERT
4. `portfolio_snapshot`은 건드리지 않음

### D. 일간 스냅샷
매일 자정 Spring Scheduler 실행
1. 모든 `holding` 조회
2. 종목별 현재가 조회 (Redis → 미적중 시 외부 API)
3. 평가금액·평가손익 합산
4. 누적 실현손익은 `realized_profit` SUM
5. `portfolio_snapshot` INSERT (`snapshot_date` UNIQUE로 중복 방지)
