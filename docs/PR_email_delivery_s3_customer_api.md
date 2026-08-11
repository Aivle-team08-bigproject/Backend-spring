# PR: 이메일 발송·S3 고객 API 연동

- Source: `feature/email-delivery-s3-customer-api`
- Target: `develop`
- Latest commit: `d39208a`

## 변경 목적

FastAPI가 발행한 이메일 요청을 Spring이 소비하고, 최종 산출물 S3 다운로드 링크와
고객 API 접속정보를 메일로 전달한다.

## 주요 변경

- 이메일 큐 메시지에 API URL·API Key 필드 추가
- Mailpit HTML 메일에 API 접속정보 표시
- API 정보 영역을 유효한 HTML table 구조로 구성
- 긴 URL·Key의 메일 내 줄바꿈 처리
- Amazon SES v2 sender와 client configuration 추가
- `EMAIL_SENDER=mailpit|ses` 전환 지원
- 최종 산출물 S3 Presigned URL 생성 유지

## 검증

- Docker 내부 `./gradlew clean test bootJar --no-daemon`
- Spring 이미지 빌드 성공
- Mailpit 컨테이너 재생성 및 메일 HTML 확인

## 확인 요청

- SES 발신 identity 검증 및 sandbox/prod access 상태 확인
- Spring 실행 role의 S3 read, SQS consume/publish, SES send 권한 확인
- `S3_ARTIFACTS_BUCKET`, `EMAIL_FROM`, `EMAIL_SENDER` 배포환경 주입 확인
