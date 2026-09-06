# Backend Spring

고객 산출물 전달과 이메일 알림을 담당하는 Spring Boot 서비스입니다. FastAPI의 데이터 처리 결과와 연동해 다운로드 가능한 산출물을 사용자에게 전달합니다.

## Responsibilities

- 고객용 산출물 조회 API
- 다운로드 URL 생성 및 전달 흐름
- 이메일 발송 worker와 결과 상태 처리
- 로컬 개발용 메일 adapter 지원

## Tech stack

Java 21, Spring Boot, Gradle, Spring Web MVC, Validation, Actuator, Spring Mail

## Local development

```bash
./gradlew bootRun --args='--spring.profiles.active=api'
./gradlew test
./gradlew build
```

필수 환경 변수와 외부 연동 값은 로컬 환경에만 설정합니다. 비밀값·접속 정보는 저장소에 포함하지 않습니다.

## Profiles

- `api`: 고객 산출물 API 제공
- `worker`: 이메일 전달 작업 처리

## Related repositories

- [Backend-fastapi](https://github.com/Aivle-team08-bigproject/Backend-fastapi): 데이터 요청·AI 파이프라인 API
- [Frontend_2](https://github.com/Aivle-team08-bigproject/Frontend_2): 사용자·운영자 웹 UI
