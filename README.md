# Backend Spring

고객·업무 API와 이메일 발송을 담당하는 Spring Boot 서비스입니다. FastAPI는 Agent 실행과 선별 샘플 제공을 담당합니다.

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

```bash
./gradlew bootRun
```

Docker 환경:

```bash
docker build -t backend-spring .
docker run --rm -p 8080:8080 backend-spring
```

헬스 체크: `GET http://localhost:8080/actuator/health`

## 서비스 책임

- FastAPI 선별 샘플 조회
- 고객 이메일 발송 요청 및 권한 검증
- Mailpit 또는 Amazon SES 발송
- 발송 이력, 상태 조회, 중복 방지 및 재발송

DB 스키마와 인증 계약은 팀 합의 후 추가합니다.
