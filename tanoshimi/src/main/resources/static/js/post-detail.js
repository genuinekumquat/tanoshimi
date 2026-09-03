(function () {
    // TNSM-52: 댓글/대댓글 로드 + 렌더링. party/room.html 사진 댓글 모달의
    // 트리 빌드 방식을 그대로 재사용한다(같은 /api/posts/{id}/comments 응답 형태를 씀).
    const commentList = document.getElementById('comment-list');
    let loadComments = null;
    if (commentList) {
        const postId = commentList.dataset.postId;
        const myId = commentList.dataset.myId ? Number(commentList.dataset.myId) : null;
        const commentCountEl = document.getElementById('comment-count');
        const commentInput = document.getElementById('comment-input');

        const escapeHtml = (s) => !s ? '' : String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));

        function renderComment(c, depth) {
            const div = document.createElement('div');
            div.className = 'comment';
            div.style.marginLeft = (depth * 24) + 'px';

            let avatarHtml = `<div class="ava">${escapeHtml((c.authorName || '?')[0])}</div>`;
            if (c.authorImage) {
                avatarHtml = `<img class="ava" src="${c.authorImage}" style="object-fit:cover;">`;
            }
            const canDelete = myId != null && myId === c.authorId;

            div.innerHTML = `
              ${depth > 0 ? '<div style="color:#ccc;font-size:15px;">↳</div>' : ''}
              ${avatarHtml}
              <div class="body">
                <span class="name">${escapeHtml(c.authorName)}</span><span class="txt">${escapeHtml(c.content)}</span>
                <div class="time">
                  ${c.createdAt}
                  <button type="button" class="reply-btn" data-id="${c.id}" style="margin-left:8px; background:none; border:none; color:var(--forest); font-weight:700; cursor:pointer;">답글</button>
                  ${canDelete ? `<button type="button" class="del-comment-btn" data-id="${c.id}" style="margin-left:6px; background:none; border:none; color:var(--danger); cursor:pointer;">삭제</button>` : ''}
                </div>
              </div>`;
            commentList.appendChild(div);
            (c.children || []).forEach(child => renderComment(child, depth + 1));
        }

        loadComments = async function () {
            commentList.innerHTML = '<div style="color:var(--ink-soft); font-size:12.8px;">댓글을 불러오는 중...</div>';
            const res = await window.api.get(`/api/posts/${postId}/comments`);
            if (!res.success) {
                commentList.innerHTML = '<div style="color:var(--danger); font-size:12.8px;">댓글을 불러오는데 실패했습니다.</div>';
                return;
            }
            const comments = res.data;
            if (commentCountEl) commentCountEl.textContent = '(' + comments.length + ')';
            commentList.innerHTML = '';
            if (comments.length === 0) {
                commentList.innerHTML = '<div style="color:var(--ink-soft); font-size:12.8px;">첫 댓글을 남겨보세요!</div>';
                return;
            }

            const map = {};
            const roots = [];
            comments.forEach(c => { c.children = []; map[c.id] = c; });
            comments.forEach(c => {
                if (c.parentId && map[c.parentId]) map[c.parentId].children.push(c);
                else roots.push(c);
            });
            roots.forEach(r => renderComment(r, 0));

            commentList.querySelectorAll('.reply-btn').forEach(btn => {
                btn.addEventListener('click', () => {
                    if (!commentInput) return;
                    commentInput.dataset.parent = btn.dataset.id;
                    commentInput.placeholder = '답글을 작성 중... (완료 시 등록 버튼 클릭)';
                    commentInput.focus();
                });
            });
            commentList.querySelectorAll('.del-comment-btn').forEach(btn => {
                btn.addEventListener('click', async () => {
                    if (!confirm('정말 삭제하시겠습니까?')) return;
                    const res = await window.api.del(`/api/posts/comments/${btn.dataset.id}`);
                    if (res.success) loadComments(); else alert(res.message || '삭제에 실패했습니다.');
                });
            });
        };

        loadComments();
    }

    const likeBtn = document.getElementById('btn-like');
    if (likeBtn) {
        likeBtn.addEventListener('click', async () => {
            likeBtn.disabled = true;
            const result = await window.api.post(`/api/posts/${likeBtn.dataset.postId}/like`, {});
            likeBtn.disabled = false;
            if (result.success) {
                location.reload();
            } else {
                // 실패해도 아무 반응이 없으면 사용자 입장에선 "눌러도 무응답"으로 보이니 반드시 알려준다.
                alert(result.message || '좋아요 처리에 실패했습니다.');
            }
        });
    }

    const commentBtn = document.getElementById('btn-comment-submit');
    if (commentBtn) {
        commentBtn.addEventListener('click', async () => {
            const input = document.getElementById('comment-input');
            const content = input.value.trim();
            if (!content) return;
            const parentId = input.dataset.parent ? Number(input.dataset.parent) : null;
            const payload = { content };
            if (parentId) payload.parentId = parentId;
            try {
                const response = await fetch(`/api/posts/${commentBtn.dataset.postId}/comments`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json',
                        [document.querySelector('meta[name="_csrf_header"]').content]: document.querySelector('meta[name="_csrf"]').content },
                    body: JSON.stringify(payload)
                });
                const result = await response.json();
                if (result.success) {
                    input.value = '';
                    delete input.dataset.parent;
                    input.placeholder = '댓글을 남겨보세요';
                    if (loadComments) loadComments();
                } else {
                    alert(result.message || '댓글 등록에 실패했습니다.');
                }
            } catch (e) {
                console.error('댓글 등록 오류:', e);
                alert('댓글 등록 중 오류가 발생했습니다.');
            }
        });
    }

    const deleteBtn = document.getElementById('btn-delete-post');
    if (deleteBtn) {
        deleteBtn.addEventListener('click', async () => {
            if (!confirm('이 글을 삭제할까요? 되돌릴 수 없습니다.')) return;
            const result = await window.api.del(`/api/posts/${deleteBtn.dataset.postId}`);
            if (result.success) window.location.href = '/board';
            else alert(result.message || '삭제에 실패했습니다.');
        });
    }

    // 공유하기 - 이 글의 URL을 클립보드에 복사한다.
    const shareBtn = document.getElementById('btn-share');
    if (shareBtn) {
        shareBtn.addEventListener('click', async () => {
            const url = window.location.href;
            try {
                if (navigator.clipboard && window.isSecureContext) {
                    await navigator.clipboard.writeText(url);
                } else {
                    // http(비보안 컨텍스트, 예: http://localhost) 에서는 clipboard API 가 막혀 있을 수 있어서
                    // 옛날 방식(임시 textarea + execCommand)으로 폴백한다.
                    const textarea = document.createElement('textarea');
                    textarea.value = url;
                    textarea.style.position = 'fixed';
                    textarea.style.opacity = '0';
                    document.body.appendChild(textarea);
                    textarea.select();
                    document.execCommand('copy');
                    document.body.removeChild(textarea);
                }
                const original = shareBtn.textContent;
                shareBtn.textContent = '✅ 링크 복사됨';
                setTimeout(() => { shareBtn.textContent = original; }, 1500);
            } catch (e) {
                console.error('클립보드 복사 실패:', e);
                alert('링크 복사에 실패했습니다. 주소창의 URL을 직접 복사해 주세요.');
            }
        });
    }
})();
