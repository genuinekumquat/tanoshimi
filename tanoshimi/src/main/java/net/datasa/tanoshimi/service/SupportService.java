package net.datasa.tanoshimi.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.entity.SupportCommentEntity;
import net.datasa.tanoshimi.domain.entity.SupportEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.SupportCommentRepository;
import net.datasa.tanoshimi.repository.SupportRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 1:1 문의(고객센터) 게시판. 회원가입 없이 guestId/guestPassword 로 글을 쓰고,
 * 본인 확인도 그 쌍으로 한다(세션에 통과 여부만 저장 - 컨트롤러).
 */
@Service
@RequiredArgsConstructor
public class SupportService {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(10);

    private final SupportRepository supportRepository;
    private final SupportCommentRepository supportCommentRepository;
    private final PasswordEncoder passwordEncoder;
    private final Map<Long, FailedAttempts> failedAttempts = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public List<SupportEntity> listNewestFirst() {
        return supportRepository.findAllByOrderByIdDesc();
    }

    /** 문의글 1건. 없으면 예외(잘못된 id 로 상세/댓글 진입 시). */
    @Transactional(readOnly = true)
    public SupportEntity get(Long id) {
        return supportRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid post ID"));
    }

    @Transactional
    public void write(String guestId, String guestPassword, String title, String content) {
        supportRepository.save(SupportEntity.builder()
                .guestId(guestId).guestPassword(passwordEncoder.encode(guestPassword))
                .title(title).content(content)
                .build());
    }

    /**
     * 비회원 본인 확인 - 글의 guestId 가 같고 비밀번호가 맞아야 true. 글이 없으면 false.
     *
     * <p>비밀번호는 BCrypt 해시로 저장한다. 이 변경 전에 쓴 글은 평문이라, 평문으로 맞으면 그 자리에서
     * 해시로 바꿔 둔다. 무차별 대입을 막으려고 글마다 연속 실패 횟수를 세서 MAX_FAILURES 번 틀리면
     * LOCK_DURATION 동안 확인 자체를 막는다(SUPPORT_AUTH_LOCKED).
     */
    @Transactional
    public boolean verifyGuest(Long id, String guestId, String guestPassword) {
        FailedAttempts attempts = failedAttempts.get(id);
        if (attempts != null && attempts.isLocked(Instant.now())) {
            throw new BusinessException(ErrorCode.SUPPORT_AUTH_LOCKED);
        }

        boolean ok = supportRepository.findById(id)
                .map(post -> post.getGuestId().equals(guestId) && passwordMatches(post, guestPassword))
                .orElse(false);
        if (ok) {
            failedAttempts.remove(id);
        } else {
            failedAttempts.computeIfAbsent(id, k -> new FailedAttempts()).recordFailure(Instant.now());
        }
        return ok;
    }

    private boolean passwordMatches(SupportEntity post, String rawPassword) {
        String stored = post.getGuestPassword();
        if (rawPassword == null || stored == null) {
            return false;
        }
        if (stored.startsWith("$2")) {
            return passwordEncoder.matches(rawPassword, stored);
        }
        // 해시 도입 전 평문 데이터 - 맞으면 해시로 교체
        boolean legacyMatch = MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.UTF_8), rawPassword.getBytes(StandardCharsets.UTF_8));
        if (legacyMatch) {
            post.replacePasswordHash(passwordEncoder.encode(rawPassword));
        }
        return legacyMatch;
    }

    /** 문의글별 연속 실패 기록(서버 메모리 - 재시작하면 초기화). */
    private static final class FailedAttempts {
        private int count;
        private Instant lockedUntil;

        synchronized boolean isLocked(Instant now) {
            return lockedUntil != null && now.isBefore(lockedUntil);
        }

        synchronized void recordFailure(Instant now) {
            if (lockedUntil != null && !now.isBefore(lockedUntil)) {
                count = 0;
                lockedUntil = null;
            }
            count++;
            if (count >= MAX_FAILURES) {
                lockedUntil = now.plus(LOCK_DURATION);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<SupportCommentEntity> topLevelComments(Long postId) {
        return supportCommentRepository.findBySupportIdAndParentCommentIsNullOrderByCreatedAtAsc(postId);
    }

    @Transactional
    public void addComment(Long postId, String content, Long parentId, String writerName) {
        SupportEntity post = get(postId);
        SupportCommentEntity parent = parentId == null ? null
                : supportCommentRepository.findById(parentId).orElse(null);
        supportCommentRepository.save(SupportCommentEntity.builder()
                .support(post)
                .content(content)
                .writerName(writerName)
                .parentComment(parent)
                .build());
    }
}
