# BOZOR Gemini 상품 사진 스캔 실험

사진 한 장을 촬영하거나 선택하면 **React → Spring Boot → FastAPI → Gemini Vision** 순서로 보내 상품 후보와 JSON 초안을 만듭니다. Spring Boot가 원본 사진과 분석 결과를 PostgreSQL에 기록하고, 판매자가 고친 상품도 같은 기록에 저장합니다. 이전 YOLO 파일과 별개의 폴더로 실행합니다.

| 폴더 | 역할 | 기본 주소 |
| --- | --- | --- |
| `frontend/` | 촬영·선택, 후보 표시, 판매자 수정과 JSON 다운로드 | `http://localhost:5173` |
| `backend/` | 사진 검사, FastAPI 전달, PostgreSQL 저장·조회 | `http://localhost:8080` |
| `vision/` | 이미지 검사·축소 후 Gemini API를 호출하고 JSON을 검증 | `http://localhost:8000` |

이 버전에는 로그인, 실제 상품 게시, 구매·주문 기능이 없습니다. 로컬 실험용으로 Spring Boot는 `127.0.0.1`에만 연결됩니다. **Gemini 모델의 내부 학습 데이터나 가중치를 얻는 것은 아닙니다.** 저장하는 것은 우리가 보낸 사진, Gemini의 추론 결과, 판매자가 직접 수정한 결과입니다. 이후 성능 평가나 학습용 데이터셋 정리에 사용할 수 있습니다.

## 준비

- Windows에서 Python 3.10 이상 (`py -0p`), Java 17 이상 및 Maven, Node.js와 npm이 필요합니다. JDK 23도 사용할 수 있으며, `mvn -v`가 표시하는 Java가 17 이상이어야 합니다.
- [Google AI Studio](https://aistudio.google.com/app/apikey)에서 Gemini API 키를 준비합니다. 키는 `vision/.env`에만 넣고 React 코드나 Git 저장소에 올리지 마세요.
- 기본 모델은 `gemini-3.5-flash`입니다. 조직 계정의 사용 권한·할당량에 따라 `vision/.env`의 `GEMINI_MODEL`을 접근 가능한 **이미지 입력과 구조화 출력을 지원하는 모델**로 바꿀 수 있습니다.
- PostgreSQL 서버와 **접속 가능한 기존 데이터베이스**가 필요합니다. Spring Boot 연결 계정에 `bozor_poc` 스키마를 생성할 권한이 있어야 합니다. 이미 같은 이름의 스키마를 쓰고 있다면 마이그레이션 적용 전 구성을 확인하세요.

## Windows PowerShell에서 실행

**첫 번째 PowerShell: FastAPI**

```powershell
cd C:\workspace\local\bozor-photo-scan-gemini-updated\vision
py -3 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade -r requirements.txt
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
notepad .env
python -m uvicorn app.main:app --reload --port 8000
```

`notepad .env`에서 `GEMINI_API_KEY=replace_with_your_key`의 오른쪽 부분만 실제 키로 바꾸고 저장합니다. 기존 YOLO 데모의 `.venv`에 `ultralytics`를 설치할 필요는 없습니다. 활성화가 안 되면 `.\.venv\Scripts\python.exe -m pip install -r requirements.txt`와 `.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --port 8000`처럼 실행하세요. `http://localhost:8000/health`에서 `key_configured`가 `true`인지 확인합니다. 이는 키 문자열 설정 여부만 확인하며 실제 API 사용 성공은 분석 요청으로 검증합니다.

`.env`를 실행 중에 고쳤다면 **FastAPI를 Ctrl+C로 중지하고 다시 시작**하세요. `--reload`는 `.env` 변경을 자동 반영하지 않을 수 있습니다.

**이미 설치한 환경에서 `legacy Interactions API schema is no longer supported`라는 400 오류가 발생하면** 첫 번째 PowerShell에서 아래 명령으로 `google-genai`를 2.x로 올리고 FastAPI를 다시 시작하세요. 이전 배포본의 `requirements.txt`에는 1.x 제한이 잘못 들어 있었습니다. `.env`와 API 키를 다시 만들 필요는 없습니다.

```powershell
cd C:\workspace\local\bozor-photo-scan-gemini-updated\vision
.\.venv\Scripts\python.exe -m pip install --upgrade "google-genai>=2.0,<3"
.\.venv\Scripts\python.exe -m pip show google-genai
```

`pip show`의 `Version`이 `2.`로 시작하는지 확인하세요. 기존 서버를 Ctrl+C로 종료한 후 `python -m uvicorn app.main:app --reload --port 8000`으로 다시 실행합니다. 이 오류는 SDK 버전 문제로, API 키 자체의 오류가 아닙니다.

사진 분석은 Gemini 응답을 기다려야 합니다. 기본 설정은 Gemini HTTP 요청 제한 **240초, 자동 재시도 없음**, Spring Boot의 FastAPI 응답 대기 **300초**입니다. 첫 번째 PowerShell에 `Gemini scan started`가 보인 뒤 `Gemini scan completed`가 보이지 않으면 Gemini 요청이 아직 진행 중입니다. Gemini 응답 시간이 초과되면 FastAPI가 HTTP 504를 반환합니다. 다른 제한이 필요하면 `vision/.env`의 `GEMINI_TIMEOUT_MS`(밀리초)와 Spring Boot 실행 전 `$env:VISION_READ_TIMEOUT_SECONDS`(초)를 바꾸되, Spring Boot 값을 Gemini 값보다 크게 설정하세요.

**두 번째 PowerShell: Spring Boot**

기존 PostgreSQL의 데이터베이스 이름·계정으로 설정합니다. 아래 `my_existing_db`는 실제 DB 이름으로 바꾸세요. 계정 정보는 예시 파일이나 프런트엔드에 입력하지 않습니다.

```powershell
cd C:\workspace\local\bozor-photo-scan-gemini-updated\backend
$env:DB_URL = 'jdbc:postgresql://localhost:5432/my_existing_db'
$credential = Get-Credential -Message 'PostgreSQL 계정'
$env:DB_USER = $credential.UserName
$env:DB_PASSWORD = $credential.GetNetworkCredential().Password
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-23'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
xxx  mvn -v
& "C:\workspace\tools\apache-maven-3.9.16\bin\mvn.cmd" -v
xxx  mvn spring-boot:run
& "C:\workspace\tools\apache-maven-3.9.16\bin\mvn.cmd" spring-boot:run
```

`mvn` 명령이 없다면 설치된 Maven의 `bin\mvn.cmd` 전체 경로로 실행하세요. 최초 실행 시 Flyway가 기존 데이터베이스 안에 **별도의 `bozor_poc` 스키마와 `scan_records` 테이블**을 생성합니다. 데이터베이스 자체는 생성하지 않습니다. 기존 테이블을 지우는 명령은 없습니다. `http://localhost:8080/api/health`에서 `spring-ok`가 보이면 준비되었습니다. 접속 정보가 틀리거나 스키마 생성 권한이 없으면 Spring Boot 시작 로그에 DB 오류가 표시됩니다.

**세 번째 PowerShell: React**

```powershell
cd C:\workspace\local\bozor-photo-scan-gemini-updated\frontend
npm install
npm run dev
```

`http://localhost:5173`에 접속해 사진을 고른 뒤 **상품 사진 스캔**을 누릅니다. 분석 성공 시 PostgreSQL에 원본 기록이 자동 저장되고, 화면에 기록 번호가 보입니다. 사진에 등장한 독립 상품 후보를 모두 선택하고, 누락된 상품은 **누락 상품 직접 추가**로 입력한 뒤 상품별 값을 고쳐 함께 저장하세요. 카메라 기능은 `localhost` 또는 HTTPS에서 브라우저 권한이 필요합니다.

## 반환 JSON과 검수

`POST /api/analyze`에 `file`이라는 이름으로 JPEG, PNG, WEBP 사진 한 장을 보내며, 최대 크기는 5MB입니다. FastAPI가 이미지를 실제 파일 내용으로 확인하고 방향을 보정한 뒤 최대 2048픽셀 JPEG로 변환해 Gemini에 보냅니다. 촬영 위치 등 메타데이터는 전달하지 않습니다.

```json
{
  "candidates": [
    {
      "name": "손잡이가 있는 컵",
      "category": "household",
      "description": "둥근 손잡이가 달린 갈색 컵이 보입니다.",
      "visible_text": [],
      "review_notes": ["정확한 재질은 사진만으로 확인할 수 없습니다."],
      "price_amount": null,
      "stock_quantity": null
    }
  ],
  "review_notes": ["AI 초안입니다. 독립 상품 후보를 모두 확인하고 이름·설명·문자 인식을 검수해 주세요."],
  "source": "gemini",
  "model": "gemini-3.5-flash",
  "prompt_version": "product_scan_v2",
  "elapsed_ms": 1200,
  "record_id": 1
}
```

위 값은 **형태를 설명하는 예시**이고 실제 사진의 결과나 성능 수치가 아닙니다. `record_id`는 PostgreSQL 저장에 성공한 뒤 Spring Boot가 추가합니다. v2 프롬프트는 단일·복수 사진의 전경 상품을 각각 후보로 만들고, 화면에 반사된 촬영 스마트폰이나 화면 속 이미지·UI를 상품에서 제외하도록 지시합니다. AI 결과는 오차가 있을 수 있으므로 판매자가 후보를 제거하거나 누락 상품을 직접 추가해야 합니다. 이미지를 읽을 수 없으면 HTTP 400, 크기 초과는 413, 키 미설정은 503, Gemini 요청 한도는 429, 대기 시간 초과는 504, 기타 Gemini 요청 또는 응답 문제는 502가 반환됩니다. DB 저장이 실패하면 분석 결과를 성공으로 표시하지 않고 HTTP 503을 반환합니다.

## 저장 데이터와 조회

`bozor_poc.scan_records`의 주요 열:

| 열 | 저장하는 값 |
| --- | --- |
| `image_data`, `image_mime`, `image_sha256` | 업로드한 원본 사진의 바이트·형식·SHA-256. 사진을 DB에 저장하므로 사용량에 주의하세요. |
| `raw_scan` (`jsonb`), `model`, `prompt_version` | Gemini가 만든 후보·검토 사항과 모델·프롬프트 버전. 판매자 수정으로 덮어쓰지 않습니다. |
| `reviewed_products` (`jsonb`), `reviewed_at` | 한 사진에서 확정한 여러 상품·검수 시각. 후보에서 온 상품은 `candidate_index`, 직접 추가한 상품은 `null`을 사용합니다. |
| `selected_index`, `reviewed_product` (`jsonb`) | v1 단일 선택 기록을 읽기 위한 옛 열. v2로 다시 저장하면 이 값은 비워집니다. |
| `schema_version`, `created_at` | 향후 데이터 구조 변경을 위한 버전과 생성 시각. |

기록 조회 `GET /api/scan-records/{record_id}`, 원본 사진 `GET /api/scan-records/{record_id}/image`, 다중 상품 검수 저장 `PUT /api/scan-records/{record_id}/reviews`를 사용할 수 있습니다. 기존 `PATCH /api/scan-records/{record_id}/review`는 옛 클라이언트용입니다. **DB에는 업로드한 원본 사진이 보존되어 촬영 위치 등의 메타데이터가 남을 수 있습니다.** 로컬 실험 후 실제 서비스에서는 접근 권한과 보관 기간을 정해야 합니다. 검수 요청 예시는 다음과 같습니다. 가격과 재고는 사진에서 생성한 값이 아니라 판매자가 입력한 값입니다.

```json
{
  "reviewed_products": [
    {
      "candidate_index": 0,
      "product": {
        "name": "손잡이 컵",
        "category": "household",
        "description": "둥근 손잡이가 있는 컵",
        "visible_text": [],
        "review_notes": [],
        "price_amount": 12000,
        "stock_quantity": 5
      }
    },
    {
      "candidate_index": null,
      "product": {
        "name": "AI가 누락한 별도 상품",
        "category": "other",
        "description": "판매자가 직접 확인해 입력",
        "visible_text": [],
        "review_notes": [],
        "price_amount": null,
        "stock_quantity": null
      }
    }
  ]
}
```

PowerShell에서 `curl.exe http://localhost:8080/api/scan-records/1`로 기록을 확인할 수 있습니다. 기록 #5를 정정하려면 React의 **기존 스캔 다시 검수**에 5를 넣고 불러와 실제 태블릿 후보만 선택한 다음 저장합니다. `raw_scan`은 원본으로 남고 `reviewed_products`가 확정본이 됩니다. v2에는 Flyway `V2__add_reviewed_products.sql`이 포함되어 Spring Boot 시작 때 열을 추가합니다. 기존 DB를 삭제할 필요가 없습니다. 나중에 서버를 옮길 때는 PostgreSQL의 `pg_dump`/`pg_restore`로 **`bozor_poc` 스키마**를 백업·복원하면 사진과 JSON 및 마이그레이션 이력이 함께 이동합니다. 대상 서버의 **빈 데이터베이스**에 먼저 복원하고 그다음 Spring Boot를 시작하세요. 접속 정보를 바꾼 예시:

```powershell
pg_dump -h localhost -U postgres -d my_existing_db -n bozor_poc -Fc -f bozor_scans.dump
pg_restore -h new_host -U new_user -d new_empty_db --no-owner bozor_scans.dump
```

데이터가 커지면 사진은 별도 파일 저장소로 옮기고 DB에는 위치·해시를 두는 방식으로 변경할 수 있습니다.

### 분석 오류 확인

세 서버가 켜진 상태에서 PowerShell로 다음을 순서대로 확인하세요. 사진 경로는 실제 파일로 바꿉니다.

```powershell
curl.exe -i http://localhost:8000/health
curl.exe -i http://localhost:8080/api/health
curl.exe -i -F "file=@C:\path\to\product.jpg" http://localhost:8000/analyze
curl.exe -i -F "file=@C:\path\to\product.jpg" http://localhost:8080/api/analyze
```

- `8000/health`에 연결되지 않으면 FastAPI가 기동되지 않은 것입니다. `key_configured=false`라면 `vision/.env`와 실행 중인 프로세스를 확인하세요.
- `8000/analyze`에서 401·403 메시지가 나오면 키나 프로젝트 접근 권한, 404면 설정한 모델 접근 가능 여부를 확인하세요. 429면 [Google AI Studio 사용량·한도](https://ai.dev/rate-limit)에서 현재 프로젝트와 모델을 확인하세요. 예를 들어 응답에 `20 requests per day`와 `retry in 13s`가 함께 나와도, 13초 후에 일일 한도가 반드시 풀린다는 의미는 아닙니다. 한도 갱신 후 재시도하세요. Gemini의 오류는 안전한 한국어 메시지로 변환되어 반환됩니다. 429로 실패한 사진은 저장되지 않으며, 이전에 성공한 스캔 기록은 남습니다.
- FastAPI 직접 호출은 성공하는데 React만 실패하면 Spring Boot의 `VISION_SERVICE_URL`과 8080 포트를 확인하세요. 수정판의 Spring Boot는 FastAPI가 반환한 구체적인 오류를 화면에 전달합니다.
- `8000/analyze`는 성공하지만 `8080/api/analyze`가 실패한다면 두 번째 PowerShell의 `FastAPI HTTP request or response processing failed` 또는 `FastAPI response was not a valid AnalysisResponse JSON` 이후 **첫 번째 `Caused by:`**를 확인하세요. Spring Boot는 이제 해당 예외를 로그에 남깁니다. 8000을 직접 호출하는 검사는 Gemini에 한 번 더 요청을 보냅니다.
- 두 명령의 결과에도 원인이 드러나지 않으면 **첫 번째 PowerShell의 마지막 예외 종류와 오류 코드**를 확인하세요. API 키 값은 공유하지 마세요.

Gemini의 상품명, 설명, 보이는 문자도 잘못될 수 있으므로 판매자가 확인해야 합니다. 가격과 재고는 스캔 응답에서 항상 `null`이며 판매자가 검수 화면에서 입력합니다. `JSON 저장` 버튼은 검수 초안을 파일로 내려받고, **상품 검수 확정 저장** 버튼은 서버 기록을 갱신합니다. 이 데모는 상품을 발행하지 않습니다.

## 실험 방법과 확장 지점

1. 현재 9개 스캔을 단일 상품과 여러 독립 상품 사진으로 구분하고, 각 사진에서 의도적으로 찍은 상품이 후보에 빠짐없이 포함됐는지 확인합니다.
2. 반사된 촬영 스마트폰은 검수 결과에서 제외하고, 화면 속 UI 문자가 상품 라벨에 섞이지 않았는지 확인합니다. 생성된 후보의 카테고리·설명·문자도 사진과 비교해 고칩니다.
3. `raw_scan`과 `reviewed_products`를 비교해 누락·오탐·수정 내용을 남깁니다. 프롬프트 v2의 효과를 확인하려면 같은 사진을 다시 스캔해야 하며 Gemini 호출 수와 소요 시간을 기록하세요. 실제 서비스 단계에서 사용자 인증, 사진 보관 정책, `POST /api/v1/stores/{storeId}/scan-jobs`, 상품 발행 흐름을 설계합니다. 이 경로들은 **이번 데모의 구현 API가 아니라 후속 설계 대상**입니다.

공식 자료: [Gemini 이미지 입력](https://ai.google.dev/gemini-api/docs/image-understanding), [구조화 JSON 출력](https://ai.google.dev/gemini-api/docs/structured-output), [API 키](https://ai.google.dev/gemini-api/docs/api-key).
