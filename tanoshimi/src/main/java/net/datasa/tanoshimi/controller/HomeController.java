package net.datasa.tanoshimi.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.service.BannerService;
import net.datasa.tanoshimi.service.PostService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    /** [TNSM-53] SNAP 피드에 보여줄 최근 사진 후기 개수. */
    private static final int SNAP_FEED_SIZE = 20;

    private final PostService postService;
    private final BannerService bannerService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("recentSnaps", postService.recentSnaps(SNAP_FEED_SIZE));
        model.addAttribute("banners", bannerService.list());
        return "index";
    }
}
