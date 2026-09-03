package net.datasa.tanoshimi.service;

import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.PostCommentEntity;
import net.datasa.tanoshimi.domain.entity.PostEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.repository.PostCommentRepository;
import net.datasa.tanoshimi.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * PostService - 컨트롤러에서 옮겨온 조회 로직 중 분기가 있는 것만 검증한다.
 * (getPost/getWithUser 같은 단순 passthrough 는 생략)
 */
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock private PostRepository postRepository;
    @Mock private PostCommentRepository postCommentRepository;
    @Mock private BlockService blockService;

    // 나머지 생성자 의존성은 이 테스트가 건드리지 않으므로 목만 채운다.
    @Mock private net.datasa.tanoshimi.repository.PostLikeRepository postLikeRepository;
    @Mock private net.datasa.tanoshimi.repository.PartyRepository partyRepository;
    @Mock private net.datasa.tanoshimi.repository.MyTripRepository myTripRepository;
    @Mock private NotificationService notificationService;
    @Mock private FileStorageService fileStorageService;
    @Mock private RegionCatalog regionCatalog;

    @InjectMocks
    private PostService postService;

    // early-return 경로(비로그인/차단없음)에서는 getUser()가 호출되지 않으므로 lenient 로 둔다.
    private PostCommentEntity commentBy(long authorId) {
        UserEntity author = mock(UserEntity.class);
        lenient().when(author.getId()).thenReturn(authorId);
        PostCommentEntity c = mock(PostCommentEntity.class);
        lenient().when(c.getUser()).thenReturn(author);
        return c;
    }

    private PostEntity postWithThumb(String thumbnailUrl) {
        PostEntity p = mock(PostEntity.class);
        when(p.getThumbnailUrl()).thenReturn(thumbnailUrl);
        return p;
    }

    // ---------------------------------------------------------------- visibleComments

    @Test
    void visibleComments_비로그인이면_차단조회_없이_전체_반환() {
        PostEntity post = mock(PostEntity.class);
        List<PostCommentEntity> all = List.of(commentBy(1L), commentBy(2L));
        when(postCommentRepository.findByPostOrderByCreatedAtAsc(post)).thenReturn(all);

        List<PostCommentEntity> result = postService.visibleComments(post, null);

        assertThat(result).isEqualTo(all);
        verifyNoInteractions(blockService);
    }

    @Test
    void visibleComments_차단_관계가_없으면_전체_반환() {
        PostEntity post = mock(PostEntity.class);
        UserEntity viewer = mock(UserEntity.class);
        List<PostCommentEntity> all = List.of(commentBy(1L), commentBy(2L));
        when(postCommentRepository.findByPostOrderByCreatedAtAsc(post)).thenReturn(all);
        when(blockService.relatedBlockedUserIds(viewer)).thenReturn(List.<Long>of());

        assertThat(postService.visibleComments(post, viewer)).isEqualTo(all);
    }

    @Test
    void visibleComments_차단한_작성자의_댓글은_걸러낸다() {
        PostEntity post = mock(PostEntity.class);
        UserEntity viewer = mock(UserEntity.class);
        PostCommentEntity ok1 = commentBy(1L);
        PostCommentEntity blocked = commentBy(99L);
        PostCommentEntity ok2 = commentBy(2L);
        when(postCommentRepository.findByPostOrderByCreatedAtAsc(post)).thenReturn(List.of(ok1, blocked, ok2));
        when(blockService.relatedBlockedUserIds(viewer)).thenReturn(List.of(99L));

        assertThat(postService.visibleComments(post, viewer)).containsExactly(ok1, ok2);
    }

    // ---------------------------------------------------------------- partyPhotos

    @Test
    void partyPhotos_썸네일이_없거나_빈_글은_제외한다() {
        PartyEntity party = mock(PartyEntity.class);
        PostEntity withThumb = postWithThumb("https://img/1.jpg");
        PostEntity noThumb = postWithThumb(null);
        PostEntity blankThumb = postWithThumb("   ");
        when(postRepository.findByPartyOrderByCreatedAtDesc(party))
                .thenReturn(List.of(withThumb, noThumb, blankThumb));

        assertThat(postService.partyPhotos(party)).containsExactly(withThumb);
    }
}
