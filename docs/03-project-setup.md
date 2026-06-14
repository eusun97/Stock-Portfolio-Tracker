# 프로젝트 초기 세팅

## 개발 환경

| 항목 | 값 |
| --- | --- |
| 호스트 OS | macOS (Apple Silicon, ARM64) |
| 개발 VM | Multipass Ubuntu 22.04 LTS (ARM64) |
| 자바 | OpenJDK 17 (Eclipse Temurin) |
| 빌드 | Gradle Wrapper (Groovy DSL) |
| 프레임워크 | Spring Boot 3.5.x |
| IDE | IntelliJ IDEA Ultimate |

운영 환경(EC2 Ubuntu)과 로컬 환경의 OS 차이를 차단하기 위해 Multipass VM을 도입.
호스트는 IDE와 Git만, 빌드와 실행은 모두 Ubuntu VM에서 수행.

## 의존성

```
spring-boot-starter-web              REST API
spring-boot-starter-data-jpa         JPA
spring-boot-starter-validation       입력 검증
spring-boot-starter-actuator         헬스체크/메트릭
spring-boot-devtools                 개발용 자동 재시작
lombok                               보일러플레이트 제거
postgresql                           DB 드라이버
```

## 패키지 구조

레이어드 아키텍처 기반.

```
com.eusun97.stocktracker
├── StockTrackerApplication.java
├── controller     REST 엔드포인트
├── service        비즈니스 로직
├── repository     DB 접근
├── entity         JPA 엔티티
├── dto            요청/응답 객체
├── config         설정 클래스
├── exception      공통 예외 + 글로벌 핸들러
└── common         공통 응답, 유틸
```

도메인 수가 5개로 작아 패키지 폭발 위험이 낮음. 익숙도와 가독성 우선.

## 환경 분리 — Spring Profile

설정 파일을 환경별로 3개로 분리.

```
src/main/resources/
├── application.yml          공통 (모든 환경에서 항상 로드)
├── application-local.yml    로컬 (.gitignore)
└── application-prod.yml     운영
```

- 활성 프로파일은 `SPRING_PROFILES_ACTIVE` 환경변수로 전환. 기본값 `local`
- 같은 키는 프로파일 yml이 공통 yml을 덮어씀
- `application-local.yml`은 로컬 비밀번호/키 자리이므로 `.gitignore` 처리

### 환경별 차이

| 항목 | local | prod |
| --- | --- | --- |
| Actuator 노출 | 전부(`*`) | health/info/metrics/prometheus |
| Health 디테일 | always | never |
| 로그 레벨 | DEBUG + Hibernate SQL TRACE | INFO |

## 검증

- `./gradlew build` → BUILD SUCCESSFUL
- 로컬 프로파일로 `./gradlew bootRun` → `/actuator/health` 풀 디테일 응답
- `SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun` → `/actuator/health` 디테일 차단 확인

## 형상관리 정책

- 메인 브랜치 보호. 모든 작업은 `feat/*`, `fix/*` 브랜치에서 진행
- Pull Request 후 Squash and Merge로 main에 1커밋씩 반영
- 커밋 메시지: 한국어 한 줄 요약
