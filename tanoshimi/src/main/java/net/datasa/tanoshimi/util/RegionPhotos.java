package net.datasa.tanoshimi.util;

import net.datasa.tanoshimi.domain.entity.PartyEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 파티 썸네일이 없을 때 대신 보여줄 지역 대표 사진.
 *
 * <p>파티 대부분이 썸네일 없이('ph1'~'ph4' 그라데이션 키 또는 NULL) 만들어져 목록이 휑해 보여서,
 * 화면에서만 지역 사진으로 채운다(DB 는 그대로 - 나중에 사용자가 올린 사진이 생기면 그게 우선).
 * 같은 지역 파티가 여러 개면 파티 id 로 사진을 돌려 써서 카드가 똑같아 보이지 않게 한다.
 * 사진은 static/assets/places/ (출처: static/assets/theme/CREDITS.txt).
 *
 * <p>템플릿에서는 {@code ${@regionPhotos.of(party)}} 로 쓴다 - 결과는 실제 이미지 경로이거나,
 * 매핑된 사진이 없는 지역이면 null(기존 그라데이션 유지).
 */
@Component("regionPhotos")
public class RegionPhotos {

    private static final String BASE = "/assets/places/";

    private static final Map<String, List<String>> POOL = Map.ofEntries(
            Map.entry("오사카", List.of("osaka_dotonbori", "osaka_castle", "osaka_shinsekai", "osaka_umeda",
                    "osaka_kaiyukan", "osaka_kuromon", "osaka_usj")),
            Map.entry("교토", List.of("kyoto_fushimi", "kyoto_kiyomizu", "kyoto_arashiyama", "kyoto_gion")),
            Map.entry("도쿄", List.of("tokyo_shibuya", "tokyo_skytree", "tokyo_tower", "tokyo_sensoji")),
            Map.entry("홋카이도", List.of("hokkaido_otaru", "hokkaido_furano", "hokkaido_odori", "hokkaido_noboribetsu")),
            Map.entry("후쿠오카", List.of("fukuoka_nakasu", "fukuoka_dazaifu", "fukuoka_momochi", "fukuoka_canalcity")),
            Map.entry("오키나와", List.of("okinawa_churaumi", "okinawa_manzamo", "okinawa_kokusai", "okinawa_american")),
            Map.entry("기타 일본", List.of("japan_fuji", "japan_himeji")),
            Map.entry("서울", List.of("capital_gyeongbokgung", "capital_bukchon", "capital_nseoul", "capital_myeongdong",
                    "capital_changdeokgung", "capital_cheonggyecheon", "capital_lotte_tower")),
            Map.entry("부산", List.of("gyeongnam_haeundae", "busan_gwangan", "gyeongnam_gamcheon", "busan_yonggungsa",
                    "busan_jagalchi")),
            Map.entry("제주", List.of("jeju_seongsan", "jeju_udo", "jeju_hyeopjae", "jeju_halla", "jeju_cheonjiyeon",
                    "jeju_seopjikoji")),
            Map.entry("강원", List.of("gangwon_seorak", "gangwon_nami", "gangwon_anmok")),
            Map.entry("대구", List.of("daegu_83tower")),
            Map.entry("대전", List.of("daejeon_expo")),
            Map.entry("전북", List.of("jeonbuk_hanok")),
            Map.entry("광주", List.of("gwangju_mudeungsan")),
            Map.entry("기타 한국", List.of("gyeongbuk_bulguksa", "gyeongnam_tongyeong", "gyeongbuk_hahoe",
                    "gyeongbuk_homigot", "capital_incheon_china"))
    );

    /** 파티 카드/상세/파티방에 실제로 보여줄 이미지 경로. 없으면 null. */
    public String of(PartyEntity party) {
        return party == null ? null : of(party.getRegion(), party.getId(), party.getThumbnailUrl());
    }

    /**
     * 사용자가 올린 썸네일('ph' 로 시작하지 않는 실제 경로)이 있으면 그것, 없으면 지역 대표 사진,
     * 그 지역 사진도 없으면 null.
     */
    public String of(String region, Long id, String thumbnailUrl) {
        if (thumbnailUrl != null && !thumbnailUrl.isBlank() && !thumbnailUrl.startsWith("ph")) {
            return thumbnailUrl;
        }
        List<String> pool = region == null ? null : POOL.get(region.trim());
        if (pool == null || pool.isEmpty()) {
            return null;
        }
        int idx = id == null ? 0 : (int) Math.floorMod(id, (long) pool.size());
        return BASE + pool.get(idx) + ".jpg";
    }
}
