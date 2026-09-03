package net.datasa.tanoshimi.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.entity.BannerEntity;
import net.datasa.tanoshimi.repository.BannerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 메인 페이지 상단 배너. 조회는 홈 화면, 등록/삭제는 관리자 화면에서 쓴다. */
@Service
@RequiredArgsConstructor
public class BannerService {

    /** 동시에 노출 가능한 배너 최대 개수. */
    private static final int MAX_BANNERS = 5;

    private final BannerRepository bannerRepository;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public List<BannerEntity> list() {
        return bannerRepository.findAllByOrderBySortOrderAsc();
    }

    /**
     * 배너 이미지 업로드. 파일이 비어 있으면 아무것도 하지 않고 true.
     * 이미 {@value #MAX_BANNERS}개가 차 있으면 저장하지 않고 false 를 돌려준다(화면은 error=bannerLimit).
     */
    @Transactional
    public boolean upload(MultipartFile file, String targetUrl) {
        if (file.isEmpty()) return true;
        List<BannerEntity> current = bannerRepository.findAllByOrderBySortOrderAsc();
        if (current.size() >= MAX_BANNERS) return false;

        String url = fileStorageService.saveImage(file);
        BannerEntity banner = new BannerEntity();
        banner.setImageUrl(url);
        banner.setTargetUrl(targetUrl);
        banner.setSortOrder(current.size());
        bannerRepository.save(banner);
        fileStorageService.markActive(url);
        return true;
    }

    @Transactional
    public void delete(Long id) {
        bannerRepository.deleteById(id);
    }
}
