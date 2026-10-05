-- =====================================================================
-- 시연용 사진 시드 - 게시물(스냅)/액티비티/투어 썸네일을 실제 관광지 사진으로 교체
--
-- data.sql 의 thumbnail_url 은 대부분 'ph1'~'ph4'(CSS 그라데이션 클래스)라서 화면에
-- 색 박스로만 보인다. 템플릿은 '/uploads/' 로 시작하는 값만 진짜 이미지로 그리므로
-- static/assets/places/*.jpg 를 업로드 폴더에 demo_*.jpg 로 복사해 두고 그 경로로 바꾼다.
-- → 사진 복사까지 한 번에 하려면 apply_demo_photos.sh 를 실행할 것(이 SQL 도 같이 실행됨).
--
-- 여러 번 실행해도 안전하다(멱등):
--   - 'ph*' / NULL / 이전에 이 시드가 넣은 '/uploads/demo_*' 값만 바꾼다
--   - 사용자가 직접 올린 사진('/uploads/<uuid>.webp')은 건드리지 않는다
--   - 액티비티/투어는 id 가 아니라 제목으로 매칭(PC 마다 id 가 달라도 동작)
-- 사진이 없는 지역(대구/광주/대전/전북 등) 게시물은 원래 그라데이션 그대로 둔다.
--
-- 되돌리기:
--   UPDATE posts      SET thumbnail_url='ph1' WHERE thumbnail_url LIKE '/uploads/demo\_%';
--   UPDATE activities SET thumbnail_url='ph1' WHERE thumbnail_url LIKE '/uploads/demo\_%';
--   UPDATE tours      SET thumbnail_url='ph1' WHERE thumbnail_url LIKE '/uploads/demo\_%';
-- =====================================================================
USE tanoshimi;

-- ---------------------------------------------------------------- 게시물
-- 1) 제목에 장소 이름이 있으면("해운대 해변", "오사카성 스냅") 그 장소 사진
-- 2) 나머지는 같은 지역 사진을 돌려가며 배정 - 1)에서 안 쓴 사진부터, 최신 글부터
--    (게시판과 같은 작성일 최신순 - 사진이 모자라 겹치는 건 뒤쪽 페이지의 오래된 글로 밀리게)
-- 작업용 테이블은 끝에서 지운다. MySQL 임시 테이블은 한 쿼리에서 두 번 참조할 수 없어서 일반 테이블을 쓴다.
-- posts 와 작업용 테이블의 collation 이 PC 마다 다를 수 있어 posts 컬럼과 비교할 때는 COLLATE 를 명시한다.
DROP TABLE IF EXISTS demo_region_photo, demo_title_photo, demo_post_target, demo_region_rank;

CREATE TABLE demo_region_photo (
    region VARCHAR(50)  NOT NULL,
    seq    INT          NOT NULL,
    file   VARCHAR(100) NOT NULL
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO demo_region_photo (region, seq, file) VALUES
('서울', 0, 'capital_gyeongbokgung.jpg'), ('서울', 1, 'capital_cheonggyecheon.jpg'), ('서울', 2, 'capital_bukchon.jpg'),
('서울', 3, 'capital_myeongdong.jpg'),    ('서울', 4, 'capital_nseoul.jpg'),         ('서울', 5, 'capital_changdeokgung.jpg'),
('서울', 6, 'capital_lotte_tower.jpg'),
('부산', 0, 'gyeongnam_haeundae.jpg'), ('부산', 1, 'gyeongnam_gamcheon.jpg'), ('부산', 2, 'busan_gwangan.jpg'),
('부산', 3, 'busan_jagalchi.jpg'),     ('부산', 4, 'busan_yonggungsa.jpg'),
('제주', 0, 'jeju_seongsan.jpg'), ('제주', 1, 'jeju_hyeopjae.jpg'), ('제주', 2, 'jeju_cheonjiyeon.jpg'), ('제주', 3, 'jeju_udo.jpg'),
('제주', 4, 'jeju_halla.jpg'),    ('제주', 5, 'jeju_seopjikoji.jpg'),
('강원', 0, 'gangwon_nami.jpg'),  ('강원', 1, 'gangwon_anmok.jpg'), ('강원', 2, 'gangwon_seorak.jpg'),
('오사카', 0, 'osaka_dotonbori.jpg'), ('오사카', 1, 'osaka_castle.jpg'),  ('오사카', 2, 'osaka_shinsekai.jpg'),
('오사카', 3, 'osaka_usj.jpg'),       ('오사카', 4, 'osaka_kaiyukan.jpg'), ('오사카', 5, 'osaka_umeda.jpg'),
('오사카', 6, 'osaka_kuromon.jpg'),
('교토', 0, 'kyoto_kiyomizu.jpg'), ('교토', 1, 'kyoto_fushimi.jpg'), ('교토', 2, 'kyoto_arashiyama.jpg'), ('교토', 3, 'kyoto_gion.jpg'),
('도쿄', 0, 'tokyo_shibuya.jpg'),  ('도쿄', 1, 'tokyo_sensoji.jpg'), ('도쿄', 2, 'tokyo_tower.jpg'),     ('도쿄', 3, 'tokyo_skytree.jpg'),
('홋카이도', 0, 'hokkaido_otaru.jpg'), ('홋카이도', 1, 'hokkaido_furano.jpg'),
('홋카이도', 2, 'hokkaido_odori.jpg'), ('홋카이도', 3, 'hokkaido_noboribetsu.jpg'),
('후쿠오카', 0, 'fukuoka_nakasu.jpg'), ('후쿠오카', 1, 'fukuoka_dazaifu.jpg'),
('후쿠오카', 2, 'fukuoka_canalcity.jpg'), ('후쿠오카', 3, 'fukuoka_momochi.jpg'),
('오키나와', 0, 'okinawa_churaumi.jpg'), ('오키나와', 1, 'okinawa_kokusai.jpg'),
('오키나와', 2, 'okinawa_manzamo.jpg'),  ('오키나와', 3, 'okinawa_american.jpg');

CREATE TABLE demo_title_photo (
    kw   VARCHAR(50)  NOT NULL,
    file VARCHAR(100) NOT NULL
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO demo_title_photo (kw, file) VALUES
('해운대', 'gyeongnam_haeundae.jpg'), ('감천', 'gyeongnam_gamcheon.jpg'), ('광안', 'busan_gwangan.jpg'),
('자갈치', 'busan_jagalchi.jpg'),
('한강', 'capital_lotte_tower.jpg'), ('북촌', 'capital_bukchon.jpg'), ('남산', 'capital_nseoul.jpg'),
('경복궁', 'capital_gyeongbokgung.jpg'), ('명동', 'capital_myeongdong.jpg'),
('성산', 'jeju_seongsan.jpg'), ('우도', 'jeju_udo.jpg'), ('한라산', 'jeju_halla.jpg'), ('협재', 'jeju_hyeopjae.jpg'),
('속초', 'gangwon_seorak.jpg'),
('도톤보리', 'osaka_dotonbori.jpg'), ('우메다', 'osaka_umeda.jpg'), ('오사카성', 'osaka_castle.jpg'),
('기요미즈', 'kyoto_kiyomizu.jpg'), ('아라시야마', 'kyoto_arashiyama.jpg'),
('시부야', 'tokyo_shibuya.jpg'), ('센소지', 'tokyo_sensoji.jpg'),
('오타루', 'hokkaido_otaru.jpg'), ('캐널시티', 'fukuoka_canalcity.jpg');

-- 바꿀 대상 글과, 제목으로 정해지는 사진(없으면 NULL)
CREATE TABLE demo_post_target AS
SELECT p.id, p.region, p.created_at,
       (SELECT t.file FROM demo_title_photo t
        WHERE p.title LIKE CONCAT('%', t.kw, '%') COLLATE utf8mb4_unicode_ci LIMIT 1) AS title_file
FROM posts p
WHERE p.thumbnail_url IS NULL OR p.thumbnail_url IN ('', 'ph1', 'ph2', 'ph3', 'ph4')
   OR p.thumbnail_url LIKE '/uploads/demo\_%';

UPDATE posts p
JOIN demo_post_target t ON t.id = p.id
SET p.thumbnail_url = CONCAT('/uploads/demo_', t.title_file)
WHERE t.title_file IS NOT NULL;

-- 지역별 사진 순서: 제목 매칭에 안 쓰인 사진이 앞으로
CREATE TABLE demo_region_rank AS
SELECT f.region, f.file,
       ROW_NUMBER() OVER (PARTITION BY f.region
                          ORDER BY EXISTS (SELECT 1 FROM demo_post_target t WHERE t.title_file = f.file), f.seq) - 1 AS rk,
       COUNT(*) OVER (PARTITION BY f.region) AS cnt
FROM demo_region_photo f;

UPDATE posts p
JOIN (
    SELECT id, region, ROW_NUMBER() OVER (PARTITION BY region ORDER BY created_at DESC, id DESC) - 1 AS rn
    FROM demo_post_target
    WHERE title_file IS NULL
) t ON t.id = p.id
JOIN demo_region_rank r ON r.region = t.region COLLATE utf8mb4_unicode_ci AND r.rk = t.rn % r.cnt
SET p.thumbnail_url = CONCAT('/uploads/demo_', r.file);

DROP TABLE demo_region_photo, demo_title_photo, demo_post_target, demo_region_rank;

-- ---------------------------------------------------------------- 액티비티: 제목 기준 1:1 매칭
-- 같은 장소 사진이 없는 항목(디즈니씨/하라주쿠/니세코/다이빙 등)은 같은 지역의 가장 비슷한 사진으로 대체.
UPDATE activities a
JOIN (
    SELECT '기요미즈데라' AS title, 'kyoto_kiyomizu.jpg' AS file
    UNION ALL SELECT '후시미이나리 신사',          'kyoto_fushimi.jpg'
    UNION ALL SELECT '아라시야마 대나무숲',        'kyoto_arashiyama.jpg'
    UNION ALL SELECT '기온 마치야 카페',           'kyoto_gion.jpg'
    UNION ALL SELECT '시부야 스크램블 교차로',     'tokyo_shibuya.jpg'
    UNION ALL SELECT '도쿄 디즈니씨 야간 불꽃쇼',  'tokyo_skytree.jpg'
    UNION ALL SELECT '하라주쿠 편집숍 투어',       'tokyo_sensoji.jpg'
    UNION ALL SELECT '오다이바 야경',              'tokyo_tower.jpg'
    UNION ALL SELECT '스미요시타이샤 하츠모데',    'osaka_shinsekai.jpg'
    UNION ALL SELECT '도톤보리 야경 산책',         'osaka_dotonbori.jpg'
    UNION ALL SELECT '우메다 공중정원 전망대',     'osaka_umeda.jpg'
    UNION ALL SELECT '쿠로몬 시장 먹거리 투어',    'osaka_kuromon.jpg'
    UNION ALL SELECT '유니버설 스튜디오 재팬',     'osaka_usj.jpg'
    UNION ALL SELECT '츄라우미 수족관',            'okinawa_churaumi.jpg'
    UNION ALL SELECT '체험 다이빙',                'okinawa_manzamo.jpg'
    UNION ALL SELECT '미이바루 비치 스노클링',     'okinawa_american.jpg'
    UNION ALL SELECT '국제거리 쇼핑',              'okinawa_kokusai.jpg'
    UNION ALL SELECT '니세코 스키 체험',           'hokkaido_furano.jpg'
    UNION ALL SELECT '노보리베츠 온천',            'hokkaido_noboribetsu.jpg'
    UNION ALL SELECT '오타루 운하 야경',           'hokkaido_otaru.jpg'
    UNION ALL SELECT '하카타 라멘 스트리트',       'fukuoka_nakasu.jpg'
    UNION ALL SELECT '다자이후 텐만구',            'fukuoka_dazaifu.jpg'
    UNION ALL SELECT '야나가와 뱃놀이',            'fukuoka_canalcity.jpg'
) m ON m.title = a.title COLLATE utf8mb4_unicode_ci
SET a.thumbnail_url = CONCAT('/uploads/demo_', m.file)
WHERE a.thumbnail_url IS NULL OR a.thumbnail_url IN ('', 'ph1', 'ph2', 'ph3', 'ph4')
   OR a.thumbnail_url LIKE '/uploads/demo\_%';

-- ---------------------------------------------------------------- 투어 패키지: 제목 기준
UPDATE tours t
JOIN (
    SELECT '오사카 신년 하츠모데 3박4일' AS title, 'osaka_castle.jpg' AS file
    UNION ALL SELECT '교토 벚꽃 힐링 2박3일',          'kyoto_kiyomizu.jpg'
    UNION ALL SELECT '홋카이도 스키&온천 3박4일',      'hokkaido_noboribetsu.jpg'
    UNION ALL SELECT '후쿠오카 맛집투어 2박3일',       'fukuoka_nakasu.jpg'
    UNION ALL SELECT '도쿄 디즈니 3박4일',             'tokyo_skytree.jpg'
    UNION ALL SELECT '오키나와 바다 액티비티 3박4일',  'okinawa_churaumi.jpg'
) m ON m.title = t.title COLLATE utf8mb4_unicode_ci
SET t.thumbnail_url = CONCAT('/uploads/demo_', m.file)
WHERE t.thumbnail_url IS NULL OR t.thumbnail_url IN ('', 'ph1', 'ph2', 'ph3', 'ph4')
   OR t.thumbnail_url LIKE '/uploads/demo\_%';

-- 결과 확인
SELECT 'posts' AS tbl, COUNT(*) AS total, SUM(thumbnail_url LIKE '/uploads/%') AS with_photo FROM posts
UNION ALL SELECT 'activities', COUNT(*), SUM(thumbnail_url LIKE '/uploads/%') FROM activities
UNION ALL SELECT 'tours',      COUNT(*), SUM(thumbnail_url LIKE '/uploads/%') FROM tours;
