package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.MyTripEntity;
import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** [⑥ 마이페이지] "내 여행" - v19 신규. MyTripEntity 클래스 주석 참고. */
public interface MyTripRepository extends JpaRepository<MyTripEntity, Long> {
    List<MyTripEntity> findByUserOrderByStartDateDesc(UserEntity user);

    /** 파티 완료 자동 등록의 중복 방지용 - 이미 이 파티로 등록된 여행이 있는지. */
    boolean existsByUserAndParty(UserEntity user, PartyEntity party);

    /** 수정/삭제 전 소유권 확인용. */
    Optional<MyTripEntity> findByIdAndUser(Long id, UserEntity user);
}
