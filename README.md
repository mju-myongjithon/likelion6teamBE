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

`.env`에는 개인별 개발 값을 입력하며 Git에 커밋하지 않는다. 애플리케이션 DB 설정은
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`로 주입하고, AI 추천은 `OPENAI_API_KEY`,
`OPENAI_MODEL`로 설정한다. 기본 추천 모델은 `gpt-5.6-terra`다.

Health check:

```bash
curl http://localhost:8080/api/health
```

로그인 세션과 프로필이 있는 사용자는 다음 API로 모임·행사 추천을 조회한다.

```bash
curl --cookie "JSESSIONID=..." \
  "http://localhost:8080/api/recommendations?filter=ALL&limit=10"
```

OpenAI 호출이 실패하거나 키가 없으면 같은 API가 규칙 기반 추천을 반환한다.

## Test

```bash
./gradlew test
```
