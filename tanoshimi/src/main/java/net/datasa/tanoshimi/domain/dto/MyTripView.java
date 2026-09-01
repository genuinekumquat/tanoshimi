package net.datasa.tanoshimi.domain.dto;

import java.time.LocalDate;
import net.datasa.tanoshimi.domain.entity.MyTripEntity;

/**
 * "내 여행" 목록/스냅 글쓰기 모달에서 쓰는 화면·API 공용 뷰.
 *
 * @param manageable SOLO 여행만 true - 화면에서 수정/삭제 버튼을 이 값으로 켜고 끈다
 *                   (PARTY 여행은 서버도 수정/삭제를 막는다 - MyTripService 참고).
 * @param counted    [v19-4 신규] 이 여행이 실제로 여행 횟수/지도/칭호에 반영되고 있는지
 *                   (MyTripService.isCountable). SOLO는 항상 true. PARTY인데 아직 연결된
 *                   스냅이 없으면 false - 화면에서 "스냅 인증 대기" 배지를 이 값으로 켠다.
 */
public record MyTripView(
        Long id, String source, String title, String destination,
        LocalDate startDate, LocalDate endDate, long days, String memo,
        boolean manageable, boolean counted
) {
    /** SOLO 여행 전용 호출부(MyTripController - 등록/수정 직후 응답)용, 항상 counted=true. */
    public static MyTripView of(MyTripEntity t) {
        return of(t, true);
    }

    public static MyTripView of(MyTripEntity t, boolean counted) {
        return new MyTripView(t.getId(), t.getSource().name(), t.getTitle(), t.getDestination(),
                t.getStartDate(), t.getEndDate(), t.days(), t.getMemo(), !t.isParty(), counted);
    }
}
