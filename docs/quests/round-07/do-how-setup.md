# Phase 2 인프라 설정 가이드

---

## 1. MySQL binlog 설정

CDC를 위한 정형화된 설정. 프로젝트가 바뀌어도 거의 그대로 쓴다.

```yaml
# docker/infra-compose.yml — mysql 서비스
command:
  - --server-id=1                    # MySQL 서버 고유 ID. Debezium이 replica로 접속할 때 필요
  - --log-bin=mysql-bin              # binlog 활성화. 모든 데이터 변경이 여기 기록됨
  - --binlog-format=ROW              # ROW 포맷 — 변경된 행의 전/후 데이터를 기록 (CDC 필수)
  - --binlog-row-image=FULL          # 변경된 행의 모든 컬럼을 기록
  - --gtid-mode=ON                   # Global Transaction ID — 트랜잭션마다 고유 ID 부여
  - --enforce-gtid-consistency=ON    # GTID 모드 일관성 보장
```

---

## 2. Kafka Connect (Debezium) 컨테이너

Debezium 플러그인이 미리 설치된 Kafka Connect 이미지를 사용한다. 설정은 거의 고정.

```yaml
# docker/infra-compose.yml — kafka-connect 서비스
kafka-connect:
  image: debezium/connect:2.5
  ports:
    - "8083:8083"                     # Kafka Connect REST API 포트
  environment:
    BOOTSTRAP_SERVERS: kafka:9092     # Kafka 브로커 주소
    GROUP_ID: connect-cluster         # Kafka Connect 클러스터 이름
    CONFIG_STORAGE_TOPIC: connect-configs   # connector 설정 저장 토픽
    OFFSET_STORAGE_TOPIC: connect-offsets   # binlog 읽기 위치 저장 토픽
    STATUS_STORAGE_TOPIC: connect-status    # connector 상태 저장 토픽
    *_REPLICATION_FACTOR: 1                 # 로컬 환경이라 복제 1
```

핵심: `OFFSET_STORAGE_TOPIC` — binlog를 어디까지 읽었는지 Kafka 토픽에 저장한다. 컨테이너가 재시작해도 이전 지점부터 이어서 읽는다.

---

## 3. Debezium Connector 설정 — 프로젝트마다 바뀌는 핵심 설정

Kafka Connect REST API로 등록한다. (`docker/debezium/register-connector.json`)

### DB 접속 정보 — 환경마다 다름

```json
"database.hostname": "mysql",
"database.port": "3306",
"database.user": "root",
"database.password": "root",
"database.server.id": "100"          // Debezium이 replica로 쓸 ID (MySQL server-id와 달라야 함)
```

### 감시 대상 — 프로젝트마다 다름

```json
"database.include.list": "loopers",                 // 감시할 DB
"table.include.list": "loopers.outbox_event"        // 감시할 테이블 — outbox만
```

### Outbox 컬럼 매핑 — outbox 테이블 구조가 바뀌면 수정

```json
"transforms.outbox.table.field.event.id": "id",              // PK → 이벤트 고유 ID
"transforms.outbox.table.field.event.key": "aggregate_id",   // → Kafka 파티션 키
"transforms.outbox.table.field.event.payload": "payload",    // → Kafka 메시지 값
"transforms.outbox.table.fields.additional.placement": "event_type:header:eventType"
                                                             // → Kafka 헤더에 이벤트 타입
```

### 토픽 라우팅 — 토픽 네이밍 규칙을 정할 때 수정

```json
"transforms.outbox.route.by.field": "aggregate_type",
"transforms.outbox.route.topic.replacement": "${routedByValue}-events"
// aggregate_type이 "LIKE"  → "LIKE-events" 토픽
// aggregate_type이 "ORDER" → "ORDER-events" 토픽
```

### JSON 페이로드 확장

```json
"transforms.outbox.table.expand.json.payload": true
// payload의 이스케이프된 JSON 문자열을 실제 JSON 객체로 펼쳐서 Kafka에 발행
```

---

## 4. Connector 등록 방법

Docker 인프라를 올린 후, Kafka Connect가 준비되면 등록 스크립트를 실행한다.

```bash
# 인프라 올리기
cd docker && docker compose -f infra-compose.yml up -d

# Connector 등록
./debezium/register-connector.sh
```

수동으로 등록할 경우:
```bash
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @docker/debezium/register-connector.json
```

상태 확인:
```bash
curl http://localhost:8083/connectors/outbox-connector/status
```

---

## 5. 전체 데이터 흐름

```
LikeService.like()
  → 좋아요 등록 → COMMIT
  → AFTER_COMMIT 리스너 → outbox_event 테이블에 INSERT
    → MySQL binlog에 자동 기록
      → Debezium이 binlog에서 감지 (replica로 접속)
        → Outbox Event Router가 변환:
            aggregate_type → 토픽 결정 (LIKE-events)
            aggregate_id → 파티션 키 (productId)
            payload → 메시지 값 (JSON)
            event_type → 헤더 (PRODUCT_LIKED)
          → Kafka "LIKE-events" 토픽에 발행
            → commerce-collector Consumer가 수신
              → product_metrics upsert
```

---

## 변경 포인트 요약

| 영역 | 바꿀 일 | 언제 바꾸나 |
|---|---|---|
| MySQL binlog 설정 | 거의 없음 | CDC 쓰는 한 고정 |
| Kafka Connect 환경 | 거의 없음 | Kafka 주소 바뀔 때만 |
| **DB 접속 정보** | **환경마다** | dev/staging/prod 분리 시 |
| **감시 대상 테이블** | **테이블 추가 시** | outbox 테이블이 늘어나면 |
| **컬럼 매핑** | **outbox 스키마 변경 시** | 컬럼명이 바뀌면 |
| **토픽 라우팅** | **토픽 규칙 변경 시** | 네이밍 컨벤션 바꾸면 |
