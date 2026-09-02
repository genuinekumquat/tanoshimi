package net.datasa.tanoshimi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 지역 이름 카탈로그 - 국가 → 권역 → 지역 3단 트리의 <b>단일 출처</b>. 담당: 김민규(⑥).
 *
 * <p>원본은 {@code static/assets/geo/region-tree.json} 이다. 같은 파일을 브라우저도 그대로
 * 받아 쓰기 때문에(지역 선택기 · 마이페이지 지도 롤업) 서버와 화면이 항상 같은 표를 본다.
 *
 * <p><b>왜 DB 테이블이 아니라 정적 파일인가.</b> 지도 드릴다운은 어차피 SVG 경로가 필요해서
 * {@code regions.json} 이라는 정적 파일에 살아야 하는데, 이름만 DB로 빼면 같은 이름이 두
 * 곳에 존재하게 된다. 파티/스냅은 지역을 <b>말단 이름 문자열 하나</b>로만 저장하므로 조인도
 * 필요 없다 - 그래서 마이그레이션 없이 파일 하나로 끝낸다.
 *
 * <p><b>이 클래스가 흡수한 것들</b>(예전에는 같은 표가 네 군데 흩어져 있었다):
 * <ul>
 *   <li>{@code TitleService.REGION_ALIASES} / {@code KOREA_CITY_TO_PROVINCE}</li>
 *   <li>{@code PostService.REGION_ALIASES}</li>
 *   <li>{@code mypage-heatmap.js} 의 {@code NAME_ALIASES} (이제 트리 파일에서 읽는다)</li>
 * </ul>
 * 인수인계 문서의 Phase 2 "지역 매핑 서버 일원화" 항목이 이걸로 해결된다.
 *
 * <p><b>계층 규칙</b>
 * <ul>
 *   <li>일본: 권역 = 지방 9개, 지역 = 도도부현 47개. {@code regions.json} 그대로.</li>
 *   <li>한국: 권역 = 시/도 17개(지도 오버뷰와 1:1), 지역 = 시/군.
 *       특별시 · 광역시 · 특별자치시는 자기 자신이 유일한 지역이다(일본 홋카이도 ·
 *       오키나와와 같은 모양).</li>
 * </ul>
 *
 * <p><b>동명 지역</b>은 트리에서 애초에 이름을 갈라놨다 - "광주(경기)"(↔ 광주광역시),
 * "고성(강원)" / "고성(경남)". 저장되는 값도 이 이름 그대로라 어디서도 헷갈리지 않는다.
 * 나중에 그 시/도의 드릴다운 지도를 그릴 때 {@code regions.json} 의 지역 이름도 반드시
 * 여기 places 와 똑같이 맞춰야 한다(트리 생성 시 경북처럼 구성 대조 검사를 한다).
 */
@Slf4j
@Component
public class RegionCatalog {

    private static final String RESOURCE = "static/assets/geo/region-tree.json";

    /** 표기 흔들림 → 표준 이름. 예: "경상북도" → "경북", "독도" → "울릉". */
    private final Map<String, String> aliases;
    /** 지역(말단) 이름 → 소속 권역 이름. 권역 이름 자신도 자기 자신으로 들어있다. */
    private final Map<String, String> areaByName;
    /** 지역 · 권역 이름 → 국가 코드("korea" / "japan"). */
    private final Map<String, String> countryByName;
    /** 화면에 그대로 쓸 수 있는 트리(순서 유지). */
    private final List<Country> countries;

    public RegionCatalog(ObjectMapper objectMapper) {
        Map<String, String> aliasMap = new HashMap<>();
        Map<String, String> areaMap = new HashMap<>();
        Map<String, String> countryMap = new HashMap<>();
        List<Country> tree = new ArrayList<>();

        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            JsonNode root = objectMapper.readTree(in);

            // JsonNode.fields() 는 최신 Jackson 에서 deprecated 라 convertValue 로 받는다.
            aliasMap.putAll(objectMapper.convertValue(
                    root.path("aliases"), new TypeReference<Map<String, String>>() { }));

            for (JsonNode c : root.path("countries")) {
                String code = c.path("code").asText();
                List<Area> areas = new ArrayList<>();
                for (JsonNode a : c.path("areas")) {
                    String areaName = a.path("name").asText();
                    List<String> places = new ArrayList<>();
                    for (JsonNode p : a.path("places")) {
                        places.add(p.asText());
                    }
                    // 권역 이름 자신도 색인해둔다 - 세분화 이전에 저장된 데이터(예: region="제주")가
                    // 그대로 들어와도 국가/권역을 찾을 수 있어야 하기 때문.
                    areaMap.put(areaName, areaName);
                    countryMap.put(areaName, code);
                    for (String place : places) {
                        areaMap.put(place, areaName);
                        countryMap.put(place, code);
                    }
                    areas.add(new Area(a.path("key").asText(), areaName, List.copyOf(places)));
                }
                tree.add(new Country(code, c.path("name").asText(), List.copyOf(areas)));
            }
        } catch (Exception e) {
            // 트리가 없으면 지역 선택 · 히트맵 롤업 · 칭호 판정이 전부 조용히 어긋난다.
            // 조용히 비어 있는 것보다 부팅에서 막히는 편이 훨씬 낫다.
            throw new IllegalStateException(RESOURCE + " 를 읽지 못했습니다.", e);
        }

        this.aliases = Map.copyOf(aliasMap);
        this.areaByName = Map.copyOf(areaMap);
        this.countryByName = Map.copyOf(countryMap);
        this.countries = List.copyOf(tree);
        log.info("[지역 카탈로그] {}개 국가 / {}개 이름 적재", countries.size(), areaByName.size());
    }

    /**
     * 표기를 표준 이름으로 맞춘다(공백 정리 + 별칭 치환). <b>상위로 접지는 않는다</b> -
     * "여수"는 "여수" 그대로 돌아온다. 비어 있으면 null.
     */
    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        String alias = aliases.get(trimmed);
        if (alias != null) {
            return alias;
        }
        if (areaByName.containsKey(trimmed)) {
            return trimmed;
        }
        // "여수시" · "울릉군"처럼 접미사가 붙은 자유 입력을 흡수한다(지역 선택기가 붙기 전에
        // 손으로 적어둔 데이터가 있다). 트리에 그 이름이 그대로 있는 경우 - 예를 들어
        // "제주시" - 는 바로 위에서 걸러지므로 잘못 자를 일이 없다.
        int last = trimmed.length() - 1;
        if (last > 0 && "시군구".indexOf(trimmed.charAt(last)) >= 0) {
            String stripped = trimmed.substring(0, last);
            if (areaByName.containsKey(stripped)) {
                return stripped;
            }
        }
        return trimmed;
    }

    /**
     * 이 이름이 속한 권역 이름. "여수" → "전남", "오사카" → "간사이".
     * 권역 이름을 넣으면 자기 자신이 돌아오고, 트리에 없는 이름이면 null.
     */
    public String areaOf(String raw) {
        String name = normalize(raw);
        return name == null ? null : areaByName.get(name);
    }

    /** 이 이름이 속한 국가 코드("korea" / "japan"). 트리에 없으면 null. */
    public String countryOf(String raw) {
        String name = normalize(raw);
        return name == null ? null : countryByName.get(name);
    }

    /** 트리가 아는 이름인가(권역 이름 포함). */
    public boolean isKnown(String raw) {
        return areaOf(raw) != null;
    }

    /**
     * 칭호 · 히트맵의 "국내 지역" 판정용 표준화.
     *
     * <p>한국은 판정 단위가 시/도라서 시/군을 상위로 접고("여수" → "전남"), 일본은 판정
     * 단위가 도도부현이라 접지 않는다("오사카"는 "오사카" - 권역인 "간사이"로 접으면
     * 광역시 특색 · 감귤 마니아 같은 단일 지역 칭호가 통째로 깨진다).
     */
    public String toTitleRegion(String raw) {
        String name = normalize(raw);
        if (name == null) {
            return null;
        }
        if ("korea".equals(countryByName.get(name))) {
            String area = areaByName.get(name);
            if (area != null) {
                return area;
            }
        }
        return name;
    }

    /** 화면(서버 렌더링)에서 쓸 전체 트리. 순서는 파일에 적힌 그대로다. */
    public List<Country> tree() {
        return countries;
    }

    /** 한 권역에 속한 지역 목록. 없는 권역이면 빈 목록. */
    public List<String> placesOf(String areaName) {
        String name = normalize(areaName);
        for (Country country : countries) {
            for (Area area : country.areas()) {
                if (area.name().equals(name)) {
                    return area.places();
                }
            }
        }
        return Collections.emptyList();
    }

    public record Country(String code, String name, List<Area> areas) { }

    public record Area(String key, String name, List<String> places) { }
}
