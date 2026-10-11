# 타지키스탄 현지 조사 (개발 참고용)

- 조사일: 2026-10-11
- 목적: 설계·개발 결정이 현지 상황과 어긋나지 않도록 근거를 한곳에 모은다.
- 읽는 법: 항목마다 **사실 → 설계 반영 → 현지 확인 필요** 순서로 정리했다. 출처가 비공식이거나 오래된 내용은 따로 표시했다.
- 공개 자료로 확인하지 못한 내용은 마지막의 [필로 확인 질문](#필로참여기업에-확인할-질문)에 모았다.

---

## 1. 인터넷·기기 환경

**사실**
- 인터넷 사용자는 615만 명으로, 보급률은 56.8%다(2025년 말). 모바일 회선은 1,090만 개로 인구의 101%다. [DataReportal 2026]
- 모바일 인터넷 속도는 143개국 중 139위다(2025년 초). [dig.watch] 측정치는 중앙값 약 8 Mbps(Ookla, 2023)이고, 통신사별로는 Tcell 12.1 Mbps, ZET-Mobile 4.4 Mbps 수준이다. [DataReportal 2023, Speedgeo]
- 정부는 2028년까지 인터넷 접근률을 80%로 높이겠다고 발표했다. 다만 요금 부담은 중앙아시아에서 가장 높은 편이다. [trade.gov ICT]
- 통신사들이 특정 SNS를 무료 데이터로 묶어 파는 경우가 많아서, 이런 SNS가 주요 정보 통로 역할을 한다. [The Diplomat 2025-10]

**설계 반영**
- 사진은 **업로드 전에 클라이언트에서 줄인다**(PoC는 서버에서만 줄임). 목록 화면은 썸네일만 보여준다(`images.thumbnail_key`).
- AI 분석은 **비동기 Job**으로 처리한다(`scan_jobs`). 느린 회선에서 요청 하나를 수십 초씩 붙잡지 않기 위해서다.
- 첫 화면 로딩을 가볍게 유지한다. 무거운 UI 라이브러리나 고해상도 배너는 피한다.

## 2. 언어

**사실**
- 국가어는 **타직어**이고, 1939년부터 **키릴 문자**로 쓴다. 러시아어는 민족 간 소통 언어로 널리 쓰인다. [Wikipedia: Languages of Tajikistan]
- 젊은 세대는 러시아어를 실제로 쓰는 일이 크게 줄었다. 반면 러시아로 가는 노동이주 때문에 러시아어의 쓸모는 유지되고 있다. [RUDN 2025, 경상국립대 연구]
- 2009년 언어법에서 러시아어의 법적 지위가 낮아졌다. [RFE/RL]

**설계 반영**
- 지원 언어는 `tg`·`ru`·`en`·`ko`다. 상품명·설명은 `product_translations`에 언어별로 저장한다. **개발 기간에는 `ko`로 운영하고(임시), 실증 전에 `tg`/`ru`로 바꾼다.** → [임시 결정 목록](00-temporary-decisions.md)
- AI 출력 언어를 고정하지 않고 요청마다 지정한다(`scan_jobs.output_locale`). PoC는 한국어로 고정돼 있다.
- 검색은 pg_trgm을 쓴다. **DB 로케일이 `C`면 키릴 문자 검색이 0건이 된다.** 실제로 확인했고, 마이그레이션에서 로케일을 검사하도록 넣었다. → [ERD 문서](02-erd.md#db-생성-주의-키릴-문자-검색)

**현지 확인 필요**
- 판매자들이 SNS에 실제로 어떤 언어로 쓰는지 확인해야 한다. 러시아어인지, 타직어인지, 섞어 쓰는지.
- **가설**: 러시아어 자판으로 타직어를 쓰면서 타직어 고유 문자를 비슷한 러시아 문자로 바꿔 쓸 수 있다(ҳ→х, ӯ→у, қ→к, ғ→г, ҷ→ч, ӣ→и). 사실이라면 검색할 때 이 문자들을 같게 취급하는 정규화가 필요하다. 공개 자료로는 확인하지 못했다.
- 타직어 상품명·설명을 Gemini가 얼마나 잘 생성하는지 아직 검증하지 않았다. 러시아어와 비교 실험이 필요하다.

## 3. 판매 채널·경쟁 환경

**사실**
- 소셜 커머스가 성장하고 있다. 사업자들은 Facebook·Instagram·Telegram으로 직접 판매한다. [trade.gov eCommerce]
- 메신저 점유율은 2021년 설문에서 **IMO 34~48%, WhatsApp 35~46%, Telegram 약 4.5%**였다. 두 설문의 1위가 서로 다르고, 더 최근 설문은 찾지 못했다. [VoxDash]
- **Wildberries**가 2025년 4월 DC Market과 손잡고 진출했다. 두샨베·후잔드 픽업 포인트로 받고, 배송에 평균 10~14일이 걸린다. [Wildberries 보도자료]
- 그 밖의 경쟁 서비스로 Alif Shop(할부 마켓), somon.tj(분류광고), 중국 직구(Pinduoduo·Taobao)가 있다. [trade.gov, Statista]
- 정부가 전자상거래 국가 프로그램(2025~2029)을 시작했다. [etradeforall]
- **SNS 차단 이력**: 2018~2019년에 Instagram·Telegram·WhatsApp 등이 간헐적으로 차단됐다. 2021년에는 고르노바다흐샨 지역의 인터넷이 끊겼다. [RSF, ISOC Pulse, OSCE]

**설계 반영**
- 상점 연락처는 **WhatsApp 번호를 우선**하고 Telegram은 선택 항목으로 둔다. 2차 회의의 "향후: Telegram·WhatsApp 기반 등록"은 WhatsApp 우선으로 다시 검토한다.
- Instagram API에 의존하지 않는다. 판매자가 저장해 둔 사진과 캡션을 직접 올리는 방식(2차 결정)을 유지한다(`scan_jobs.source_type = 'SNS_IMPORT'`, `caption_text`).
- 차별점: 대형 마켓·직구는 10~14일이 걸린다. BOZOR는 **동네 시장 상품을 당일 픽업**으로 제공하고, **현지 상인이 상품을 바로 등록**할 수 있다.

## 4. 결제

**사실**
- 현금 의존도가 높고, 온라인 판매에도 착불(COD)이 필요하다. 출처가 벤더 가이드라 비공식이다. [cartdna]
- 전자지갑은 **1,980만 개**가 등록돼 있다(2026년 6월, 전년 대비 +25.9%). [중앙은행 통계, The Fintech Times 인용]
- Alif는 지갑·Alif Pay·QR 결제·할부 서비스를 운영하고, 국가 카드망 Korti Milli와 연동돼 있다. [Alif, App Store]
- 바자르에서는 카드가 거의 안 되고 현금이 필수다. [여행 가이드]

**설계 반영**
- `orders.payment_method`는 `CASH`(대면·착불)와 `TRANSFER`(지갑·QR 송금, 판매자가 직접 확인)만 둔다. PG 연동은 범위 밖이다(2차 결정과 일치).

## 5. 시장(바자르)·거래 관행

**사실**
- 두샨베의 Shohmansur(Green Bazaar)는 **폐쇄**됐다. 지금은 **Mehrgon**(실내)이 식품·수공예 중심 시장으로, 말린 과일·꿀·향신료·할바를 판다. **Korvon**은 의류·섬유·생활용품 위주의 대형 야외 시장이다. [nomadsland, 여행 가이드]
- **흥정이 관행**이고, 가격을 표시하지 않은 매대가 많다. 정가 매장에서는 흥정하지 않는다. [여행 가이드]

**설계 반영**
- `markets` 테이블에 Mehrgon과 Korvon을 기본 데이터로 넣는다. 매장 위치는 `stores.location_detail`(줄·칸 번호)에 적는다.
- `products.price_type`은 `FIXED`와 `NEGOTIABLE`(기준가 + 흥정 가능)로 나눈다. **발행하려면 가격이 반드시 있어야 한다.** 가격 미표시 → DM 문의가 바로 이 서비스가 푸는 문제이기 때문이다.
- `products.unit`에 `KG` 등 무게·부피 단위를 둔다. 수량과 재고는 소수(`numeric(10,3)`)로 받는다.

**현지 확인 필요**
- 말린 과일·향신료를 kg 단위로 가격 매기는 관행을 공개 자료로는 확인하지 못했다.
- 시장 안 위치를 실제로 어떻게 표기하는지(줄·칸 번호 체계가 있는지) 확인해야 한다.

## 6. 주소·배송

**사실**
- 주소에 랜드마크를 함께 적는 관행이 있다. 예를 들어 은행 지점 주소가 "OO 쇼핑센터 근처" 같은 형식이다. [Spitamen Bank]
- Yandex Go는 타지키스탄에서 서비스되지 않는다는 이야기가 있다. 여행자 포럼 정보라 비공식이다. 우체국(Tajik Post)은 있지만 시내 근거리 배송 정보는 부족하다.

**설계 반영**
- 수령 방식은 `PICKUP`(매장 픽업)과 `DELIVERY`(판매자가 직접 배송)로 나눈다. 주소는 자유 텍스트와 `landmark`로 받는다. 배송 추적은 하지 않는다(2차 결정).

## 7. 전화번호·인증

**사실**
- 전화번호는 국가번호 **+992 + 9자리**다. 통신사는 Tcell·MegaFon·ZET-Mobile·Babilon-Mobile이 있지만, 앞자리 번호와 통신사의 대응은 자료마다 다르다. [sent.dm, e164.com]
- 기업용 SMS(A2P) 발송이 가능하고, 발신자 ID 등록은 필요 없다. [LINK Mobility]

**설계 반영**
- 번호는 E.164 형식으로 저장한다. 앱에서는 **+992 + 9자리**를 검사하고(개발 중에는 +82도 허용), 앞자리로 통신사를 판별하지 않는다.
- 개발 기간에는 **목(mock) 로그인**으로 진행한다(임시). 실증 전에 전화번호 + 비밀번호 또는 SMS OTP 중에서 정한다. OTP는 SMS 발송 비용을 확인해야 한다.

## 8. 법·규제

**사실**
- **개인정보보호법 No.1537(2018-08-03)**: 적정한 보호 수준을 갖춘 국가로는 국외 이전이 허용된다. 그 밖의 경우에는 정보주체 동의 같은 예외가 필요하다. 데이터 현지화 요구는 없다고 알려져 있는데, 날짜가 없는 2차 요약 자료라서 원문 확인이 필요하다. [DLA Piper, GRATA]
- 2025년 5월 SNS '좋아요' 형사처벌이 폐지됐다. 2025년 10월에는 14세 미만 SNS 금지 법안이 제안됐다. 전반적으로 인터넷 통제가 강한 환경이다. [AFP, The Diplomat]

**설계 반영**
- 가입할 때 **해외 서버(Gemini·클라우드 저장소) 전송에 대한 동의**를 받고, 그 시각을 기록한다(`users.privacy_consented_at`).
- 사진은 **EXIF(촬영 위치 등)를 지운 정규화 이미지만 저장**한다. PoC는 원본을 DB에 저장하고 있어서 바꿔야 한다.
- 실서비스로 갈 때는 현지 법률 자문이 필요하다.

## 9. 통화·물가

**사실**
- 통화는 **소모니(TJS)**이고, 1 소모니는 100 디람이다. 2025년 1월 1 USD ≈ 10.9 TJS였고, 2025년 동안 약 14% 절상됐다. [EDB, Times of Central Asia]
- 물가상승률은 약 3.5%다(2025년 7월). 중앙은행 목표는 5% ±2%p다. [Interfax, NBT]

**설계 반영**
- 금액은 `numeric(12,2)`, 통화는 기본값 `TJS`로 둔다. 시연 데이터 가격은 1 USD ≈ 9.5~11 TJS를 기준으로 현실성 있게 만든다.

---

## 필로(참여기업)에 확인할 질문

1. 판매자들이 인스타 게시물을 어떤 언어로 쓰나요? (러시아어 / 타직어 / 섞어 씀 / 러시아 자판으로 타직어)
2. 판매자와 구매자가 주로 쓰는 메신저는 무엇인가요? (WhatsApp / IMO / Telegram)
3. 말린 과일·향신료 등은 kg 단위로 가격을 매기나요? 가격은 어떻게 표기하나요?
4. Mehrgon·Korvon 안에서 매장 위치를 표기하는 체계(줄·칸 번호)가 있나요?
5. **실제 판매 게시물(사진 + 캡션) 20~50건을 받을 수 있나요?** 2차 회의에서 정한 "정답셋 100장" 평가에 쓸 예정입니다.
6. 근거리 배송은 보통 어떻게 하나요? (판매자 직접, 택시 기사, 배달 앱)
7. 송금 결제를 할 때 많이 쓰는 앱은 무엇인가요? (Alif, DC 등)

## 출처

- DataReportal — [Digital 2026: Tajikistan](https://datareportal.com/reports/digital-2026-tajikistan), [Digital 2023: Tajikistan](https://datareportal.com/reports/digital-2023-tajikistan)
- [dig.watch — 타지키스탄 인터넷](https://dig.watch/?p=586)
- [Speedgeo — Internet Speed in Tajikistan](https://www.speedgeo.net/statistics/tajikistan)
- trade.gov — [Tajikistan eCommerce](https://www.trade.gov/country-commercial-guides/tajikistan-ecommerce), [Tajikistan ICT](https://www.trade.gov/country-commercial-guides/tajikistan-information-and-communication-technologies-ict)
- [The Diplomat — 14세 미만 SNS 금지 제안 (2025-10)](https://sv7.thediplomat.com/2025/10/tajik-mp-proposes-a-social-media-ban-for-children-under-14)
- [Wikipedia — Languages of Tajikistan](https://en.wikipedia.org/wiki/Languages_of_Tajikistan)
- [RUDN — 타지키스탄의 러시아어 기능 (2025)](https://journals.rudn.ru/russian-test/article/view/47939/en_US)
- [경상국립대 — 타지키스탄 문자 교체 문제와 언어 상황 연구](https://scholarworks.gnu.ac.kr/item/bad595c2-263f-403d-91b5-417b6c9a75ee)
- [RFE/RL — 러시아어 공식 지위 폐지](https://rferl.org/a/Tajikistan_Drops_Russian_As_Official_Language/1846118.html)
- VoxDash 메신저 설문 — [2021-07](https://voxdash.com/question/16620), [2021-10](https://voxdash.com/question/17083)
- [Wildberries 타지키스탄 진출](https://vietnamnews.vn/media-outreach/1716229/wildberries-launches-sales-in-tajikistan.html)
- [Statista — eCommerce Tajikistan](https://www.statista.com/outlook/emo/ecommerce/tajikistan)
- [etradeforall — 전자상거래 국가 프로그램](https://etradeforall.org/node/10926)
- [RSF — 인터넷 검열](https://rsf.org/en/tajikistan-turns-chinese-model-large-scale-internet-censorship), [ISOC Pulse — 2019 차단](https://pulse.internetsociety.org/en/shutdowns/content-blocking-in-tajikistan-23-april-2019/), [OSCE](https://www.osce.org/representative-on-freedom-of-media/418964)
- [cartdna — Tajikistan 결제 가이드](https://cartdna.com/en/shopify-payment-guide/asia-central/tajikistan)
- [The Fintech Times — 송금 경제와 핀테크](https://thefintechtimes.com/tajikistan-can-fintech-digitalise-a-remittance-economy/)
- [Alif Bank (Wikipedia)](https://en.wikipedia.org/wiki/Alif_Bank), [Alif 앱](https://apps.apple.com/us/app/alif-pay-transfer-shop/id1331374853)
- [nomadsland — Shohmansur market](https://nomadsland.travel/en/before-you-go/tajikistan/bazaar/shohmansur-market)
- [World Travel Guide — Tajikistan shopping](https://www.worldtravelguide.net/guides/asia/tajikistan/shopping-nightlife)
- [Spitamen Bank 지점 주소](https://spitamenbank.tj/en/contacts/filials/)
- [eurasia.travel — 두샨베 여행 후기](https://eurasia.travel/forum/trips-tajikistan/my-experience-in-dushanbe/)
- [sent.dm — Tajikistan SMS](https://sent.dm/resources/tajikistan-sms-guide), [e164.com — Babilon Mobile](https://www.e164.com/directory/operators/tjk/babilon-mobile), [LINK Mobility — Sender ID](https://docs.linkmobility.com/regulatory-guidelines/sender-id/tajikistan)
- 개인정보보호법 — [DLA Piper](https://www.dlapiperdataprotection.com/?t=law&c=TJ), [GRATA](https://gratanet.com/publications/personal-data-protection-in-the-republic-of-tajikistan)
- [AFP — '좋아요' 처벌 폐지](https://www.nampa.org/text/22631557)
- 환율·물가 — [Times of Central Asia (EDB)](https://timesca.com/edb-forecasts-8-4-gdp-growth-for-tajikistan-in-2025), [Interfax](https://www.interfax.com/newsroom/top-stories/113424/), [National Bank of Tajikistan](https://nbt.tj:443/en/?lang=en)
