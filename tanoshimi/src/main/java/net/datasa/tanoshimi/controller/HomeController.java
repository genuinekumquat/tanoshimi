package net.datasa.tanoshimi.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.service.BannerService;
import net.datasa.tanoshimi.service.PartyService;
import net.datasa.tanoshimi.service.PostService;
import net.datasa.tanoshimi.service.RegionCatalog;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final PartyService partyService;
    private final PostService postService;
    private final BannerService bannerService;
    private final RegionCatalog regionCatalog;

    /** 메인 페이지 "인기 스냅" 그리드에 노출할 사진 글 개수. */
    private static final int POPULAR_SNAP_LIMIT = 8;

    @GetMapping("/")
    public String home(@RequestParam(required = false) String region, Model model) {
        // "🔥 모집 마감 임박 파티" 그리드 재료 - 잔여석 적은 순으로 정렬된 모집중 파티 카드.
        model.addAttribute("urgentParties", partyService.urgentPartyCards());
        // [TNSM-53 재구현] "인기 스냅" 그리드 재료 - region이 선택되면 그 지역(+하위 지역)만.
        model.addAttribute("popularSnaps", postService.popularSnapCards(POPULAR_SNAP_LIMIT, region));
        // 지역별 인기 스냅 드롭다운 재료(한국 17개 시/도 + 일본 9개 지방) + 현재 선택값.
        model.addAttribute("regionTree", regionCatalog.tree());
        model.addAttribute("selectedRegion", region);
        model.addAttribute("banners", bannerService.list());
        return "index";
    }
}
