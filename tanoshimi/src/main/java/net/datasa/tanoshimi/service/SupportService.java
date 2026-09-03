package net.datasa.tanoshimi.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.entity.SupportCommentEntity;
import net.datasa.tanoshimi.domain.entity.SupportEntity;
import net.datasa.tanoshimi.repository.SupportCommentRepository;
import net.datasa.tanoshimi.repository.SupportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 1:1 문의(고객센터) 게시판. 회원가입 없이 guestId/guestPassword 로 글을 쓰고,
 * 본인 확인도 그 쌍으로 한다(세션에 통과 여부만 저장 - 컨트롤러).
 */
@Service
@RequiredArgsConstructor
public class SupportService {

    private final SupportRepository supportRepository;
    private final SupportCommentRepository supportCommentRepository;

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
                .guestId(guestId).guestPassword(guestPassword)
                .title(title).content(content)
                .build());
    }

    /** 비회원 본인 확인 - 글의 guestId/guestPassword 와 정확히 일치해야 true. 글이 없으면 false. */
    @Transactional(readOnly = true)
    public boolean verifyGuest(Long id, String guestId, String guestPassword) {
        return supportRepository.findById(id)
                .map(post -> post.getGuestId().equals(guestId) && post.getGuestPassword().equals(guestPassword))
                .orElse(false);
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
