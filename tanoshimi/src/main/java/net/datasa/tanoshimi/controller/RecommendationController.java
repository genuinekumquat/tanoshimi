package net.datasa.tanoshimi.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import net.datasa.tanoshimi.domain.dto.RecommendationLikeResult;
import net.datasa.tanoshimi.service.PlaceExploreService;
import net.datasa.tanoshimi.service.RecommendationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final PlaceExploreService placeExploreService;

    /**
     * 관광지 둘러보기 - 비로그인도 열람 가능. 예전엔 사용자 추천글 목록이었는데 여행 스냅과
     * 역할이 겹치고 글도 거의 없어서, 계획표에 넣을 수 있는 관광지를 지역별로 보여주고
     * 그 지역 파티로 이어지는 화면으로 바꿨다. (글쓰기/좋아요 API 는 기존 데이터 때문에 남겨둠)
     */
    @GetMapping
    public String list(@RequestParam(required = false) String region, Model model) {
        String selected = (region == null || region.isBlank()) ? null : region.trim();
        model.addAttribute("regions", placeExploreService.regions());
        model.addAttribute("selectedRegion", selected);
        model.addAttribute("regionViews", placeExploreService.byRegion(selected));
        model.addAttribute("totalPlaces", placeExploreService.totalPlaces());
        return "recommendations/list";
    }

    @GetMapping("/write")
    public String writeForm() {
        return "recommendations/write";
    }

    @PostMapping("/write")
    public String writeSubmit(@RequestParam("title") String title,
                              @RequestParam("region") String region,
                              @RequestParam("content") String content,
                              @RequestParam(value = "image", required = false) MultipartFile file,
                              @AuthenticationPrincipal UserDetails userDetails) {
        String authorId = (userDetails != null) ? userDetails.getUsername() : "anonymous";
        recommendationService.write(title, region, content, file, authorId);
        return "redirect:/recommendations";
    }

    @PostMapping("/{id}/like")
    @ResponseBody
    public ApiResponse<RecommendationLikeResult> like(@PathVariable Long id,
                                                      @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(recommendationService.toggleLike(id, principal.getId()));
    }
}
