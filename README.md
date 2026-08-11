# Backend Spring

고객 산출물 API와 이메일 발송을 담당하는 Spring Boot 서비스입니다. FastAPI는 Agent 실행과 선별 샘플 제공을 담당합니다.

## 기술 구성

- Java 21
- Spring Boot 4.0.7
- Gradle Wrapper
- Spring Web MVC
- Validation
- Actuator
- Spring Mail

## 실행

Java 21이 설치된 환경:

API 역할은 `api` 프로필, SQS·SES 이메일 발송 역할은 `worker` 프로필로 분리한다. `INTERNAL_SERVICE_KEY`는 필수이며, 누락하면 API 시작이 실패한다.

```bash
INTERNAL_SERVICE_KEY=replace-with-a-secret ./gradlew bootRun --args='--spring.profiles.active=api'
```

Docker 환경:

```bash
# Graviton EC2 표준 이미지
docker buildx build --platform linux/arm64 -t backend-spring .

# 고객 API: SQS consumer를 실행하지 않는다.
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=api \
  -e INTERNAL_SERVICE_KEY=replace-with-a-secret \
  -e INTERNAL_API_BASE_URL=http://backend-api:8000 \
  backend-spring

# 이메일 worker: HTTP listener 없이 SQS long polling과 SES 발송만 실행한다.
docker run --rm \
  -e SPRING_PROFILES_ACTIVE=worker \
  -e EMAIL_QUEUE_BACKEND=sqs \
  -e EMAIL_REQUEST_QUEUE_URL=https://sqs.ap-northeast-2.amazonaws.com/ACCOUNT/REQUEST \
  -e EMAIL_RESULT_QUEUE_URL=https://sqs.ap-northeast-2.amazonaws.com/ACCOUNT/RESULT \
  -e EMAIL_SENDER=ses \
  -e EMAIL_FROM=no-reply@example.com \
  -e S3_ARTIFACTS_BUCKET=bigproject-dev-artifacts-ACCOUNT \
  backend-spring
```

API 헬스 체크: `GET http://localhost:8080/actuator/health/readiness`

## 서비스 책임

- API 컨테이너: 고객 API key 검증, FastAPI 내부 API 조회, S3 presigned download URL 생성
- Worker 컨테이너: SQS 요청 소비, Amazon SES 발송, SQS 결과 발행
- 로컬 개발에서는 Mailpit·Redis adapter를 선택적으로 사용

현재 운영 프로필은 Spring 자체 DB를 사용하지 않는다. 과거 이메일 접수/이력 JPA 구현은 `legacy-db` 프로필에만 남아 있으며 운영 API·Worker에는 포함하지 않는다.

AWS 역할 권한과 SES 도메인 검증 절차는 [AWS 배포 체크리스트](docs/AWS_DEPLOYMENT.md)를 따른다.
