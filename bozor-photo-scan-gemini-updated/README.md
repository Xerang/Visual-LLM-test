# BOZOR — AI 상품 등록 자동화 기반 타지키스탄 로컬 커머스

판매자는 상품 사진(또는 SNS 게시물)만 올리고, AI가 만든 초안을 **확인·수정만** 해서 상품을 등록한다. 구매자는 시장·카테고리로 상품을 찾아 상점별로 주문한다(결제는 대면·송금).

| 폴더 | 내용 | 주소 |
|---|---|---|
| `backend/` | Spring Boot 3.5 · Java 17 · PostgreSQL · Flyway. API 전체와 AI 스캔 워커 | `http://localhost:8080` (API 문서: `/swagger-ui.html`) |
| `vision/` | FastAPI · Gemini. 사진 1~3장 + 캡션 → 상품 후보 JSON | `http://localhost:8000` |
| `frontend/` | React 19 · Vite. 구매자·판매자·관리자 화면 | `http://localhost:5173` |
| `docs/` | [현지 조사](docs/01-tajikistan-research.md) · [ERD](docs/02-erd.md) · [API 명세](docs/03-api.md) · [임시 결정](docs/00-temporary-decisions.md) · [후보고](docs/04-refactor-report.md) | |
| `legacy/poc/` | 이전 PoC 코드(참고용, 빌드하지 않음) | |

```
React ──/api──▶ Spring Boot ──▶ PostgreSQL
                    │  ▲
         스캔 워커 ──┘  └── 사진 파일(로컬 디스크 → 나중에 S3)
                    │
                    └──▶ FastAPI ──▶ Gemini
```

## 준비물

- Java 17 이상, Maven(또는 IntelliJ 내장 Maven), Node.js 20 이상, Python 3.10 이상
- PostgreSQL 16 이상
- (실제 AI를 쓸 때만) Gemini API 키 — 없으면 **가짜 AI 모드**로 전체 흐름을 돌려볼 수 있다

## 1. DB 만들기

**UTF-8 로케일 DB가 꼭 필요하다.** `C` 로케일이면 키릴 문자 검색이 항상 0건이 되므로, 마이그레이션이 일부러 실패하게 해 두었다.

```sql
CREATE DATABASE bozor TEMPLATE template0 ENCODING 'UTF8';
CREATE DATABASE bozor_test TEMPLATE template0 ENCODING 'UTF8';   -- 테스트용
```

확인: `SELECT show_trgm('Курага');` 결과가 `{}`가 아니면 된다.

## 2. 실행 (터미널 3개)

**백엔드** — 프로필을 정하지 않으면 `dev`로 뜬다(목 로그인 + 목 데이터 자동 적용).

```powershell
# Windows PowerShell
cd backend
$env:DB_URL = 'jdbc:postgresql://localhost:5432/bozor'
$env:DB_USER = 'postgres'
$env:DB_PASSWORD = '비밀번호'
$env:VISION_MODE = 'fake'        # Gemini 없이 개발할 때. 실제 AI를 쓰려면 이 줄을 빼고 AI 서버를 띄운다
mvn spring-boot:run              # mvn 이 없다고 나오면 Maven 설치 폴더의 bin\mvn.cmd 전체 경로로 실행
```

```bash
# macOS / Linux
cd backend
DB_URL=jdbc:postgresql://localhost:5432/bozor DB_USER=postgres VISION_MODE=fake mvn spring-boot:run
```

**AI 서버** (가짜 AI 모드면 생략)

```powershell
# Windows PowerShell
cd vision
py -3 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
Copy-Item .env.example .env      # 메모장으로 열어 GEMINI_API_KEY= 뒤에 키를 넣는다
.\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8000
```

```bash
# macOS / Linux
cd vision
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
cp .env.example .env             # GEMINI_API_KEY= 뒤에 키를 넣는다
.venv/bin/python -m uvicorn app.main:app --port 8000
```

- 키는 [Google AI Studio](https://aistudio.google.com/app/apikey)에서 만든다. `vision/.env`는 `.gitignore`에 들어 있어 저장소에 올라가지 않는다. **키를 코드·채팅·문서에 붙이지 말 것.**
- 확인: `http://localhost:8000/health`에서 `"key_configured": true`면 키가 읽힌 것이다(키 값 자체는 보여주지 않는다).
- `.env`를 고쳤으면 **AI 서버를 껐다가 다시 켠다.** 키는 시작할 때 한 번만 읽는다.
- **무료 키는 하루 요청 수에 한도가 있다.** 화면 개발이나 연습은 가짜 AI 모드로 하고, 실제 키는 측정할 때만 쓴다. 한도를 넘으면 앱이 30초·60초 뒤에 다시 시도하고, 그래도 안 되면 "분석 실패 → 다시 시도"로 안내한다.

**프런트엔드**

```bash
cd frontend
npm install
npm run dev
```

`http://localhost:5173` → **로그인**에서 사용자를 고른다(비밀번호 없음).

| 목 사용자 | 해 볼 것 |
|---|---|
| 판매자 파르호드 (진열대형 상점) | 사진으로 상품 등록 → 검수 → 발행, 재고 [−][+], 주문 접수 |
| 판매자 닐루파르 (가격표형) · 루스탐 (공방형) | 템플릿별 상점 화면 |
| 구매자 마디나 | 검색 → 장바구니 → 주문 → 취소 |
| 관리자 | 지표 화면(카테고리 정확도·등록 소요시간 등) |

가짜 AI 모드에서 캡션에 `#fail429`를 넣으면 "요청 한도 초과 → 30초 뒤 재시도", `#fail`을 넣으면 "분석 실패 → 다시 시도" 화면을 확인할 수 있다.

## 3. 설정 (환경 변수)

| 변수 | 기본값 | 설명 |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/bozor`, `postgres`, (없음) | DB 접속 |
| `VISION_MODE` | `http` | `http` = FastAPI 호출, `fake` = Gemini 없이 고정 후보 |
| `VISION_SERVICE_URL` | `http://localhost:8000` | AI 서버 주소 |
| `STORAGE_DIR` | `./data/images` | 사진 저장 폴더 |
| `JWT_SECRET` | dev 프로필에만 개발용 값 | 32바이트 이상. dev 외 프로필에서는 꼭 설정 |
| `AUTH_MODE` | dev 프로필에서 `mock` | 목 로그인 켜기/끄기 |
| `GEMINI_API_KEY`, `GEMINI_MODEL` | (`vision/.env`) | AI 서버용 |

운영 언어(지금 한국어), 목 로그인 같은 **임시 설정은 실증 전에 바꾼다** → [임시 결정 목록](docs/00-temporary-decisions.md).

## 4. 테스트

```bash
cd backend && mvn test                                   # 36개. TEST_DB_URL 로 테스트 DB 지정 가능(기본 bozor_test)
cd vision && .venv/bin/python -m unittest discover -s tests   # 5개. Gemini 호출은 가짜로 대체
cd frontend && npm run build                             # 빌드 확인
```

백엔드 테스트는 실제 PostgreSQL을 쓴다. jsonb·pg_trgm·`FOR UPDATE SKIP LOCKED`는 H2 같은 메모리 DB로 흉내 낼 수 없어서다.

## 5. 자주 막히는 곳

| 증상 | 원인과 해결 |
|---|---|
| 백엔드 시작 시 `DB LC_CTYPE(C)가 키릴 문자를 지원하지 않습니다` | DB 로케일 문제. 1번 명령(`TEMPLATE template0 ENCODING 'UTF8'`)으로 DB를 다시 만든다 |
| 백엔드 시작 시 `JWT_SECRET ... 32바이트 이상` | dev 프로필이 아니다. 프로필을 지정하지 않거나 `JWT_SECRET`을 32자 이상으로 설정한다 |
| 백엔드 시작 시 `Schema-validation` 오류 | 엔티티와 테이블이 다르다. 이미 적용된 마이그레이션 파일을 고쳤다면 되돌리고 새 버전 파일로 추가한다 |
| 스캔이 계속 "대기 중"에 "AI 요청 한도" 메시지 | 무료 키 한도 초과(429). 다음 날 다시 하거나 가짜 AI 모드로 개발한다 |
| 스캔이 "AI 서버에 연결하지 못했습니다"로 재시도 | AI 서버(8000)가 꺼져 있다. 띄우거나 `VISION_MODE=fake`로 실행한다 |
| 로그인 화면에 "목 로그인이 꺼져 있습니다" | 백엔드가 dev 프로필이 아니다 |

## 6. 코드 구조 (백엔드)

기능별 패키지 안에 Controller → Service → Repository 순서로 나눴다.

| 패키지 | 내용 |
|---|---|
| `auth`, `user` | 목 로그인·JWT·`@LoginUser`, 내 정보 |
| `catalog` | 카테고리 트리(2단계)·시장 |
| `image` | 사진 정규화(EXIF 제거·축소·썸네일)와 저장소 |
| `store` | 판매자 상점 관리(`/seller/stores`)와 공개 상점(`/stores`) |
| `scan` | AI 스캔 요청·큐·워커·검수 확정, `vision/`(FastAPI 호출·가짜 AI) |
| `product` | 판매자 상품 관리와 구매자 검색 |
| `order` | 주문·상태 변경·선택적 재고 차감 |
| `admin` | 2차 회의 검증 지표 |
| `common` | 설정, 오류 형식, 페이지, 언어 |

DB 테이블은 Flyway(`src/main/resources/db/migration`)가 만들고, Hibernate는 엔티티와 테이블이 맞는지 검사만 한다. **이미 적용된 마이그레이션 파일은 고치지 말고 새 버전 파일을 추가한다.**
