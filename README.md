# mju-ton

Spring Boot backend for MJU hackathon.

## Tech Stack

- Java 17
- Spring Boot
- Gradle
- Spring Web MVC
- Validation
- Lombok
- PostgreSQL

## Run

로컬 환경 파일을 만든 뒤 Docker Compose와 애플리케이션을 실행한다.

```bash
cp .env.example .env
docker compose up -d
set -a
source .env
set +a
./gradlew bootRun
```

`.env`에는 개인별 개발 값을 입력하며 Git에 커밋하지 않는다. 애플리케이션 DB 설정은 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경변수로 주입한다.

Health check:

```bash
curl http://localhost:8080/api/health
```

## Test

```bash
./gradlew test
```
