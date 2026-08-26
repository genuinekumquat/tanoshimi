package net.datasa.tanoshimi.service;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.entity.AiCreditUsageEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.repository.AiCreditUsageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [v16 신규] AI 챗봇 추천 / 동선 최적화가 소모하는 일일 크레딧을 관리한다.
 * 필드제약조건 확정 사항: "전 사용자 동일한 1일 총량 지급, 자정 초기화. 활동량 많은
 * 사용자는 총량은 동일하되 응답 품질(설명·개인화)만 차등" - 총량 차등 지급은 하지 않는다.
 * "자정 초기화"는 usage_date 컬럼이 (user_id, usage_date) 유니크라, 날짜가 바뀌면 그날의
 * 새 행이 자연히 0부터 시작하는 방식으로 구현한다(기존 행을 지우거나 리셋하지 않음).
 */
@Service
@RequiredArgsConstructor
public class AiCreditService {

    /** 담당자(이동인 ③)가 추후 운영값으로 조정할 수 있도록 설정값으로 뺀다. */
    @Value("${app.ai-credit.daily-limit:20}")
    private int dailyLimit;

    private final AiCreditUsageRepository aiCreditUsageRepository;

    /** 오늘 남은 크레딧이 있으면 1 소모하고 true, 없으면 false(호출자가 AI_CREDIT_EXCEEDED 를 던진다). */
    @Transactional
    public boolean tryConsume(UserEntity user) {
        AiCreditUsageEntity usage = todayUsage(user);
        if (!usage.hasRemaining()) {
            return false;
        }
        usage.consume();
        aiCreditUsageRepository.save(usage);
        return true;
    }

    @Transactional(readOnly = true)
    public int remaining(UserEntity user) {
        if (user == null) return 0;
        return aiCreditUsageRepository.findByUserAndUsageDate(user, LocalDate.now())
                .map(AiCreditUsageEntity::remaining)
                .orElse(dailyLimit);
    }

    private AiCreditUsageEntity todayUsage(UserEntity user) {
        return aiCreditUsageRepository.findByUserAndUsageDate(user, LocalDate.now())
                .orElseGet(() -> aiCreditUsageRepository.save(new AiCreditUsageEntity(user, LocalDate.now(), dailyLimit)));
    }
}
