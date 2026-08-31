-- =====================================================================
-- 마이그레이션: 칭호 획득 조건을 예약 기준 -> 완료한 파티 기준으로 교체
-- 담당: 김민규(⑥ 마이페이지)
--
-- 배경
--  - 기존 EXPLORER/VETERAN 은 "예약이 confirmed 된 횟수 15회/40회"가 조건이었다.
--    v16 에서 예약·결제 기능이 삭제되면서 이 조건은 달성 자체가 불가능해졌다.
--  - FLEX('정산 잔돈 사다리게임에서 3회 당첨')는 결제·미니게임이 모두 빠지면서
--    근거가 사라져, 히트맵과 짝이 맞는 '지도 수집가'로 교체한다.
--
-- 스키마 변경은 없고 titles 시드 데이터만 손본다. 새로 설치하는 사람은 data.sql 에
-- 이미 반영돼 있어 이 파일을 돌릴 필요가 없다. 이미 data.sql 을 실행한 사람만 실행.
--
-- ※ 칭호 장착(user_titles.equipped) 컬럼은 여기 없다. 스키마 변경 조율이 ⑤ 담당이라
--   협의 후 별도 마이그레이션으로 추가할 예정.
-- =====================================================================
USE tanoshimi;

-- 1. 등급 칭호 조건을 완료한 여행 횟수 기준으로 (TitleService 의 상수와 같은 값)
UPDATE titles SET condition_desc = '완료한 여행 3회 이상'  WHERE code = 'EXPLORER';
UPDATE titles SET condition_desc = '완료한 여행 10회 이상' WHERE code = 'VETERAN';

-- 2. FLEX -> COLLECTOR 로 교체.
--    행을 지우지 않고 갱신하는 이유: user_titles 가 title_id 로 이 행을 참조하고 있어
--    DELETE 하면 FK 제약에 걸린다. 제자리에서 바꾸면 참조가 그대로 유지된다.
UPDATE titles
   SET code = 'COLLECTOR', name = '지도 수집가',
       condition_desc = '5개 이상 지역 방문', icon_key = 'globe'
 WHERE code = 'FLEX';

-- 3. FLEX 없이 시드된 DB 를 위한 안전망. code 에 UNIQUE 제약이 있어 중복 삽입은 무시된다.
INSERT IGNORE INTO titles (code, name, condition_desc, icon_key)
VALUES ('COLLECTOR', '지도 수집가', '5개 이상 지역 방문', 'globe');
