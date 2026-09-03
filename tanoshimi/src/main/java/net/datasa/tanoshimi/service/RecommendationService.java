package net.datasa.tanoshimi.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.entity.Recommendation;
import net.datasa.tanoshimi.repository.RecommendationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 사용자 추천 여행지 게시판(가벼운 소셜 기능). */
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationRepository recommendationRepository;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public List<Recommendation> listNewestFirst() {
        return recommendationRepository.findAllByOrderByCreatedAtDesc();
    }

    /** 이미지가 없으면 제목 기반 무작위 여행 사진(loremflickr)을 기본값으로 넣는다. */
    @Transactional
    public void write(String title, String region, String content, MultipartFile image, String authorId) {
        String imageUrl;
        if (image != null && !image.isEmpty()) {
            imageUrl = fileStorageService.saveImage(image);
            fileStorageService.markActive(imageUrl);
        } else {
            imageUrl = "https://loremflickr.com/400/400/travel," + title;
        }
        recommendationRepository.save(Recommendation.builder()
                .title(title).region(region).content(content)
                .imageUrl(imageUrl).authorId(authorId)
                .build());
    }

    /** 좋아요 +1. 반환값은 갱신된 좋아요 수(글이 없으면 0). */
    @Transactional
    public int like(Long id) {
        return recommendationRepository.findById(id)
                .map(rec -> {
                    rec.incrementLike();
                    recommendationRepository.save(rec);
                    return rec.getLikeCount();
                })
                .orElse(0);
    }
}
