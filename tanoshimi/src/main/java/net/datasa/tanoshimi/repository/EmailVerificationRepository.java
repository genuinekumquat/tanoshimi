package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.EmailVerificationEntity;
import net.datasa.tanoshimi.domain.entity.VerificationPurpose;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationRepository extends JpaRepository<EmailVerificationEntity, Long> {
    Optional<EmailVerificationEntity> findTopByEmailAndPurposeOrderByIdDesc(String email, VerificationPurpose purpose);

    @Query("""
            select ev from EmailVerificationEntity ev
            where ev.email = :email and ev.purpose = :purpose
              and ev.verifiedAt is not null and ev.usedAt is null
              and ev.verifiedAt >= :since
            order by ev.id desc limit 1
            """)
    Optional<EmailVerificationEntity> findVerified(@Param("email") String email,
                                                   @Param("purpose") VerificationPurpose purpose,
                                                   @Param("since") LocalDateTime since);

    long countByEmailAndCreatedAtAfter(String email, LocalDateTime after);

    @Modifying
    @Query("delete from EmailVerificationEntity ev where ev.createdAt < :threshold")
    int deleteOlderThan(@Param("threshold") LocalDateTime threshold);
}
