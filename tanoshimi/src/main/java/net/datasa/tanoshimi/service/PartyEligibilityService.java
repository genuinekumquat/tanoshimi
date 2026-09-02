package net.datasa.tanoshimi.service;

import java.time.LocalDate;
import net.datasa.tanoshimi.domain.entity.*;
import org.springframework.stereotype.Service;

/**
 * 파티 참가 자격 검증.
 *
 * <p>"방장이 사람 보고 거르는 게 아니라 시스템이 조건 불일치 시 신청 버튼을 자동
 * 비활성화한다"는 팀 방침에 따라, 이 서비스는 신청 화면(프론트 1차 차단)과
 * 신청 API(서버 2차 검증) 양쪽에서 똑같이 호출된다. 결과를 프론트가 미리 알아야
 * 버튼을 비활성화할 수 있으므로 boolean 판정 메서드를 공개로 둔다.
 */
@Service
public class PartyEligibilityService {

    public EligibilityResult check(PartyEntity party, UserEntity user) {
        if (user.isAdmin()) return EligibilityResult.ok();

        // 모집 마감/출발일 경과 - "출발 당일부터는 신규 신청을 받지 않는다"는 팀 결정에 따라,
        // status==recruiting 이어도 출발일이 오늘이거나 이미 지났으면 신청을 막는다.
        // status 자체는 PartyCompletionScheduler가 여행 종료일(출발일+여행일수) 경과 후에나
        // completed 로 바꾸므로, 이 체크가 없으면 출발~여행 종료 사이 파티에도 신청이 들어간다.
        if (party.getStatus() != PartyStatus.recruiting || !party.getDepartureDate().isAfter(LocalDate.now())) {
            return EligibilityResult.rejected("party.apply.disabled.closed");
        }

        if (party.getGenderRestriction() == GenderRestriction.male_only && user.getGender() != Gender.male) {
            return EligibilityResult.rejected("party.apply.disabled.gender");
        }
        if (party.getGenderRestriction() == GenderRestriction.female_only && user.getGender() != Gender.female) {
            return EligibilityResult.rejected("party.apply.disabled.gender");
        }

        int age = user.age();
        if (party.getAgeMin() != null && age < party.getAgeMin()) {
            return EligibilityResult.rejected("party.apply.disabled.age");
        }
        if (party.getAgeMax() != null && age > party.getAgeMax()) {
            return EligibilityResult.rejected("party.apply.disabled.age");
        }

        if (party.getNationalityRestriction() == NationalityRestriction.kr_only && user.getNationality() != Nationality.KR) {
            return EligibilityResult.rejected("party.apply.disabled.nationality");
        }
        if (party.getNationalityRestriction() == NationalityRestriction.jp_only && user.getNationality() != Nationality.JP) {
            return EligibilityResult.rejected("party.apply.disabled.nationality");
        }

        return EligibilityResult.ok();
    }

    public boolean isEligible(PartyEntity party, UserEntity user) {
        return check(party, user).eligible();
    }

    /** messageKey 는 messages_ko/ja.properties 의 키 — 컨트롤러가 i18n MessageSource 로 변환해서 내려준다. */
    public record EligibilityResult(boolean eligible, String messageKey) {
        public static EligibilityResult ok() { return new EligibilityResult(true, null); }
        public static EligibilityResult rejected(String messageKey) { return new EligibilityResult(false, messageKey); }
    }
}
