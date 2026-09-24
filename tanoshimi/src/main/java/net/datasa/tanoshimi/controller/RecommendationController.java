package net.datasa.tanoshimi.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import net.datasa.tanoshimi.domain.dto.RecommendationLikeResult;
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

    @GetMapping
    public String list(Model model, @AuthenticationPrincipal CustomUserDetails principal) {
        model.addAttribute("recommendations", recommendationService.listNewestFirst());
        model.addAttribute("likedIds", recommendationService.likedIds(principal.getId()));
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
