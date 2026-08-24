package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.ApplicationStatus;
import net.datasa.tanoshimi.domain.entity.PartyApplicationEntity;
import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartyApplicationRepository extends JpaRepository<PartyApplicationEntity, Long> {
    Optional<PartyApplicationEntity> findByPartyAndApplicant(PartyEntity party, UserEntity applicant);
    List<PartyApplicationEntity> findByPartyAndStatus(PartyEntity party, ApplicationStatus status);
    List<PartyApplicationEntity> findByApplicantOrderByAppliedAtDesc(UserEntity applicant);

    /** 승인/거절 처리 중 party, applicant 필드를 다루므로 미리 JOIN FETCH 해서 지연로딩 예외를 막는다. */
    @org.springframework.data.jpa.repository.Query(
            "select a from PartyApplicationEntity a join fetch a.party join fetch a.applicant where a.id = :id")
    Optional<PartyApplicationEntity> findWithPartyAndApplicantById(@org.springframework.data.repository.query.Param("id") Long id);
}
