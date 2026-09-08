package net.datasa.tanoshimi.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.service.BannerService;
import net.datasa.tanoshimi.service.PartyService;
import net.datasa.tanoshimi.service.PostService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final PartyService partyService;
    private final PostService postService;
    private final BannerService bannerService;

    /** 메인 페이지 "인기 스냅" 그리드에 노출할 사진 글 개수. */
    private static final int POPULAR_SNAP_LIMIT = 8;

    @GetMapping("/")
    public String home(Model model) {
        // "🔥 모집 마감 임박 파티" 그리드 재료 - 잔여석 적은 순으로 정렬된 모집중 파티 카드.
        model.addAttribute("urgentParties", partyService.urgentPartyCards());
        // "인기 스냅" 그리드 재료 - 커뮤니티 사진 글을 좋아요순으로.
        model.addAttribute("popularSnaps", postService.popularSnapCards(POPULAR_SNAP_LIMIT));
        model.addAttribute("banners", bannerService.list());
        return "index";
    }
}
