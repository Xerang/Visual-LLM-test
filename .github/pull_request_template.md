## 무엇을 바꿨나

<!-- 한두 줄로. 관련 이슈가 있으면 #번호 -->

## 왜

<!-- 회의 결정, 현지 조사, 버그 등 근거 -->

## 확인한 것

- [ ] `backend`: `mvn test` 통과
- [ ] `vision`: `python -m unittest discover -s tests` 통과 (AI 서버를 바꿨다면)
- [ ] `frontend`: `npm run build` 통과, 휴대폰 크기 화면에서 확인 (화면을 바꿨다면)
- [ ] 키·비밀번호·`.env`가 들어가지 않았다

## 문서

- [ ] API를 바꿨다면 `docs/03-api.md` 갱신
- [ ] DB를 바꿨다면 새 마이그레이션 파일 추가(기존 파일 수정 금지) + `docs/02-erd.md` 갱신
- [ ] 임시 설정을 바꿨다면 `docs/00-temporary-decisions.md` 갱신

## 리뷰어에게

<!-- 특히 봐 줬으면 하는 부분, 화면 캡처 등 -->
