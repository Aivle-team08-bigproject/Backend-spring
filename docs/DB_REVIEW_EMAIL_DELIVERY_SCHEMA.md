# DB 검토 요청서: Spring 이메일 발송 이력 스키마

## 1. 요청 목적

데이터 선별 Agent가 생성한 합성 샘플 5건을 외부 고객에게 이메일로 전달하기 위해 Spring 내부 이메일 서비스를 추가하고 있습니다.

사용자 인증·권한과 샘플 검증은 FastAPI가 담당하고, Spring은 검증된 내부 요청을 받아 이메일 발송과 발송 이력을 단독으로 관리합니다. 이번 요청은 Spring 전용 이메일 발송 이력 스키마와 Flyway 마이그레이션에 대한 DB 검토입니다.

**현재 해당 SQL은 Neon에 적용하지 않았습니다. DB 검토와 승인 후 개발 환경부터 적용해야 합니다.**

## 2. 서비스와 데이터 소유권

| 구분 | 소유 서비스 | 설명 |
|---|---|---|
| Agent 실행 및 선별 샘플 | FastAPI | 기존 `service` 스키마와 `StageRun.output_payload` 사용 |
| 이메일 발송 이력 | Spring | 신규 `email_service` 스키마 단독 소유 |
| 사용자 인증·권한 | FastAPI | Spring은 사용자 JWT를 직접 처리하지 않음 |
| 이메일 발송 | Spring | 로컬 Mailpit, 운영 Amazon SES 예정 |

Spring과 FastAPI가 같은 발송 행을 함께 수정하지 않습니다. 기존 `service.deliveries` 테이블은 재사용하거나 변경하지 않습니다.

## 3. 검토 대상 파일

Flyway 마이그레이션:

```text
Backend-spring/src/main/resources/db/migration/V1__create_email_deliveries.sql
```

JPA 엔티티:

```text
Backend-spring/src/main/java/com/aivle/team08/backendspring/email/domain/EmailDelivery.java
```

Repository:

```text
Backend-spring/src/main/java/com/aivle/team08/backendspring/email/infrastructure/EmailDeliveryRepository.java
```

## 4. 요청 스키마

신규 스키마 이름:

```sql
email_service
```

신규 테이블:

```sql
email_service.email_deliveries
```

`run_id`, `stage_attempt_no`, `requested_by`는 FastAPI 영역의 논리적 참조값입니다. 서비스 간 결합과 마이그레이션 충돌을 막기 위해 `service` 스키마에 대한 외래 키는 생성하지 않습니다.

## 5. 컬럼 정의

| 컬럼 | 타입 | NULL | 목적 |
|---|---|---:|---|
| `id` | `BIGINT IDENTITY` | N | 발송 식별자 |
| `idempotency_key` | `VARCHAR(128)` | N | 접수 재시도 중복 방지 키 |
| `request_fingerprint` | `CHAR(64)` | N | 동일 키에 다른 요청이 들어오는지 확인하는 SHA-256 |
| `run_id` | `BIGINT` | N | FastAPI PipelineRun 논리 식별자, FK 없음 |
| `stage_attempt_no` | `INTEGER` | N | 발송 대상 선별 단계 버전 |
| `requested_by` | `BIGINT` | N | FastAPI에서 인증한 요청자 논리 식별자, FK 없음 |
| `delivery_type` | `VARCHAR(40)` | N | 현재 `SELECTION_SAMPLE` |
| `recipient` | `VARCHAR(254)` | N | 실제 이메일 발송에 사용할 수신 주소 |
| `recipient_normalized` | `VARCHAR(254)` | N | 중복 판정용 정규화 주소 |
| `status` | `VARCHAR(30)` | N | 발송 상태 |
| `provider` | `VARCHAR(30)` | Y | `SMTP`, `SES` 등 발송 제공자 |
| `provider_message_id` | `VARCHAR(255)` | Y | 외부 제공자 메시지 ID |
| `attempt_count` | `INTEGER` | N | 실제 발송 시도 횟수 |
| `failure_code` | `VARCHAR(80)` | Y | 내부 장애 분류 코드 |
| `failure_reason` | `VARCHAR(1000)` | Y | 내부 운영용 실패 정보 |
| `sample_sha256` | `CHAR(64)` | N | 합성 샘플 스냅샷 해시 |
| `template_version` | `VARCHAR(40)` | N | 사용한 이메일 템플릿 버전 |
| `created_at` | `TIMESTAMPTZ` | N | 최초 접수 시각 |
| `updated_at` | `TIMESTAMPTZ` | N | 최종 변경 시각 |
| `delivered_at` | `TIMESTAMPTZ` | Y | 발송 완료 시각 |
| `version` | `BIGINT` | N | JPA 낙관적 잠금 버전 |

샘플 5건의 원문은 이메일 DB에 저장하지 않고 SHA-256과 템플릿 버전만 저장합니다.

## 6. 상태 계약

허용 상태:

```text
QUEUED
SENDING
SENT
RETRYING
FAILED
```

예상 흐름:

```text
QUEUED → SENDING → SENT
             └──→ RETRYING → SENDING
                         └──→ FAILED
```

상태 전이 검증은 Spring 애플리케이션에서 수행합니다. DB CHECK는 허용 가능한 상태 값만 제한합니다.

## 7. 중복 발송 방지

### Idempotency-Key

```sql
UNIQUE (idempotency_key)
```

FastAPI가 Spring 접수 응답을 받지 못해 같은 요청을 재시도하더라도 기존 발송 건을 반환하기 위한 제약입니다. 같은 키인데 `request_fingerprint`가 다르면 Spring이 `409 IDEMPOTENCY_KEY_REUSED`로 거부합니다.

### 진행 중 발송 중복 방지

```sql
CREATE UNIQUE INDEX uq_email_deliveries_active_request
ON email_deliveries (
    run_id,
    stage_attempt_no,
    recipient_normalized,
    delivery_type
)
WHERE status IN ('QUEUED', 'SENDING', 'RETRYING');
```

애플리케이션의 조회 후 INSERT 사이에 발생할 수 있는 경쟁 조건을 PostgreSQL에서 최종 차단하기 위한 partial unique index입니다.

## 8. 이메일 정규화 정책

현재 정책:

1. 주소 앞뒤 공백 제거
2. `@` 뒤의 도메인 부분만 소문자 변환
3. 로컬 파트는 원형 유지

적용하지 않는 처리:

- Gmail `+tag` 제거
- Gmail 점 제거
- 도메인 별칭 임의 통합

이메일 제공자별 규칙을 백엔드가 임의로 적용해 서로 다른 주소를 같은 주소로 취급하지 않기 위함입니다.

## 9. 개인정보 및 접근 권한 검토 요청

`recipient`와 `recipient_normalized`는 개인정보에 해당합니다. 현재 구현은 실제 발송과 중복 판정을 위해 두 값을 평문 컬럼으로 설계했으며, 다음 조건을 전제로 합니다.

- `email_service` 스키마 접근 계정을 최소화
- 일반 분석 계정과 FastAPI 계정에는 조회 권한을 부여하지 않음
- 애플리케이션·SQL 로그에서 이메일 주소 마스킹
- 백업 및 운영 조회 권한 제한
- 보존 기간 만료 후 삭제 또는 비식별화 정책 추가 예정

DB 또는 보안 정책상 평문 저장이 허용되지 않는다면 적용 전에 알려주시기 바랍니다. 그 경우 다음 구조로 변경해야 합니다.

- `recipient_ciphertext`: 애플리케이션 AES-GCM 암호문
- `recipient_lookup_hash`: 비밀키 기반 HMAC-SHA-256
- `recipient_masked`: 운영 화면 표시용 마스킹 값

암호화 여부는 마이그레이션 적용 전에 확정해야 합니다.

## 10. 계정과 권한 권장안

가능하면 마이그레이션 계정과 런타임 계정을 분리해 주십시오.

### Flyway 마이그레이션 계정

필요 권한:

- `email_service` 스키마 생성 또는 소유
- 해당 스키마의 TABLE, INDEX, CONSTRAINT, COMMENT 생성·변경
- Flyway schema history 테이블 생성·변경

### Spring 런타임 계정

필요 권한:

- `USAGE` on schema `email_service`
- `SELECT`, `INSERT`, `UPDATE` on `email_service.email_deliveries`
- IDENTITY/SEQUENCE 사용 권한

초기 기능에서는 런타임 계정에 `DELETE`, `DROP`, `ALTER`, `CREATE` 권한이 필요하지 않습니다. 삭제·보존 정책이 확정되면 별도 배치 계정 또는 제한된 권한을 검토합니다.

FastAPI DB 계정에는 `email_service` 접근 권한을 부여하지 않는 것을 권장합니다.

## 11. Flyway 설정

현재 Spring 설정:

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
spring.flyway.schemas=email_service
spring.flyway.default-schema=email_service
spring.flyway.create-schemas=true
```

검토 요청:

1. 애플리케이션이 스키마를 생성하도록 허용할지
2. DB 담당자가 스키마를 사전 생성하고 `create-schemas=false`로 바꿀지
3. Flyway history 테이블을 `email_service` 안에 두는 것이 적절한지
4. Neon 연결 시 migration은 pooler 주소가 아닌 direct connection을 사용할지
5. 운영 배포 계정과 런타임 계정을 분리할지

권장안은 DB 담당자가 스키마와 권한을 먼저 생성하고, 별도 migration 계정으로 Flyway를 실행하며, Spring 런타임 계정에는 DML 권한만 부여하는 방식입니다.

## 12. 적용 전 확인 SQL

개발용 Neon에서 아래 항목을 먼저 확인해 주십시오.

```sql
SELECT current_database(), current_user;

SELECT schema_name
FROM information_schema.schemata
WHERE schema_name = 'email_service';

SELECT table_schema, table_name
FROM information_schema.tables
WHERE table_schema = 'email_service';
```

기존 동일 이름 객체가 있으면 마이그레이션을 실행하지 말고 충돌 여부를 먼저 확인해야 합니다.

## 13. 적용 후 검증 SQL

```sql
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = 'email_service'
  AND table_name = 'email_deliveries'
ORDER BY ordinal_position;

SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'email_service'
  AND tablename = 'email_deliveries'
ORDER BY indexname;

SELECT constraint_name, constraint_type
FROM information_schema.table_constraints
WHERE table_schema = 'email_service'
  AND table_name = 'email_deliveries'
ORDER BY constraint_name;
```

확인 대상:

- `idempotency_key` UNIQUE
- 진행 상태 partial unique index
- 상태·유형·시도 횟수 CHECK
- FastAPI `service` 스키마에 대한 FK가 없는지
- Flyway schema history가 의도한 스키마에 생성됐는지

## 14. 복구 및 롤백 주의사항

Flyway Community의 일반적인 운영 방식은 적용된 migration 파일을 수정하거나 자동 downgrade하는 것이 아니라 새 보정 migration을 추가하는 것입니다.

개발 환경에서 아직 실제 발송 이력이 없고 완전 제거가 승인된 경우에만 다음 역방향 절차를 검토할 수 있습니다.

```sql
DROP INDEX IF EXISTS email_service.uq_email_deliveries_active_request;
DROP INDEX IF EXISTS email_service.ix_email_deliveries_status_updated_at;
DROP INDEX IF EXISTS email_service.ix_email_deliveries_run_id;
DROP TABLE IF EXISTS email_service.email_deliveries;
```

주의사항:

- 운영 데이터가 존재하면 위 SQL을 실행하지 않음
- Flyway schema history 행을 임의로 삭제하지 않음
- `email_service` 스키마는 다른 객체가 없고 별도 승인된 경우에만 삭제
- 적용 전 Neon 브랜치 또는 백업을 생성하는 것을 권장

## 15. 현재 검증 상태

- Java 21 컴파일 성공
- Spring 애플리케이션 테스트 성공
- H2 PostgreSQL 호환 모드에서 JPA 저장 및 API 테스트 성공
- 동일 Idempotency-Key 중복 반환 테스트 성공
- 동일 키·다른 본문 409 테스트 성공
- 합성 샘플 5건 계약 테스트 성공
- Docker 이미지 빌드 성공
- 실제 Neon에는 미적용
- Flyway SQL의 실제 PostgreSQL/Neon 적용 검증은 DB 검토 후 진행 예정

H2 테스트는 JPA 동작을 검증하기 위한 것이며 PostgreSQL partial index까지 완전히 대체하지 않습니다. 따라서 개발용 Neon 적용 전 별도의 로컬 PostgreSQL 또는 Neon 브랜치에서 Flyway SQL 검증이 필요합니다.

## 16. DB 담당자 확인 요청사항

다음 항목에 대해 승인 또는 수정 의견을 부탁드립니다.

1. `email_service` 신규 스키마 생성 가능 여부
2. 기존 `service.deliveries` 대신 Spring 전용 테이블을 사용하는 방향
3. 교차 서비스 FK를 만들지 않는 방향
4. 컬럼 타입, 길이, CHECK 제약의 적절성
5. Idempotency-Key UNIQUE와 진행 상태 partial unique index
6. 수신 이메일 평문 저장 허용 여부 또는 암호화 필수 여부
7. 이메일 이력 보존 기간과 삭제 정책
8. Flyway migration 계정과 Spring runtime 계정 분리 가능 여부
9. Neon migration에 사용할 direct connection 정보
10. 개발용 Neon 적용 일정 및 적용 담당자

승인 전에는 `alembic upgrade`, Flyway migration 또는 Neon 콘솔 수동 SQL을 실행하지 않습니다.
