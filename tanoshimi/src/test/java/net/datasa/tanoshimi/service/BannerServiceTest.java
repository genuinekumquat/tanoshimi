package net.datasa.tanoshimi.service;

import net.datasa.tanoshimi.domain.entity.BannerEntity;
import net.datasa.tanoshimi.repository.BannerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BannerServiceTest {

    @Mock private BannerRepository bannerRepository;
    @Mock private FileStorageService fileStorageService;

    @InjectMocks
    private BannerService bannerService;

    @Test
    void upload_파일이_비어있으면_아무것도_안_하고_true() {
        MultipartFile empty = mock(MultipartFile.class);
        when(empty.isEmpty()).thenReturn(true);

        assertThat(bannerService.upload(empty, "/x")).isTrue();
        verify(bannerRepository, never()).save(any());
        verify(bannerRepository, never()).findAllByOrderBySortOrderAsc();
    }

    @Test
    void upload_이미_5개면_저장하지_않고_false() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        List<BannerEntity> five = IntStream.range(0, 5).mapToObj(i -> mock(BannerEntity.class)).toList();
        when(bannerRepository.findAllByOrderBySortOrderAsc()).thenReturn(five);

        assertThat(bannerService.upload(file, "/x")).isFalse();
        verify(bannerRepository, never()).save(any());
        verify(fileStorageService, never()).saveImage(any());
    }

    @Test
    void upload_정상이면_이미지_저장하고_sortOrder_는_현재_개수로_넣는다() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(bannerRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(mock(BannerEntity.class), mock(BannerEntity.class)));
        when(fileStorageService.saveImage(file)).thenReturn("https://img/b.jpg");

        assertThat(bannerService.upload(file, "/target")).isTrue();

        org.mockito.ArgumentCaptor<BannerEntity> cap = org.mockito.ArgumentCaptor.forClass(BannerEntity.class);
        verify(bannerRepository).save(cap.capture());
        assertThat(cap.getValue().getSortOrder()).isEqualTo(2);
        assertThat(cap.getValue().getImageUrl()).isEqualTo("https://img/b.jpg");
        verify(fileStorageService).markActive("https://img/b.jpg");
    }
}
