-- BOZOR 개발용 목(mock) 데이터 — dev 프로필에서만 적용한다(application-dev.yml).
-- Flyway 반복 마이그레이션(R__): 이 파일 내용이 바뀌면 다시 실행되므로 모든 INSERT는
-- 여러 번 실행해도 중복되지 않게 작성한다.
--
-- * 모든 인물·상점·가격은 가상 데이터다.
-- * 전화번호는 +992 뒤가 0으로 시작해 실제로 쓰이지 않는 번호 형식이다(+9920000000NN).
-- * password_hash는 NULL: 목 로그인은 dev 전용 API에서 사용자 id만으로 로그인한다.
--   (실제 인증 도입 시 이 파일과 dev 로그인 API를 함께 걷어낸다 → docs/00-temporary-decisions.md)

-- 사용자 ---------------------------------------------------------------------
INSERT INTO users (phone_number, display_name, locale, role, privacy_consented_at) VALUES
    ('+992000000001', '판매자 파르호드',  'ko', 'USER',  now()),
    ('+992000000002', '판매자 닐루파르',  'ko', 'USER',  now()),
    ('+992000000003', '판매자 루스탐',    'ko', 'USER',  now()),
    ('+992000000011', '구매자 마디나',    'ko', 'USER',  now()),
    ('+992000000012', '구매자 테스트',    'ko', 'USER',  now()),
    ('+992000000099', '관리자',           'ko', 'ADMIN', now())
ON CONFLICT (phone_number) DO NOTHING;

-- 상점: 템플릿 3종을 하나씩 ------------------------------------------------
INSERT INTO stores (owner_id, slug, name, description, template, theme_color, market_id,
                    location_detail, address_text, landmark, whatsapp_phone, instagram_handle, status)
SELECT u.id, s.slug, s.name, s.description, s.template, s.theme_color, m.id,
       s.location_detail, s.address_text, s.landmark, u.phone_number, s.instagram_handle, 'ACTIVE'
FROM (VALUES
    ('+992000000001', 'farhod-dried-fruit', '파르호드 말린 과일',
     '판자켄트 살구와 건포도를 직접 말려 팝니다.', 'GRID', '#C2410C',
     'Меҳргон', '3열 12번', NULL, NULL, 'farhod.mewa'),
    ('+992000000002', 'nilufar-atlas', '닐루파르 아틀라스',
     '아틀라스·아드라스 원단을 미터 단위로 재단해 드립니다.', 'LIST', '#7C3AED',
     'Корвон', 'B동 41번', NULL, NULL, NULL),
    ('+992000000003', 'rustam-ceramics', '루스탐 도자기 공방',
     '이스타라프샨 전통 문양 도자기와 수자니를 만듭니다.', 'SHOWCASE', '#0F766E',
     NULL, NULL, '두샨베 시 루다키 대로', '중앙백화점(ЦУМ) 맞은편', 'rustam.kulol')
) AS s (owner_phone, slug, name, description, template, theme_color,
        market_name, location_detail, address_text, landmark, instagram_handle)
JOIN users u ON u.phone_number = s.owner_phone
LEFT JOIN markets m ON m.name = s.market_name
ON CONFLICT (slug) DO NOTHING;

-- 상품 (AI 스캔을 거치지 않은 직접 등록 상품) ---------------------------------
-- 상품이 하나도 없는 시드 상점에만 넣는다(다시 실행돼도 중복되지 않음).
-- unit이 NULL이면 카테고리의 default_unit을 쓴다(실제 등록 화면과 같은 규칙).
-- 시드 안에서는 (상점, 가격)이 상품마다 달라서 아래 이름 매핑의 키로 쓴다.
INSERT INTO products (store_id, category_id, status, price, price_type, unit, stock_quantity, published_at)
SELECT st.id, c.id, 'PUBLISHED', p.price, p.price_type, coalesce(p.unit, c.default_unit), p.stock, now()
FROM (VALUES
    ('farhod-dried-fruit', 'food.dried_fruit',     45.00, 'NEGOTIABLE', NULL,  120.000),
    ('farhod-dried-fruit', 'food.dried_fruit',     38.00, 'FIXED',      NULL,   80.000),
    ('farhod-dried-fruit', 'food.nuts',            60.00, 'NEGOTIABLE', NULL,     NULL),
    ('nilufar-atlas',      'textiles.ikat',        35.00, 'FIXED',      NULL,  300.000),
    ('rustam-ceramics',    'handicrafts.suzani', 1200.00, 'NEGOTIABLE', NULL,    2.000),
    ('rustam-ceramics',    'household.kitchen',   150.00, 'FIXED',      'SET',  10.000)
) AS p (store_slug, category_code, price, price_type, unit, stock)
JOIN stores st ON st.slug = p.store_slug
JOIN categories c ON c.code = p.category_code
WHERE NOT EXISTS (SELECT 1 FROM products x WHERE x.store_id = st.id);

INSERT INTO product_translations (product_id, locale, name, description)
SELECT p.id, 'ko', t.name, t.description
FROM (VALUES
    ('farhod-dried-fruit',   45.00, '말린 살구(쿠라가)', '씨를 뺀 판자켄트산 말린 살구. 1kg 단위로 판매합니다.'),
    ('farhod-dried-fruit',   38.00, '건포도(마비즈)',     '황금색 건포도. 1kg 단위.'),
    ('farhod-dried-fruit',   60.00, '호두',               '껍질 깐 호두. 양에 따라 가격 조정 가능합니다.'),
    ('nilufar-atlas',        35.00, '아틀라스 원단',      '전통 이캇 문양 실크 혼방 원단. 1m 단위 재단.'),
    ('rustam-ceramics',    1200.00, '수자니 벽걸이',      '손자수 수자니, 약 150×200cm.'),
    ('rustam-ceramics',     150.00, '피알라 찻잔 세트',   '전통 문양 찻잔 6개 세트.')
) AS t (store_slug, price, name, description)
JOIN stores st ON st.slug = t.store_slug
JOIN products p ON p.store_id = st.id AND p.price = t.price
ON CONFLICT (product_id, locale) DO NOTHING;
