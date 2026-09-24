package net.datasa.tanoshimi.service;

import net.datasa.tanoshimi.domain.dto.RecommendationLikeResult;
import net.datasa.tanoshimi.domain.entity.Recommendation;
import net.datasa.tanoshimi.domain.entity.RecommendationLikeEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.RecommendationLikeRepository;
import net.datasa.tanoshimi.repository.RecommendationRepository;
import net.datasa.tanoshimi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock private RecommendationRepository recommendationRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private RecommendationLikeRepository recommendationLikeRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private RecommendationService recommendationService;

    @Test
    void write_이미지가_없으면_제목_기반_기본_이미지_URL_을_넣는다() {
        recommendationService.write("오사카", "간사이", "내용", null, "yuja");

        ArgumentCaptor<Recommendation> cap = ArgumentCaptor.forClass(Recommendation.class);
        verify(recommendationRepository).save(cap.capture());
        assertThat(cap.getValue().getImageUrl()).isEqualTo("https://loremflickr.com/400/400/travel,오사카");
        verify(fileStorageService, never()).saveImage(any());
    }

    @Test
    void write_이미지가_있으면_업로드한_URL_을_쓴다() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(fileStorageService.saveImage(file)).thenReturn("https://cdn/r.jpg");

        recommendationService.write("교토", "간사이", "내용", file, "yuja");

        ArgumentCaptor<Recommendation> cap = ArgumentCaptor.forClass(Recommendation.class);
        verify(recommendationRepository).save(cap.capture());
        assertThat(cap.getValue().getImageUrl()).isEqualTo("https://cdn/r.jpg");
        verify(fileStorageService).markActive("https://cdn/r.jpg");
    }

    @Test
    void toggleLike_처음_누르면_기록을_남기고_1_증가한다() {
        Recommendation rec = Recommendation.builder().title("t").content("c").authorId("a").build();
        UserEntity user = mock(UserEntity.class);
        when(recommendationRepository.findById(1L)).thenReturn(Optional.of(rec));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(recommendationLikeRepository.existsByRecommendationAndUser(rec, user)).thenReturn(false);

        RecommendationLikeResult result = recommendationService.toggleLike(1L, 7L);

        assertThat(result).isEqualTo(new RecommendationLikeResult(true, 1));
        verify(recommendationLikeRepository).save(any(RecommendationLikeEntity.class));
    }

    @Test
    void toggleLike_이미_눌렀으면_취소하고_1_감소한다() {
        Recommendation rec = Recommendation.builder().title("t").content("c").authorId("a").build();
        rec.incrementLike();
        UserEntity user = mock(UserEntity.class);
        when(recommendationRepository.findById(1L)).thenReturn(Optional.of(rec));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(recommendationLikeRepository.existsByRecommendationAndUser(rec, user)).thenReturn(true);

        RecommendationLikeResult result = recommendationService.toggleLike(1L, 7L);

        assertThat(result).isEqualTo(new RecommendationLikeResult(false, 0));
        verify(recommendationLikeRepository).deleteByRecommendationAndUser(rec, user);
        verify(recommendationLikeRepository, never()).save(any());
    }

    @Test
    void toggleLike_글이_없으면_RECOMMENDATION_NOT_FOUND() {
        when(recommendationRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recommendationService.toggleLike(2L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RECOMMENDATION_NOT_FOUND);
    }
}
