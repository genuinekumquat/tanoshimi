package net.datasa.tanoshimi.service;

import net.datasa.tanoshimi.domain.entity.Recommendation;
import net.datasa.tanoshimi.repository.RecommendationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock private RecommendationRepository recommendationRepository;
    @Mock private FileStorageService fileStorageService;

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
    void like_글이_있으면_1_증가한_수를_돌려주고_없으면_0() {
        Recommendation rec = mock(Recommendation.class);
        when(rec.getLikeCount()).thenReturn(6);
        when(recommendationRepository.findById(1L)).thenReturn(Optional.of(rec));
        when(recommendationRepository.findById(2L)).thenReturn(Optional.empty());

        assertThat(recommendationService.like(1L)).isEqualTo(6);
        verify(rec).incrementLike();
        assertThat(recommendationService.like(2L)).isZero();
    }
}
