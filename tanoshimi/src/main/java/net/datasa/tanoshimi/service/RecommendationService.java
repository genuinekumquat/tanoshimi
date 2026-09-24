package net.datasa.tanoshimi.service;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.dto.RecommendationLikeResult;
import net.datasa.tanoshimi.domain.entity.Recommendation;
import net.datasa.tanoshimi.domain.entity.RecommendationLikeEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.RecommendationLikeRepository;
import net.datasa.tanoshimi.repository.RecommendationRepository;
import net.datasa.tanoshimi.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 사용자 추천 여행지 게시판(가벼운 소셜 기능). */
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationRepository recommendationRepository;
    private final FileStorageService fileStorageService;
    private final RecommendationLikeRepository recommendationLikeRepository;
    private final UserRepository userRepository;

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

    /** 로그인 사용자가 좋아요를 누른 추천글 id 목록 - 목록 화면에서 하트를 채워 보여줄 때 쓴다. */
    @Transactional(readOnly = true)
    public Set<Long> likedIds(Long userId) {
        return recommendationLikeRepository.findRecommendationIdsByUserId(userId);
    }

    /**
     * 좋아요 토글 - 안 눌렀으면 +1, 이미 눌렀으면 취소(-1). 게시글 좋아요(PostService.toggleLike)와
     * 같은 방식으로 한 사람당 한 번만 반영되게 recommendation_likes 에 누른 기록을 남긴다.
     */
    @Transactional
    public RecommendationLikeResult toggleLike(Long id, Long userId) {
        Recommendation rec = recommendationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECOMMENDATION_NOT_FOUND));
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        boolean liked;
        if (recommendationLikeRepository.existsByRecommendationAndUser(rec, user)) {
            recommendationLikeRepository.deleteByRecommendationAndUser(rec, user);
            rec.decrementLike();
            liked = false;
        } else {
            recommendationLikeRepository.save(new RecommendationLikeEntity(rec, user));
            rec.incrementLike();
            liked = true;
        }
        recommendationRepository.save(rec);
        return new RecommendationLikeResult(liked, rec.getLikeCount());
    }
}
