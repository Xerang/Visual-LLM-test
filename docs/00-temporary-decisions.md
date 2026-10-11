# 임시 결정 목록 (실증 전 변경 대상)

- 작성일: 2026-10-11
- 원칙: 지금은 **전체 서비스가 끝까지 돌아가는 것**을 먼저 만든다. 아래 항목은 개발 속도를 위해 임시로 정한 것이다. **현지 실증 전에 모두 다시 정한다.**
- 각 항목은 **설정이나 기본 데이터만 바꾸면 되게** 만든다. 코드 구조나 테이블 구조를 갈아엎지 않아도 되는 것이 목표다.
- 코드 위치는 리팩토링 후 실제 경로로 갱신했다(2026-10-11).

| # | 항목 | 지금(임시) | 실증 전 | 바꿀 곳 | 근거 |
|---|---|---|---|---|---|
| 1 | **로그인** | 목 로그인: dev 전용 API에서 시드 사용자 id만으로 로그인. 비밀번호 없음 | 전화번호 + 비밀번호 또는 SMS OTP 중 결정 | `app.auth.mode`(application-dev.yml), `DevAuthController` 제거 후 같은 `TokenService`로 토큰을 주는 로그인 API 추가, 프런트 `pages/Login.jsx`만 교체, `users.password_hash` 채움(OTP면 인증 테이블 추가) | [조사 §7](01-tajikistan-research.md#7-전화번호인증) |
| 2 | **운영 언어** | `ko`. 사용자 기본 언어와 AI 출력 언어 모두 한국어 | `tg` 또는 `ru` (필로 확인 질문 1번) | 서버: `app.default-locale` 한 곳(DB에는 기본값 없음). 화면: `frontend/src/format.js` 라벨과 각 화면 문구(#10) | [조사 §2](01-tajikistan-research.md#2-언어) |
| 3 | **상점 템플릿** | `GRID`/`LIST`/`SHOWCASE` 3종(진열대형·가격표형·공방형) | Stitch 프로토타입과 현지 판매자 피드백으로 확정 | `frontend/src/pages/StorePage.jsx`의 `GridTemplate`·`ListTemplate`·`ShowcaseTemplate`. 코드 이름이 바뀌면 `StoreTemplate` enum과 `stores.template` CHECK 수정 | [ERD](02-erd.md#상점-템플릿-v1-임시) |
| 4 | **카테고리 트리** | 대분류 9개 / 말단 41개, 이름은 한국어만 | 실제 판매 게시물로 검증한 뒤 조정. 타직어·러시아어 이름 추가 | 새 버전 마이그레이션 파일(`V3__...`)로 추가. `V2__reference_data.sql`은 고치지 않는다 | [ERD](02-erd.md#카테고리-트리-v1-임시) |
| 5 | **목 데이터** | 가상 사용자 6명, 상점 3곳, 상품 6개(dev 프로필만) | 시연용 실데이터(상점 5곳, 상품 100개 이상, 2차 지표)로 교체 | `backend/src/main/resources/db/dev/R__dev_mock_data.sql` (dev 프로필에서만 적용) | 2차 회의 |
| 6 | **이미지 저장소** | 로컬 디스크 (`storage_key` = 파일 경로) | S3 등 오브젝트 스토리지 | `ImageStorage` 인터페이스의 새 구현체(지금은 `LocalImageStorage`)와 `ImageService.urlOf`. DB는 그대로 | 2차 아키텍처 |
| 7 | **AI 모델·한도** | Gemini 무료 키 (일일 요청 한도 있음) | 정답셋 100장 평가와 시연이 가능한 한도 확보(유료 또는 지원) | `GEMINI_MODEL`, API 키 | PoC README의 "20 requests per day" 예시 |
| 8 | **토큰** | JWT 7일, 리프레시 토큰 없음 | 짧은 액세스 토큰 + 리프레시 토큰(필요하면 `refresh_tokens` 테이블) | 토큰 발급 서비스, 보안 설정 | [API §2](03-api.md#2-인증-목-로그인) |
| 9 | **사진 접근** | 공개 사진(판매 중 상품, 공개 상점 로고·배너)만 서명 없이 조회. 나머지는 주인 응답에 담긴 서명 주소(1~2시간)로만 조회(2026-10-12). 서명 키는 JWT 키를 함께 씀 | S3로 바꿀 때(#6) S3 서명 URL로 교체, 서명 키를 JWT 키와 분리 | `ImageUrlSigner`, `ImageService.serve`·`isPublic`, 저장소 구현체 | [API §5.3](03-api.md#53-사진) |
| 10 | **화면 문구** | 한국어를 화면 코드에 직접 씀. 상태·단위 라벨만 `format.js`에 모음 | 운영 언어로 번역(i18n 라이브러리 도입 검토) | `frontend/src/format.js`, `frontend/src/pages/**` | 임시 결정 #2 |
| 11 | **검수 기본값** | AI 후보를 모두 "수락" 상태로 보여줌(빠른 등록 우선) | 실증에서 판매자가 확인 없이 넘기는지 관찰. 그렇다면 기본값을 "미결정"으로 바꿔 직접 고르게 함(정밀도 지표가 부풀려지지 않게) | `frontend/src/pages/seller/ScanJob.jsx`의 `newRow` | 2차 지표(등록 30초) |
| 12 | **가짜 AI 모드** | `VISION_MODE=fake`면 Gemini 없이 고정 후보를 줌. 캡션에 `#fail429`·`#fail`을 넣으면 실패를, `#empty`를 넣으면 "상품 못 찾음"을 흉내 냄 | 실증·시연에서는 반드시 `http`(기본값) | `app.vision.mode` | 무료 키 일일 한도 |

## 실증 전 체크리스트

- [ ] 필로 확인 질문 7개에 답을 받음 ([조사 문서 끝](01-tajikistan-research.md#필로참여기업에-확인할-질문))
- [ ] 운영 언어 결정 → `app.default-locale` 변경, 카테고리·UI 문구 번역(타직어는 필로 검수)
- [ ] 실제 로그인 방식 구현, dev 로그인 API와 목 데이터 제거 확인
- [ ] 템플릿 3종을 현지 판매자에게 보여주고 피드백 반영
- [ ] 실제 판매 게시물로 카테고리 정확도(소분류·대분류)를 측정하고 트리 조정
- [ ] 이미지 저장소를 S3로 전환하고 EXIF 제거 확인
- [ ] 가입 화면에 국외 전송 동의 문구 넣기(개인정보보호법 No.1537)
- [ ] AI 사용 한도 확보, `VISION_MODE`가 `http`인지 확인
- [ ] 화면 문구 번역(#10), 검수 기본값 재검토(#11)
