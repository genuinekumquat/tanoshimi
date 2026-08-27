package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.ActiveStatus;
import net.datasa.tanoshimi.domain.entity.ActivityEntity;
import net.datasa.tanoshimi.domain.entity.VenueType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<ActivityEntity, Long> {
    List<ActivityEntity> findByRegionAndStatus(String region, ActiveStatus status);
    List<ActivityEntity> findByStatus(ActiveStatus status);

    /** 날씨 기반 추천: 우천 시 실내(indoor)만, 맑으면 실외 위주로 우선순위 조정하는 데 사용. */
    List<ActivityEntity> findByRegionAndVenueTypeAndStatus(String region, VenueType venueType, ActiveStatus status);

    List<ActivityEntity> findByRegionAndStyleTagContainingAndStatus(String region, String styleTag, ActiveStatus status);

    /** [v16 신규] 장소검색 API 캐시 중복 저장 방지 - 같은 장소를 이미 저장해뒀으면 재사용. */
    java.util.Optional<ActivityEntity> findByExternalPlaceId(String externalPlaceId);
}
