package net.datasa.tanoshimi.controller;

import lombok.RequiredArgsConstructor;
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
    public String list(Model model) {
        model.addAttribute("recommendations", recommendationService.listNewestFirst());
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
    public String like(@PathVariable Long id) {
        return String.valueOf(recommendationService.like(id));
    }
}
