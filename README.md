# Tutorkim Backend

김조교 백엔드 서버입니다.

김조교는 과외 선생을 위한 튜터링 OS를 목표로 합니다. 1차 MVP는 수학 과외 선생이 학생, 수업 일정, 숙제/테스트, 문제은행, 오답 기록, 학생 리포트를 한 흐름에서 관리할 수 있도록 만드는 데 집중합니다.

## Stack

- Kotlin
- Spring Boot
- PostgreSQL
- Redis
- JPA
- Gradle

## Development

```bash
./gradlew test
./gradlew bootRun
```

기본 설정은 `src/main/resources/application.yml`에 있으며, 로컬/서버 환경값은 `.env` 또는 환경변수로 주입합니다.
