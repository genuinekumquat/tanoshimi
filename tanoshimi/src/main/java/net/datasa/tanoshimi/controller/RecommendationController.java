package net.datasa.tanoshimi.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.entity.Recommendation;
import net.datasa.tanoshimi.repository.RecommendationRepository;
import net.datasa.tanoshimi.service.FileStorageService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@Controller
@RequestMapping("/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationRepository recommendationRepository;
    private final FileStorageService fileStorageService;

    @GetMapping
    public String list(Model model) {
        List<Recommendation> list = recommendationRepository.findAllByOrderByCreatedAtDesc();
        model.addAttribute("recommendations", list);
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
                              @RequestParam(value="image", required=false) MultipartFile file,
                              @AuthenticationPrincipal UserDetails userDetails) {
                              
        String authorId = (userDetails != null) ? userDetails.getUsername() : "anonymous";
        String imageUrl = null;
        if (file != null && !file.isEmpty()) {
            imageUrl = fileStorageService.saveImage(file);
            fileStorageService.markActive(imageUrl);
        } else {
            // 인터넷을 통한 자동 기본값
            imageUrl = "https://loremflickr.com/400/400/travel," + title;
        }

        Recommendation rec = Recommendation.builder()
                .title(title)
                .region(region)
                .content(content)
                .imageUrl(imageUrl)
                .authorId(authorId)
                .build();
                
        recommendationRepository.save(rec);
        return "redirect:/recommendations";
    }
    
    @PostMapping("/{id}/like")
    @ResponseBody
    public String like(@PathVariable Long id) {
        Recommendation rec = recommendationRepository.findById(id).orElse(null);
        if(rec != null) {
            rec.incrementLike();
            recommendationRepository.save(rec);
            return String.valueOf(rec.getLikeCount());
        }
        return "0";
    }
}
