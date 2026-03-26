# CLAUDE.md

이 파일은 Claude Code (claude.ai/claude-code)가 이 저장소에서 작업할 때 참고하는 지침서입니다.

## 프로젝트 개요

Loopers에서 제공하는 **Spring Boot + Java 멀티모듈 템플릿** 프로젝트입니다.
커머스 도메인을 예시로 API 서버, 배치, 스트리밍 애플리케이션 구조를 제공합니다.

## 기술 스택 및 버전

### Core
| 기술 | 버전 |
|------|------|
| Java | 21 |
| Spring Boot | 3.4.4 |
| Spring Cloud | 2024.0.1 |
| Gradle | Kotlin DSL |

### 주요 라이브러리
| 라이브러리 | 버전 | 용도 |
|------------|------|------|
| Spring Data JPA | - | ORM |
| QueryDSL | jakarta | 타입 세이프 쿼리 |
| Spring Data Redis | - | 캐시/세션 |
| Spring Kafka | - | 메시지 스트리밍 |
| SpringDoc OpenAPI | 2.7.0 | API 문서화 |
| Lombok | - | 보일러플레이트 제거 |
| Jackson | - | JSON 직렬화 |
| Micrometer (Prometheus, Brave) | - | 메트릭/트레이싱 |

### 테스트
| 라이브러리 | 버전 |
|------------|------|
| JUnit 5 | - |
| Testcontainers | - |
| SpringMockK | 4.0.2 |
| Mockito | 5.14.0 |
| Instancio | 5.0.2 |

### 인프라 (Docker)
| 서비스 | 버전 |
|--------|------|
| MySQL | 8.0 |
| Redis | 7.0 |
| Kafka (KRaft) | 3.5.1 |
| Grafana | latest |
| Prometheus | latest |

## 모듈 구조

```
Root (loopers-java-spring-template)
├── domain/                  # Domain Layer (순수 비즈니스 규칙, java-library)
│
├── application/             # Application Layer (유스케이스 조합, java-library)
│   └── commerce-service/    # 비즈니스 서비스 로직
│
├── presentation/            # Presentation Layer (Spring Boot bootJar)
│   ├── commerce-api/        # REST API 서버 (Web, Actuator, OpenAPI)
│   ├── commerce-batch/      # Spring Batch 애플리케이션
│   └── commerce-streamer/   # Kafka Consumer 스트리밍 애플리케이션
│
├── modules/                 # Infrastructure Layer (인프라 어댑터, java-library)
│   ├── jpa/                 # JPA + QueryDSL + MySQL 설정 + Repository Adapter
│   ├── redis/               # Redis 설정 (Master-Replica 지원)
│   └── kafka/               # Kafka Producer/Consumer 설정
│
├── supports/                # Cross-cutting (횡단 관심사)
│   ├── jackson/             # Jackson ObjectMapper 설정
│   ├── logging/             # Logback + Slack Appender 설정
│   └── monitoring/          # Prometheus + Micrometer 메트릭 설정
│
└── docker/                  # Docker Compose 파일
    ├── infra-compose.yml    # MySQL, Redis, Kafka 인프라
    └── monitoring-compose.yml # Grafana, Prometheus 모니터링
```

### 모듈 의존성

- **presentation** → **application**, **domain**, **modules**, **supports**
- **application** → **domain** (만 의존)
- **modules** → **domain** (엔티티 참조, Port 구현)
- **supports** → 독립적 (add-on 성격)
- **domain** → 없음 (최하위 계층)

## 빌드 및 실행

### 빌드
```bash
./gradlew build
```

### 테스트
```bash
./gradlew test
```

테스트 설정:
- 타임존: `Asia/Seoul`
- 프로파일: `test`
- Testcontainers 사용 (MySQL, Redis, Kafka)

### 로컬 개발 환경 실행

1. 인프라 실행
```bash
docker-compose -f ./docker/infra-compose.yml up -d
```

2. 모니터링 실행 (선택)
```bash
docker-compose -f ./docker/monitoring-compose.yml up -d
```
- Grafana: http://localhost:3000 (admin/admin)

3. 애플리케이션 실행
```bash
./gradlew :presentation:commerce-api:bootRun
```

### 로컬 인프라 접속 정보

| 서비스 | 호스트:포트 | 인증 정보 |
|--------|-------------|-----------|
| MySQL | localhost:3306 | application/application |
| Redis Master | localhost:6379 | - |
| Redis Replica | localhost:6380 | - |
| Kafka | localhost:19092 | - |
| Kafka UI | localhost:9099 | - |

## 코드 컨벤션

### 패키지 구조 (presentation 모듈)
```
com.loopers
├── interfaces/           # 외부 인터페이스 (API, Consumer)
│   ├── api/             # REST Controller
│   └── consumer/        # Kafka Consumer
├── infrastructure/      # app-specific Repository Adapter
└── {App}Application.java
```

### 패키지 구조 (application 모듈)
```
com.loopers
├── application/         # Service, Facade, DTO
├── domain/              # app-specific 도메인 로직
└── support/             # 공통 유틸/예외
```

### 패키지 구조 (domain 모듈)
```
com.loopers
├── domain/              # Entity, VO, Repository Port, Policy
└── utils/               # 도메인 유틸리티
```

### 패키지 구조 (modules 모듈)
```
com.loopers
├── config/              # Configuration 클래스
└── infrastructure/      # 공유 Repository Adapter
```

## 주요 파일 위치

- 빌드 설정: `build.gradle.kts`, `gradle.properties`
- 멀티모듈 설정: `settings.gradle.kts`
- Docker 설정: `docker/`
- HTTP 클라이언트: `http/`

## 개발 규칙
### 진행 Workflow - 증강 코딩
- **대원칙** : 방향성 및 주요 의사 결정은 개발자에게 제안만 할 수 있으며, 최종 승인된 사항을 기반으로 작업을 수행.
- **중간 결과 보고** : AI 가 반복적인 동작을 하거나, 요청하지 않은 기능을 구현, 테스트 삭제를 임의로 진행할 경우 개발자가 개입.
- **설계 주도권 유지** : AI 가 임의판단을 하지 않고, 방향성에 대한 제안 등을 진행할 수 있으나 개발자의 승인을 받은 후 수행.

### 개발 Workflow - TDD (Red > Green > Refactor)
- 모든 테스트는 3A 원칙으로 작성할 것 (Arrange - Act - Assert)
#### 1. Red Phase : 실패하는 테스트 먼저 작성
- **프로덕션 코드 없이 테스트부터 작성한다.** 컴파일 에러가 나는 상태가 정상이다. 존재하지 않는 클래스, 메서드를 테스트에서 먼저 설계하고, 그 설계를 기반으로 Green Phase에서 프로덕션 코드를 작성한다.
- 요구사항을 만족하는 기능 테스트 케이스 작성
- 프로덕션에서 아직 사용하지 않는 동작이라도 자유롭게 테스트 작성 가능 (Refactor에서 정리)
#### 2. Green Phase : 테스트를 만족하도록 프로덕션 코드 작성
- Red Phase에서 작성한 테스트가 모두 통과할 수 있도록 프로덕션 코드를 작성한다.
- 오버엔지니어링 금지
#### 3. Refactor Phase : 테스트 점검 및 객체지향 개선
- 테스트에서 이상한 점이 있는지 확인하고, 코드를 더욱 객체지향적으로 개선한다.
- 프로덕션 미사용 테스트 삭제: 프로덕션 코드에서 사용하지 않는 동작을 검증하는 테스트는 이 단계에서 삭제한다. (단, 필수값 검증이나 비즈니스 규칙 검증은 유지)
- 불필요한 private 함수 지양, 객체지향적 코드 작성
- unused import 제거
- 성능 최적화
- 모든 테스트 케이스가 통과해야 함

### 테스트 단위 원칙 - 단언문(Assertion)은 하나
- **단언문이란**: 테스트의 검증 지점. "이 테스트가 무엇을 증명하는가"의 답. 하나의 단언문 = 하나의 기대 행위.
- **원칙**: 하나의 테스트 메서드에는 단언문(`assertThat`, `assertThrows` 등)이 **1개만** 존재해야 한다.
- **E2E 테스트도 동일**: MockMvc의 `.andExpect()`도 단언문이다. HTTP 상태코드 검증과 응답 본문 검증은 별도 테스트로 분리한다.
- **이유**: 단언문이 여러 개이면 첫 번째 실패 시 나머지는 실행되지 않아 실패 정보가 손실된다. 또한 테스트 이름이 "무엇을 검증하는가"를 정확히 표현할 수 없게 된다.
- **적용**: 단언문이 2개 이상 필요한 테스트는 별도의 테스트 메서드로 분리한다.

### 도메인 & 객체 설계 원칙

- **비즈니스 규칙 캡슐화**: 도메인 객체(Entity, VO)가 비즈니스 규칙을 소유한다. Application Service에 도메인 로직을 직접 구현하지 않는다.
- **Application Service는 조정자**: 서로 다른 도메인 객체를 조립하고, 도메인 로직을 조정하여 유스케이스를 완성한다. 규칙 판단은 도메인 객체에 위임한다.
- **규칙 위치 판단**: 동일한 규칙이 여러 Service에 반복되면, 해당 규칙은 도메인 객체(Entity/VO/Domain Service)에 속할 가능성이 높다. 도메인 객체로 이동한다.
- **기능별 책임과 결합도**: 새로운 기능 구현 시, 해당 기능의 책임 소재와 다른 도메인과의 결합도에 대해 개발자의 의도를 확인한 후 진행한다.
- **JPA 관계 매핑 금지**: `@OneToMany`, `@ManyToOne`, `@OneToOne` 사용하지 않는다. 모든 엔티티 간 참조는 `ID(Long)`로만 한다.
- **Aggregate Root 원칙**: 하위 엔티티에 대한 불변식 검증은 Aggregate Root가 직접 수행한다. 외부(DomainService 등)에 위임하지 않는다.
- **Aggregate Root 하위 할당**: Root가 하위 엔티티의 소속을 관리한다 (예: `Order.assignOrderLines()` → 각 `line.assignToOrder()`).
- **연산의 닫힘**: assign 등 상태 변경 메서드는 자기 자신(this)을 반환하여 체이닝 가능하게 한다. `forEach`(void)보다 `map`(self 반환) 선호.
- **정적 팩토리 네이밍**: 도메인 행위를 표현하는 동사 사용 (`Brand.register`, `Order.place` 등). `create` 같은 기술적 이름 금지.
- **엔티티 메서드명 — WHAT, not HOW**: 메서드명은 "무엇을 하느냐(비즈니스 행위)"에 초점을 맞춘다. "어떻게 하느냐(구현 방식)"는 금지. 기술적 필요에 의한 메서드(ID 연결 등)는 엔티티 행위에 맞지 않으므로 도입 전 필요성을 재검토한다.
- **예외 테스트**: `isInstanceOf(CoreException.class)`만이 아니라 구체적인 `ExceptionMessage`도 함께 검증한다.
- **캡슐화**: 외부 코드에서 getter 체인(`entity.getVO().getValue()`) 사용 금지. VO 내부 구조를 외부에 노출하지 않는다.
  - 테스트 then절: 도메인 행위 메서드(boolean 반환)로 검증. VO 내부값 검증은 VOTest에서만. 예: `assertThat(order.isAccepted()).isTrue()`
  - 프로덕션 코드: 엔티티가 편의 메서드(`nameValue()`, `priceValue()` 등)를 제공. DTO/Service가 VO 내부 구조를 알 필요 없다.
  - 도메인 내부: 엔티티의 `hasName()`, VO 간 `Stock.isEnough(Quantity)` 등은 허용.
- **프로덕션 미사용 테스트 정리**: Red Phase에서는 자유롭게 작성하되, Refactor Phase에서 프로덕션 코드에서 실제로 사용하지 않는 동작을 검증하는 테스트는 삭제한다. (단, 필수값 검증이나 비즈니스 규칙 검증은 유지)

#### Domain Service 도입 기준

Domain Service는 다음 3가지 경우에 도입한다:

1. **단일 엔티티에 속하지 않는 도메인 규칙이 있을 때**
   - 예: "주문 시 모든 상품의 재고가 충분해야 승인" — 여러 Product의 상태를 종합 판단하는 규칙
2. **여러 엔티티 간 불변식(invariant)을 검증해야 할 때**
   - 예: "브랜드 삭제 시 소속 상품 상태 확인" — Brand와 Product 간 무결성
3. **Application Service에 도메인 로직이 누출될 때**
   - 예: 재고 차감 + 주문 승인/거절 판단이 Service에 있으면 Domain Service로 추출

#### Facade vs Domain Service 구분

| 구분 | Facade | Domain Service |
|------|--------|----------------|
| 위치 | Application 레이어 | Domain 레이어 |
| 역할 | Application Service 간 순환 참조 해소 | 같은 BC 내 cross-aggregate 도메인 규칙 |
| 예시 | (현재 해당 없음) | CatalogDomainService (Brand↔Product 삭제 연쇄, 생성 시 브랜드 검증) |
| 의존 | 여러 Application Service 주입 | 도메인 객체 + Repository Port (DIP) |

#### 동시성 / 락 / 트랜잭션 검토 기준

새로운 기능 구현 또는 리팩토링 시, 다음 기준으로 동시성·락·트랜잭션 이슈를 검토한다:

1. **사용자 관점 동시성 장애**: 동시 요청이 발생했을 때 사용자에게 장애(데이터 유실, 초과 판매, 중복 처리 등)가 발생하는가?
2. **락/원자 연산 판단**: 장애 가능성이 있다면, 비관적 락 / 낙관적 락 / 원자 SQL 중 어떤 전략이 적합한가?
3. **트랜잭션 범위**: 하나의 트랜잭션이 너무 커서 불필요한 락 보유 시간이 길어지거나, 무관한 aggregate가 묶여 있지 않은가?
4. **Aggregate 트랜잭션 원칙**: 하나의 aggregate 내의 엔티티들은 반드시 하나의 트랜잭션에 묶여야 한다 (aggregate의 불변식 보장). 서로 다른 aggregate를 하나의 트랜잭션에 묶을 경우, 트랜잭션 분리 기준(아래)으로 정당화해야 한다.

#### 트랜잭션 분리 기준

트랜잭션 분리 여부는 다음 2가지 조건을 기준으로 판단한다:

1. **비즈니스 문제 없는가**: 쪼개졌을 때 비즈니스적으로 문제가 발생하지 않는가
2. **데이터 정합성 유지되는가**: 분리 후에도 데이터 정합성이 보장되는가

두 조건을 모두 만족하는 경우에만 트랜잭션을 분리한다. 하나라도 만족하지 못하면 단일 트랜잭션으로 유지한다.

### 레이어 경계 원칙

#### DTO 분리

Presentation DTO와 Application DTO를 분리한다. 각 레이어는 자신의 DTO만 소유한다.

| 레이어 | DTO 위치 | 예시 |
|--------|---------|------|
| Presentation | `interfaces/api/{domain}/dto/` | `BrandCreateApiRequest`, `BrandApiResponse` |
| Application | `application/service/dto/` | `BrandCreateCommand`, `BrandInfo` |

- **변환**: `from()`, `to()` 매퍼 메서드를 사용하여 DTO 간 변환한다.
- **표현 로직**: 마스킹, 포맷팅 등 표현 관심사는 Presentation DTO 또는 Controller에서 처리한다.
- **Application DTO**: 도메인 개념을 반영한 순수한 데이터 전달 객체. HTTP 관심사(status code, header 등)를 알지 않는다.

#### 모듈-레이어 대응

| Gradle 모듈 | 논리적 레이어 | 패키지 루트 | 하위 구조 |
|-------------|-------------|-----------|----------|
| `domain/` | Domain | `com.loopers.domain` | `/{도메인명}/` (member, brand, product ...) |
| `application/commerce-service/` | Application | `com.loopers.application` | `/service/`, `/service/dto/` |
| `presentation/commerce-api/` | Presentation | `com.loopers.interfaces.api` | `/{도메인명}/`, `/{도메인명}/dto/` |
| `modules/jpa/` | Infrastructure | `com.loopers.infrastructure` | `/{도메인명}/` (member, brand, product ...) |

## 주의사항
### 1. Never Do
- 실제 동작하지 않는 코드, 불필요한 Mock 데이터를 이요한 구현을 하지 말 것
- null-safety 하지 않게 코드 작성하지 말 것 (Java 의 경우, Optional 을 활용할 것)
- println 코드 남기지 말 것

### 2. Recommendation
- 실제 API 를 호출해 확인하는 E2E 테스트 코드 작성
- 재사용 가능한 객체 설계
- 성능 최적화에 대한 대안 및 제안
- 개발 완료된 API 의 경우, `.http/**.http` 에 분류해 작성

### 3. Priority
1. 실제 동작하는 해결책만 고려
2. null-safety, thread-safety 고려
3. 테스트 가능한 구조로 설계
4. 기존 코드 패턴 분석 후 일관성 유지

### 4. 코드 검수 (구현 완료 후)
구현 완료 시 다음 관점의 명세서를 작성한다.
- **테스트↔프로덕션 매핑**: 어떤 테스트가 어떤 프로덕션 로직을 검증하는지 사실만 기술
- **테스트 유통기한**: 테스트가 구현이 아닌 행위를 검증하고 있어 리팩토링에 강한지
- **BC/Aggregate 경계**: 바운디드 컨텍스트와 애그리거트 영역을 잘 지키는지
- **레이어 책임**: 도메인 로직이 Application에 누출되지 않았는지, Presentation이 비즈니스 규칙을 알지 않는지

## EDA (Event-Driven Architecture) 설계 원칙

### 이벤트 설계 — 대상, 행위, 정보, 시간

모든 도메인 이벤트는 다음 4가지 요소를 포함해야 한다:

| 요소 | 설명 | 예시 (배달 집하 완료) |
|------|------|----------------------|
| **대상** | 주체가 누구인가 | 배달 |
| **행위** | 대상이 무엇을 하는가 | 집하 완료 |
| **정보** | 주체가 행위 시 갖고 있던 정보 | BHC에서 후라이드를 |
| **시간** | 언제 일어났는가 | 10시 |

- 이벤트 이름: `{대상}{행위 과거분사}Event` (ProductLikedEvent, OrderCreatedEvent)
- 팩토리 메서드(`of`)로 생성하여 `occurredAt`을 자동 설정
- 정보가 부족하면 Consumer가 DB를 다시 조회해야 하므로, 이벤트 발행 시점에 충분한 정보를 담는다

### 이벤트 분리 기준

```
이 로직을 분리했을 때 일시적 불일치가 비즈니스 리스크를 만드는가?
├── Yes → 같은 TX 유지, @EventListener로 코드만 분리
└── No
    ├── 이 로직이 실패하면 메인도 실패해야 하는가?
    │   └── No → @TransactionalEventListener(AFTER_COMMIT)
    └──
```

1. **동기 도메인 이벤트** (`@EventListener`, 같은 TX) — 코드 복잡도 해소, 일관성 유지
2. **부가 로직 분리** (`@TransactionalEventListener` AFTER_COMMIT) — 실패해도 메인에 영향 없음
3. **결과적 일관성** — 같은 TX에 안 묶여도 되는 메인 로직

### Outbox Pattern

- outbox 저장은 비즈니스 데이터와 **같은 TX**에서 수행 (`@EventListener`)
- AFTER_COMMIT으로 outbox에 저장하면 커밋 후 앱이 죽을 때 유실 가능
- 예외: readOnly TX에서는 AFTER_COMMIT + 별도 TX 사용 (유실 감수)

### Kafka Consumer

- Consumer(메시지 수신 + Ack)와 Processor(DB 처리)를 **별도 빈으로 분리** — Spring `@Transactional` 프록시 이슈
- 집계(좋아요/판매량/조회수)는 **Batch Listener**, 쿠폰 발급 같은 건 **Single Listener** (실패 시 Kafka 재전달)
- 멱등 처리: `event_handled` 테이블로 중복 처리 방지
- 토픽 네이밍: **소문자 kebab-case** (like-events, order-events)

### 이벤트 테스트

- `any()` 사용 금지 — 너무 넓어서 잘못된 인자가 전달되어도 테스트가 통과하고, null 관련 버그를 숨기며, 거짓 안정감을 줌
- 이벤트를 발행하는 쪽(서비스)의 테스트는 **이벤트 발행 여부**에 집중
- 이벤트를 수신하는 쪽(리스너/Consumer)의 테스트는 리스너 자체의 동작을 검증
- 서비스 테스트에서 리스너의 결과까지 검증하지 않는다 — 관심사가 분리된 만큼 테스트도 분리