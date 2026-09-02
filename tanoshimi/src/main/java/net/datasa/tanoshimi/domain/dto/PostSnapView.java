package net.datasa.tanoshimi.domain.dto;

import java.time.LocalDate;
import net.datasa.tanoshimi.domain.entity.PostEntity;

/**
 * [v20 신규] 마이페이지 "내 여행" 관리 화면에서 "스냅 연결" 모달의 후보 목록으로 쓰는 뷰.
 *
 * <p>PostEntity.trip 은 LAZY 필드라, 템플릿(mypage/mytrip.html)에서 post.trip 을 직접 찍으면
 * open-in-view=false 환경에서 LazyInitializationException 이 난다(PostRepository의
 * findWithUserById 주석 참고 - 이 프로젝트는 이미 그 설정으로 굴러가고 있다). 그래서
 * 트랜잭션 안에 있는 PostService 에서 미리 값을 꺼내 이 DTO 로 넘긴다.
 *
 * @param tripId 이미 연결된 여행의 id(없으면 null) - 모달에서 "이미 연결됨" 표시에 쓴다.
 */
public record PostSnapView(
        Long id, String title, String thumbnailUrl, String region, LocalDate createdDate, Long tripId
) {
    public static PostSnapView of(PostEntity p) {
        return new PostSnapView(
                p.getId(), p.getTitle(), p.getThumbnailUrl(), p.getRegion(),
                p.getCreatedAt() != null ? p.getCreatedAt().toLocalDate() : null,
                p.getTrip() != null ? p.getTrip().getId() : null);
    }
}
