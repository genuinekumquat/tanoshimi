package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.PhoneVerificationEntity;
import net.datasa.tanoshimi.domain.entity.VerificationPurpose;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhoneVerificationRepository extends JpaRepository<PhoneVerificationEntity, Long> {
    Optional<PhoneVerificationEntity> findTopByPhoneAndPurposeOrderByIdDesc(String phone, VerificationPurpose purpose);

    @Query("""
            select pv from PhoneVerificationEntity pv
            where pv.phone = :phone and pv.purpose = :purpose
              and pv.verifiedAt is not null and pv.usedAt is null
              and pv.verifiedAt >= :since
            order by pv.id desc limit 1
            """)
    Optional<PhoneVerificationEntity> findVerified(@Param("phone") String phone,
                                                   @Param("purpose") VerificationPurpose purpose,
                                                   @Param("since") LocalDateTime since);

    long countByPhoneAndCreatedAtAfter(String phone, LocalDateTime after);

    @Modifying
    @Query("delete from PhoneVerificationEntity pv where pv.createdAt < :threshold")
    int deleteOlderThan(@Param("threshold") LocalDateTime threshold);
}
