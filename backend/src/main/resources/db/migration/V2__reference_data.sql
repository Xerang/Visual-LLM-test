-- 기준 데이터: 카테고리 트리 v1(임시)와 두샨베 시장.
-- 카테고리를 바꿀 때는 이 파일을 고치지 말고 새 버전 파일(V3__...)을 추가한다.

-- 10. 기본 데이터 ------------------------------------------------------------
-- 카테고리 트리 v1 (임시). 분류 기준:
--   ① 구매자가 찾는 방식(검색·필터)  ② 현지 시장의 매대 구분(Mehrgon 식품·수공예, Korvon 의류·원단)
--   ③ AI가 사진만 보고 구분할 수 있는지
-- 대분류마다 '.etc'를 둬서 AI가 확신이 없으면 대분류까지만 맞히게 한다.
-- 이름은 ko 우선(임시). PoC 대분류 6개의 tg/ru/en은 유지하고, 나머지 언어는 실증 전에 채운다.
INSERT INTO categories (code, default_unit, sort_order) VALUES
    ('food', NULL, 10), ('textiles', NULL, 20), ('clothing', NULL, 30),
    ('accessories', NULL, 40), ('handicrafts', NULL, 50), ('household', NULL, 60),
    ('beauty', NULL, 70), ('electronics', NULL, 80), ('other', 'PIECE', 99);

INSERT INTO categories (parent_id, code, default_unit, sort_order)
SELECT p.id, s.code, s.unit, s.sort_order
FROM (VALUES
    ('food.dried_fruit',        'KG',    1), ('food.nuts',          'KG',    2),
    ('food.spices',             'KG',    3), ('food.sweets',        'KG',    4),
    ('food.tea',                'PIECE', 5), ('food.dairy',         'KG',    6),
    ('food.bread',              'PIECE', 7), ('food.fresh',         'KG',    8),
    ('food.grains',             'KG',    9), ('food.etc',           'PIECE', 99),
    ('textiles.ikat',           'METER', 1), ('textiles.fabric',    'METER', 2),
    ('textiles.etc',            'PIECE', 99),
    ('clothing.traditional',    'PIECE', 1), ('clothing.women',     'PIECE', 2),
    ('clothing.men',            'PIECE', 3), ('clothing.kids',      'PIECE', 4),
    ('clothing.shoes',          'PIECE', 5), ('clothing.etc',       'PIECE', 99),
    ('accessories.headwear',    'PIECE', 1), ('accessories.jewelry','PIECE', 2),
    ('accessories.bags',        'PIECE', 3), ('accessories.etc',    'PIECE', 99),
    ('handicrafts.suzani',      'PIECE', 1), ('handicrafts.ceramics','PIECE', 2),
    ('handicrafts.carpets',     'PIECE', 3), ('handicrafts.wood_metal','PIECE', 4),
    ('handicrafts.souvenirs',   'PIECE', 5), ('handicrafts.etc',    'PIECE', 99),
    ('household.kitchen',       'PIECE', 1), ('household.bedding',  'PIECE', 2),
    ('household.cleaning',      'PIECE', 3), ('household.decor',    'PIECE', 4),
    ('household.etc',           'PIECE', 99),
    ('beauty.cosmetics',        'PIECE', 1), ('beauty.traditional', 'PIECE', 2),
    ('beauty.etc',              'PIECE', 99),
    ('electronics.phone_acc',   'PIECE', 1), ('electronics.appliances','PIECE', 2),
    ('electronics.etc',         'PIECE', 99)
) AS s (code, unit, sort_order)
JOIN categories p ON p.code = split_part(s.code, '.', 1);

INSERT INTO category_translations (category_id, locale, name)
SELECT c.id, 'ko', t.name
FROM (VALUES
    ('food', '식품'),                         ('food.dried_fruit', '말린 과일'),
    ('food.nuts', '견과류'),                  ('food.spices', '향신료'),
    ('food.sweets', '꿀·과자·디저트'),        ('food.tea', '차·음료'),
    ('food.dairy', '유제품(쿠르트 등)'),      ('food.bread', '빵(논)'),
    ('food.fresh', '신선 과일·채소'),         ('food.grains', '곡물·쌀'),
    ('food.etc', '기타 식품'),
    ('textiles', '원단·섬유'),                ('textiles.ikat', '아틀라스·아드라스 원단'),
    ('textiles.fabric', '일반 원단'),         ('textiles.etc', '기타 섬유'),
    ('clothing', '의류'),                     ('clothing.traditional', '전통 의상'),
    ('clothing.women', '여성복'),             ('clothing.men', '남성복'),
    ('clothing.kids', '아동복'),              ('clothing.shoes', '신발'),
    ('clothing.etc', '기타 의류'),
    ('accessories', '잡화'),                  ('accessories.headwear', '모자·스카프'),
    ('accessories.jewelry', '장신구'),        ('accessories.bags', '가방'),
    ('accessories.etc', '기타 잡화'),
    ('handicrafts', '수공예품'),              ('handicrafts.suzani', '수자니(자수 직물)'),
    ('handicrafts.ceramics', '도자기'),       ('handicrafts.carpets', '카펫·러그'),
    ('handicrafts.wood_metal', '목공예·금속공예'), ('handicrafts.souvenirs', '기념품'),
    ('handicrafts.etc', '기타 수공예'),
    ('household', '생활용품'),                ('household.kitchen', '주방용품'),
    ('household.bedding', '침구·쿠르파차'),   ('household.cleaning', '청소·위생용품'),
    ('household.decor', '인테리어 소품'),     ('household.etc', '기타 생활용품'),
    ('beauty', '미용'),                       ('beauty.cosmetics', '화장품·향수'),
    ('beauty.traditional', '전통 미용(헤나·우스마)'), ('beauty.etc', '기타 미용'),
    ('electronics', '전자제품'),              ('electronics.phone_acc', '휴대폰 액세서리'),
    ('electronics.appliances', '소형 가전'),  ('electronics.etc', '기타 전자제품'),
    ('other', '기타')
) AS t (code, name)
JOIN categories c ON c.code = t.code;

-- PoC 대분류의 다국어 이름(실증 대비). 타직어 표기는 필로 검수 필요.
INSERT INTO category_translations (category_id, locale, name)
SELECT c.id, t.locale, t.name
FROM (VALUES
    ('clothing',    'tg', 'Либос'),            ('clothing',    'ru', 'Одежда'),
    ('clothing',    'en', 'Clothing'),
    ('accessories', 'tg', 'Аксессуарҳо'),      ('accessories', 'ru', 'Аксессуары'),
    ('accessories', 'en', 'Accessories'),
    ('handicrafts', 'tg', 'Ҳунарҳои мардумӣ'), ('handicrafts', 'ru', 'Изделия ручной работы'),
    ('handicrafts', 'en', 'Handicrafts'),
    ('food',        'tg', 'Хӯрокворӣ'),        ('food',        'ru', 'Продукты питания'),
    ('food',        'en', 'Food'),
    ('household',   'tg', 'Молҳои рӯзгор'),    ('household',   'ru', 'Товары для дома'),
    ('household',   'en', 'Household'),
    ('other',       'tg', 'Дигар'),            ('other',       'ru', 'Другое'),
    ('other',       'en', 'Other')
) AS t (code, locale, name)
JOIN categories c ON c.code = t.code;

INSERT INTO markets (name, city) VALUES
    ('Меҳргон', 'Душанбе'),
    ('Корвон',  'Душанбе');
